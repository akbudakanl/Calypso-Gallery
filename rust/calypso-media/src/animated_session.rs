use std::io::Cursor;
use std::sync::Arc;

use image_webp::WebPDecoder;
use png::{BlendOp, ColorType, Decoder as PngDecoder, DisposeOp, FrameControl, Limits as PngLimits, Reader as PngReader, Transformations};

use crate::{
    compute_target_dimensions,
    gif_session::GifOpenInfo,
    pixel_ops, premultiply_rgba_in_place, try_alloc_zeroed, validate_source_dimensions, MediaError,
    MAX_DECODE_ALLOC_BYTES, MAX_GIF_DIMENSION, MAX_GIF_FRAMES, MAX_GIF_PIXELS,
};

const DEFAULT_FRAME_DELAY_MS: u32 = 100;
const MIN_FRAME_DELAY_MS: u32 = 20;
const MAX_FRAME_DELAY_MS: u32 = 10_000;
const PNG_MAGIC: &[u8; 8] = b"\x89PNG\r\n\x1a\n";

pub(crate) fn is_png_bytes(data: &[u8]) -> bool {
    data.len() >= PNG_MAGIC.len() && &data[..8] == PNG_MAGIC
}

pub(crate) fn is_webp_bytes(data: &[u8]) -> bool {
    data.len() >= 12 && &data[..4] == b"RIFF" && &data[8..12] == b"WEBP"
}

fn clamp_delay_ms(raw_ms: u32) -> u32 {
    if raw_ms == 0 {
        DEFAULT_FRAME_DELAY_MS
    } else {
        raw_ms.clamp(MIN_FRAME_DELAY_MS, MAX_FRAME_DELAY_MS)
    }
}

fn apng_delay_ms(fc: &FrameControl) -> u32 {
    let den = if fc.delay_den == 0 { 100u64 } else { u64::from(fc.delay_den) };
    let ms = (u64::from(fc.delay_num) * 1000) / den;
    clamp_delay_ms(ms.min(u64::from(u32::MAX)) as u32)
}

pub(crate) struct ApngSession {
    raw_bytes: Arc<[u8]>,
    reader: Option<PngReader<Cursor<Arc<[u8]>>>>,
    screen_w: u32,
    screen_h: u32,
    out_w: u32,
    out_h: u32,
    frame_count: u32,
    canvas: Vec<u8>,
    prev_canvas: Option<Vec<u8>>,
    raw_sub: Vec<u8>,
    sub_rgba: Vec<u8>,
    premul_buf: Vec<u8>,
    out_buf: Vec<u8>,
    last_dispose: DisposeOp,
    last_rect: (u32, u32, u32, u32),
    current_frame: Option<usize>,
}

impl ApngSession {
    pub(crate) fn out_len(&self) -> usize {
        self.out_buf.len()
    }

    pub(crate) fn open(raw_bytes: Arc<[u8]>, req_w: u32, req_h: u32) -> Result<(Self, GifOpenInfo), MediaError> {
        let mut decoder = PngDecoder::new_with_limits(
            Cursor::new(Arc::clone(&raw_bytes)),
            PngLimits { bytes: MAX_DECODE_ALLOC_BYTES },
        );
        decoder.set_transformations(Transformations::EXPAND | Transformations::STRIP_16);
        let reader = decoder.read_info().map_err(|_| MediaError::DecodeFailed)?;
        let info = reader.info();
        let (screen_w, screen_h) = (info.width, info.height);
        validate_source_dimensions(screen_w, screen_h)?;

        let anim = info.animation_control().ok_or(MediaError::UnsupportedFormat)?;
        if anim.num_frames < 2 {
            return Err(MediaError::UnsupportedFormat);
        }
        let frame_count = anim.num_frames.min(MAX_GIF_FRAMES as u32);
        let (out_w, out_h) = compute_target_dimensions(
            screen_w,
            screen_h,
            req_w,
            req_h,
            MAX_GIF_DIMENSION,
            MAX_GIF_PIXELS,
        )?;

        let canvas_len = (screen_w as usize)
            .checked_mul(screen_h as usize)
            .and_then(|n| n.checked_mul(4))
            .ok_or(MediaError::TooLarge)?;
        let out_len = (out_w as usize)
            .checked_mul(out_h as usize)
            .and_then(|n| n.checked_mul(4))
            .ok_or(MediaError::TooLarge)?;

        let canvas = try_alloc_zeroed(canvas_len)?;
        let raw_sub = try_alloc_zeroed(canvas_len)?;
        let sub_rgba = try_alloc_zeroed(canvas_len)?;
        let premul_buf = try_alloc_zeroed(canvas_len)?;
        let out_buf = try_alloc_zeroed(out_len)?;

        let session = Self {
            raw_bytes,
            reader: Some(reader),
            screen_w,
            screen_h,
            out_w,
            out_h,
            frame_count,
            canvas,
            prev_canvas: None,
            raw_sub,
            sub_rgba,
            premul_buf,
            out_buf,
            last_dispose: DisposeOp::None,
            last_rect: (0, 0, 0, 0),
            current_frame: None,
        };
        let open_info = GifOpenInfo {
            handle: 0,
            width: out_w,
            height: out_h,
            frame_count,
        };
        Ok((session, open_info))
    }

    pub(crate) fn render_into(&mut self, frame_index: usize, dst: Option<&mut Vec<u8>>) -> Result<u32, MediaError> {
        let count = self.frame_count as usize;
        if count == 0 {
            return Err(MediaError::InvalidInput);
        }
        let target = frame_index % count;
        let need_reset = match self.current_frame {
            Some(cur) if target > cur && self.reader.is_some() => false,
            _ => true,
        };
        if need_reset {
            let mut decoder = PngDecoder::new_with_limits(
                Cursor::new(Arc::clone(&self.raw_bytes)),
                PngLimits { bytes: MAX_DECODE_ALLOC_BYTES },
            );
            decoder.set_transformations(Transformations::EXPAND | Transformations::STRIP_16);
            let mut reader = decoder.read_info().map_err(|_| MediaError::DecodeFailed)?;
            if reader.info().frame_control().is_none() {
                reader.next_frame_info().map_err(|_| MediaError::DecodeFailed)?;
            }
            self.reader = Some(reader);
            self.canvas.fill(0);
            self.prev_canvas = None;
            self.last_dispose = DisposeOp::None;
            self.last_rect = (0, 0, 0, 0);
            self.current_frame = None;
        }

        let start = self.current_frame.map_or(0, |c| c + 1);
        let mut last_delay = DEFAULT_FRAME_DELAY_MS;
        for idx in start..=target {
            let reader = self.reader.as_mut().ok_or(MediaError::DecodeFailed)?;
            let out_info = reader.next_frame(&mut self.raw_sub).map_err(|_| MediaError::DecodeFailed)?;
            let fc = reader
                .info()
                .frame_control()
                .cloned()
                .ok_or(MediaError::DecodeFailed)?;

            apply_apng_disposal(
                &mut self.canvas,
                self.prev_canvas.as_deref(),
                self.screen_w,
                self.screen_h,
                self.last_dispose,
                self.last_rect,
            );
            if fc.dispose_op == DisposeOp::Previous {
                let mut snap = try_alloc_zeroed(self.canvas.len())?;
                snap.copy_from_slice(&self.canvas);
                self.prev_canvas = Some(snap);
            }

            let sub_pixels = (out_info.width as usize)
                .checked_mul(out_info.height as usize)
                .ok_or(MediaError::TooLarge)?;
            let sub_rgba_len = sub_pixels.checked_mul(4).ok_or(MediaError::TooLarge)?;
            let raw_slice = self.raw_sub.get(..out_info.buffer_size()).ok_or(MediaError::DecodeFailed)?;
            expand_subframe_to_rgba(raw_slice, out_info.color_type, sub_pixels, &mut self.sub_rgba[..sub_rgba_len])?;

            composite_apng_subframe(
                &mut self.canvas,
                self.screen_w,
                self.screen_h,
                &self.sub_rgba[..sub_rgba_len],
                &fc,
            );
            self.last_dispose = fc.dispose_op;
            self.last_rect = (fc.x_offset, fc.y_offset, fc.width, fc.height);
            self.current_frame = Some(idx);
            last_delay = apng_delay_ms(&fc);
        }

        self.premul_buf.copy_from_slice(&self.canvas);
        premultiply_rgba_in_place(&mut self.premul_buf);
        if self.out_w != self.screen_w || self.out_h != self.screen_h {
            pixel_ops::downscale_rgba_into(
                &self.premul_buf,
                self.screen_w,
                self.screen_h,
                self.out_w,
                self.out_h,
                &mut self.out_buf,
            )?;
        } else {
            self.out_buf.copy_from_slice(&self.premul_buf);
        }
        if let Some(out_vec) = dst {
            *out_vec = try_alloc_zeroed(self.out_buf.len())?;
            out_vec.copy_from_slice(&self.out_buf);
        }
        Ok(last_delay)
    }

    pub(crate) fn copy_out_into(&self, dst: &mut [u8]) -> Result<(), MediaError> {
        if dst.len() < self.out_buf.len() {
            return Err(MediaError::InvalidInput);
        }
        dst[..self.out_buf.len()].copy_from_slice(&self.out_buf);
        Ok(())
    }
}

pub(crate) struct WebpAnimSession {
    decoder: WebPDecoder<Cursor<Arc<[u8]>>>,
    screen_w: u32,
    screen_h: u32,
    out_w: u32,
    out_h: u32,
    frame_count: u32,
    has_alpha: bool,
    raw_frame: Vec<u8>,
    canvas_rgba: Vec<u8>,
    out_buf: Vec<u8>,
    current_frame: Option<usize>,
}

impl WebpAnimSession {
    pub(crate) fn out_len(&self) -> usize {
        self.out_buf.len()
    }

    pub(crate) fn open(raw_bytes: Arc<[u8]>, req_w: u32, req_h: u32) -> Result<(Self, GifOpenInfo), MediaError> {
        let mut decoder = WebPDecoder::new(Cursor::new(raw_bytes)).map_err(|_| MediaError::DecodeFailed)?;
        decoder.set_memory_limit(MAX_DECODE_ALLOC_BYTES);
        if !decoder.is_animated() {
            return Err(MediaError::UnsupportedFormat);
        }
        let (screen_w, screen_h) = decoder.dimensions();
        validate_source_dimensions(screen_w, screen_h)?;
        let num_frames = decoder.num_frames();
        if num_frames == 0 {
            return Err(MediaError::DecodeFailed);
        }
        let frame_count = num_frames.min(MAX_GIF_FRAMES as u32);
        let (out_w, out_h) = compute_target_dimensions(
            screen_w,
            screen_h,
            req_w,
            req_h,
            MAX_GIF_DIMENSION,
            MAX_GIF_PIXELS,
        )?;
        let raw_len = decoder.output_buffer_size().ok_or(MediaError::TooLarge)?;
        let canvas_len = (screen_w as usize)
            .checked_mul(screen_h as usize)
            .and_then(|n| n.checked_mul(4))
            .ok_or(MediaError::TooLarge)?;
        let out_len = (out_w as usize)
            .checked_mul(out_h as usize)
            .and_then(|n| n.checked_mul(4))
            .ok_or(MediaError::TooLarge)?;

        let has_alpha = decoder.has_alpha();
        let raw_frame = try_alloc_zeroed(raw_len)?;
        let canvas_rgba = try_alloc_zeroed(canvas_len)?;
        let out_buf = try_alloc_zeroed(out_len)?;

        let session = Self {
            decoder,
            screen_w,
            screen_h,
            out_w,
            out_h,
            frame_count,
            has_alpha,
            raw_frame,
            canvas_rgba,
            out_buf,
            current_frame: None,
        };
        let open_info = GifOpenInfo {
            handle: 0,
            width: out_w,
            height: out_h,
            frame_count,
        };
        Ok((session, open_info))
    }

    pub(crate) fn render_into(&mut self, frame_index: usize, dst: Option<&mut Vec<u8>>) -> Result<u32, MediaError> {
        let count = self.frame_count as usize;
        if count == 0 {
            return Err(MediaError::InvalidInput);
        }
        let target = frame_index % count;
        let need_reset = match self.current_frame {
            Some(cur) if target > cur => false,
            _ => true,
        };
        if need_reset {
            self.decoder.reset_animation();
            self.current_frame = None;
        }

        let start = self.current_frame.map_or(0, |c| c + 1);
        let mut last_delay = DEFAULT_FRAME_DELAY_MS;
        for idx in start..=target {
            let duration_ms = self
                .decoder
                .read_frame(&mut self.raw_frame)
                .map_err(|_| MediaError::DecodeFailed)?;
            self.current_frame = Some(idx);
            last_delay = clamp_delay_ms(duration_ms);
        }

        let pixel_count = (self.screen_w as usize)
            .checked_mul(self.screen_h as usize)
            .ok_or(MediaError::TooLarge)?;
        if self.has_alpha {
            let expected = pixel_count.checked_mul(4).ok_or(MediaError::TooLarge)?;
            if self.raw_frame.len() < expected {
                return Err(MediaError::DecodeFailed);
            }
            self.canvas_rgba.copy_from_slice(&self.raw_frame[..expected]);
            premultiply_rgba_in_place(&mut self.canvas_rgba);
        } else {
            expand_rgb_slice(&self.raw_frame, pixel_count, &mut self.canvas_rgba)?;
        }

        if self.out_w != self.screen_w || self.out_h != self.screen_h {
            pixel_ops::downscale_rgba_into(
                &self.canvas_rgba,
                self.screen_w,
                self.screen_h,
                self.out_w,
                self.out_h,
                &mut self.out_buf,
            )?;
        } else {
            self.out_buf.copy_from_slice(&self.canvas_rgba);
        }
        if let Some(out_vec) = dst {
            *out_vec = try_alloc_zeroed(self.out_buf.len())?;
            out_vec.copy_from_slice(&self.out_buf);
        }
        Ok(last_delay)
    }

    pub(crate) fn copy_out_into(&self, dst: &mut [u8]) -> Result<(), MediaError> {
        if dst.len() < self.out_buf.len() {
            return Err(MediaError::InvalidInput);
        }
        dst[..self.out_buf.len()].copy_from_slice(&self.out_buf);
        Ok(())
    }
}

fn expand_rgb_slice(src: &[u8], pixel_count: usize, dst: &mut [u8]) -> Result<(), MediaError> {
    let expected_src = pixel_count.checked_mul(3).ok_or(MediaError::TooLarge)?;
    let expected_dst = pixel_count.checked_mul(4).ok_or(MediaError::TooLarge)?;
    if src.len() < expected_src || dst.len() < expected_dst {
        return Err(MediaError::DecodeFailed);
    }
    for (out, px) in dst[..expected_dst]
        .chunks_exact_mut(4)
        .zip(src[..expected_src].chunks_exact(3))
    {
        out[0] = px[0];
        out[1] = px[1];
        out[2] = px[2];
        out[3] = 255;
    }
    Ok(())
}

fn expand_subframe_to_rgba(
    src: &[u8],
    color_type: ColorType,
    pixel_count: usize,
    dst: &mut [u8],
) -> Result<(), MediaError> {
    let expected_dst = pixel_count.checked_mul(4).ok_or(MediaError::TooLarge)?;
    if dst.len() < expected_dst {
        return Err(MediaError::DecodeFailed);
    }
    match color_type {
        ColorType::Rgba => {
            if src.len() < expected_dst {
                return Err(MediaError::DecodeFailed);
            }
            dst[..expected_dst].copy_from_slice(&src[..expected_dst]);
        }
        ColorType::Rgb => expand_rgb_slice(src, pixel_count, dst)?,
        ColorType::GrayscaleAlpha => {
            let expected_src = pixel_count.checked_mul(2).ok_or(MediaError::TooLarge)?;
            if src.len() < expected_src {
                return Err(MediaError::DecodeFailed);
            }
            for (out, px) in dst[..expected_dst]
                .chunks_exact_mut(4)
                .zip(src[..expected_src].chunks_exact(2))
            {
                out[0] = px[0];
                out[1] = px[0];
                out[2] = px[0];
                out[3] = px[1];
            }
        }
        ColorType::Grayscale => {
            if src.len() < pixel_count {
                return Err(MediaError::DecodeFailed);
            }
            for (out, &g) in dst[..expected_dst].chunks_exact_mut(4).zip(src[..pixel_count].iter()) {
                out[0] = g;
                out[1] = g;
                out[2] = g;
                out[3] = 255;
            }
        }
        ColorType::Indexed => return Err(MediaError::DecodeFailed),
    }
    Ok(())
}

fn apply_apng_disposal(
    canvas: &mut [u8],
    prev_canvas: Option<&[u8]>,
    screen_w: u32,
    screen_h: u32,
    dispose_op: DisposeOp,
    rect: (u32, u32, u32, u32),
) {
    match dispose_op {
        DisposeOp::None => {}
        DisposeOp::Background => {
            let (rx, ry, rw, rh) = rect;
            let sw = screen_w as usize;
            let sh = screen_h as usize;
            let x_start = (rx as usize).min(sw);
            let y_start = (ry as usize).min(sh);
            let x_end = x_start.saturating_add(rw as usize).min(sw);
            let y_end = y_start.saturating_add(rh as usize).min(sh);
            for y in y_start..y_end {
                let row_start = (y * sw + x_start) * 4;
                let row_end = (y * sw + x_end) * 4;
                if row_end <= canvas.len() {
                    canvas[row_start..row_end].fill(0);
                }
            }
        }
        DisposeOp::Previous => {
            if let Some(prev) = prev_canvas {
                if prev.len() == canvas.len() {
                    canvas.copy_from_slice(prev);
                }
            }
        }
    }
}

fn composite_apng_subframe(
    canvas: &mut [u8],
    screen_w: u32,
    screen_h: u32,
    sub_rgba: &[u8],
    fc: &FrameControl,
) {
    let fw = fc.width as usize;
    let fh = fc.height as usize;
    let left = fc.x_offset as usize;
    let top = fc.y_offset as usize;
    let sw = screen_w as usize;
    let sh = screen_h as usize;
    let blend_over = fc.blend_op == BlendOp::Over;

    for fy in 0..fh {
        let cy = top.saturating_add(fy);
        if cy >= sh {
            break;
        }
        for fx in 0..fw {
            let cx = left.saturating_add(fx);
            if cx >= sw {
                break;
            }
            let si = (fy * fw + fx) * 4;
            let di = (cy * sw + cx) * 4;
            if si + 4 > sub_rgba.len() || di + 4 > canvas.len() {
                continue;
            }
            if !blend_over {
                canvas[di..di + 4].copy_from_slice(&sub_rgba[si..si + 4]);
            } else {
                blend_pixel_over(&mut canvas[di..di + 4], &sub_rgba[si..si + 4]);
            }
        }
    }
}

fn blend_pixel_over(dst: &mut [u8], src: &[u8]) {
    let sa = u32::from(src[3]);
    if sa == 0 {
        return;
    }
    if sa == 255 {
        dst.copy_from_slice(src);
        return;
    }
    let da = u32::from(dst[3]);
    if da == 0 {
        dst.copy_from_slice(src);
        return;
    }
    let inv_sa = 255 - sa;
    let out_a_255 = sa * 255 + da * inv_sa;
    let out_a = (out_a_255 + 127) / 255;
    if out_a == 0 {
        dst.fill(0);
        return;
    }
    for ch in 0..3 {
        let num = u32::from(src[ch]) * sa * 255 + u32::from(dst[ch]) * da * inv_sa;
        dst[ch] = ((num + out_a_255 / 2) / out_a_255).min(255) as u8;
    }
    dst[3] = out_a.min(255) as u8;
}

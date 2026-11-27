use std::io::Cursor;

use image_webp::WebPDecoder;
use jxl_oxide::JxlImage;
use png::{ColorType, Decoder as PngDecoder, Limits as PngLimits, Transformations};
use zune_jpeg::zune_core::bytestream::ZCursor;
use zune_jpeg::zune_core::colorspace::ColorSpace;
use zune_jpeg::zune_core::options::DecoderOptions;
use zune_jpeg::JpegDecoder;

use crate::{
    gif_session, pixel_ops, premultiply_rgba_in_place, try_alloc_zeroed,
    validate_source_dimensions, DecodedRgba, MediaError, FORMAT_GIF, FORMAT_JPEG, FORMAT_JXL,
    FORMAT_PNG, FORMAT_WEBP, MAX_DECODE_ALLOC_BYTES, MAX_OUTPUT_DIMENSION, MAX_OUTPUT_PIXELS,
    MAX_SOURCE_DIMENSION,
};

pub(crate) struct DecodedStaticMeta {
    pub width: u32,
    pub height: u32,
    pub src_width: u32,
    pub src_height: u32,
    pub rgba: Vec<u8>,
}

pub(crate) fn probe_bounds(format: i32, data: &[u8]) -> Result<(u32, u32), MediaError> {
    if data.is_empty() {
        return Err(MediaError::InvalidInput);
    }
    match format {
        FORMAT_PNG => probe_png_bounds(data),
        FORMAT_JPEG => probe_jpeg_bounds(data),
        FORMAT_WEBP => probe_webp_bounds(data),
        FORMAT_GIF => gif_session::probe_gif_bounds(data),
        FORMAT_JXL => probe_jxl_bounds(data),
        _ => Err(MediaError::UnsupportedFormat),
    }
}

pub(crate) fn decode_static(
    format: i32,
    data: &[u8],
    req_w: u32,
    req_h: u32,
) -> Result<DecodedRgba, MediaError> {
    let decoded = decode_static_ex(
        format,
        data,
        req_w,
        req_h,
        pixel_ops::EXIF_NORMAL,
        pixel_ops::SCALE_MODE_FIT_INSIDE,
    )?;
    Ok(DecodedRgba {
        width: decoded.width,
        height: decoded.height,
        rgba: decoded.rgba,
    })
}

pub(crate) fn decode_static_ex(
    format: i32,
    data: &[u8],
    req_w: u32,
    req_h: u32,
    exif_orientation: i32,
    scale_mode: i32,
) -> Result<DecodedStaticMeta, MediaError> {
    if data.is_empty() {
        return Err(MediaError::InvalidInput);
    }
    match format {
        FORMAT_PNG => decode_png_static(data, req_w, req_h, exif_orientation, scale_mode),
        FORMAT_JPEG => decode_jpeg_static(data, req_w, req_h, exif_orientation, scale_mode),
        FORMAT_WEBP => decode_webp_static(data, req_w, req_h, exif_orientation, scale_mode),
        FORMAT_GIF => gif_session::decode_gif_first_frame_ex(
            data,
            req_w,
            req_h,
            exif_orientation,
            scale_mode,
        ),
        FORMAT_JXL => decode_jxl_static(data, req_w, req_h, exif_orientation, scale_mode),
        _ => Err(MediaError::UnsupportedFormat),
    }
}

fn finish_rgba(
    mut rgba: Vec<u8>,
    src_w: u32,
    src_h: u32,
    req_w: u32,
    req_h: u32,
    exif_orientation: i32,
    scale_mode: i32,
    is_opaque: bool,
) -> Result<DecodedStaticMeta, MediaError> {
    let (eff_req_w, eff_req_h) = if pixel_ops::swaps_axes(exif_orientation) {
        (req_h, req_w)
    } else {
        (req_w, req_h)
    };
    let (dst_w, dst_h) = pixel_ops::compute_target_dimensions_ex(
        src_w,
        src_h,
        eff_req_w,
        eff_req_h,
        scale_mode,
        MAX_OUTPUT_DIMENSION,
        MAX_OUTPUT_PIXELS,
    )?;
    if !is_opaque {
        premultiply_rgba_in_place(&mut rgba);
    }
    let scaled = if dst_w != src_w || dst_h != src_h {
        pixel_ops::downscale_rgba_fast(&rgba, src_w, src_h, dst_w, dst_h)?
    } else {
        rgba
    };
    let (final_rgba, final_w, final_h) =
        pixel_ops::apply_exif_orientation_rgba(scaled, dst_w, dst_h, exif_orientation)?;
    Ok(DecodedStaticMeta {
        width: final_w,
        height: final_h,
        src_width: src_w,
        src_height: src_h,
        rgba: final_rgba,
    })
}

fn probe_png_bounds(data: &[u8]) -> Result<(u32, u32), MediaError> {
    let mut decoder = PngDecoder::new_with_limits(
        Cursor::new(data),
        PngLimits {
            bytes: MAX_DECODE_ALLOC_BYTES,
        },
    );
    decoder.set_transformations(Transformations::EXPAND | Transformations::STRIP_16);
    let reader = decoder.read_info().map_err(|_| MediaError::DecodeFailed)?;
    let info = reader.info();
    validate_source_dimensions(info.width, info.height)?;
    Ok((info.width, info.height))
}

fn decode_png_static(
    data: &[u8],
    req_w: u32,
    req_h: u32,
    exif_orientation: i32,
    scale_mode: i32,
) -> Result<DecodedStaticMeta, MediaError> {
    let mut decoder = PngDecoder::new_with_limits(
        Cursor::new(data),
        PngLimits {
            bytes: MAX_DECODE_ALLOC_BYTES,
        },
    );
    decoder.set_transformations(Transformations::EXPAND | Transformations::STRIP_16);
    let mut reader = decoder.read_info().map_err(|_| MediaError::DecodeFailed)?;
    let src_w = reader.info().width;
    let src_h = reader.info().height;
    validate_source_dimensions(src_w, src_h)?;

    let buf_size = reader
        .output_buffer_size()
        .ok_or(MediaError::TooLarge)?;
    let mut raw = try_alloc_zeroed(buf_size)?;
    let info = reader
        .next_frame(&mut raw)
        .map_err(|_| MediaError::DecodeFailed)?;
    let bytes = &raw[..info.buffer_size()];
    let is_opaque = matches!(info.color_type, ColorType::Rgb | ColorType::Grayscale);
    let rgba = expand_to_rgba(bytes, src_w, src_h, info.color_type)?;
    finish_rgba(
        rgba,
        src_w,
        src_h,
        req_w,
        req_h,
        exif_orientation,
        scale_mode,
        is_opaque,
    )
}

fn expand_to_rgba(
    src: &[u8],
    width: u32,
    height: u32,
    color_type: ColorType,
) -> Result<Vec<u8>, MediaError> {
    let pixel_count = (width as usize)
        .checked_mul(height as usize)
        .ok_or(MediaError::TooLarge)?;
    let out_len = pixel_count.checked_mul(4).ok_or(MediaError::TooLarge)?;
    match color_type {
        ColorType::Rgba => {
            if src.len() < out_len {
                return Err(MediaError::DecodeFailed);
            }
            let mut out = try_alloc_zeroed(out_len)?;
            out.copy_from_slice(&src[..out_len]);
            Ok(out)
        }
        ColorType::Rgb => {
            let need = pixel_count.checked_mul(3).ok_or(MediaError::TooLarge)?;
            if src.len() < need {
                return Err(MediaError::DecodeFailed);
            }
            let mut out = try_alloc_zeroed(out_len)?;
            for (dst, s) in out.chunks_exact_mut(4).zip(src[..need].chunks_exact(3)) {
                dst[0] = s[0];
                dst[1] = s[1];
                dst[2] = s[2];
                dst[3] = 255;
            }
            Ok(out)
        }
        ColorType::GrayscaleAlpha => {
            let need = pixel_count.checked_mul(2).ok_or(MediaError::TooLarge)?;
            if src.len() < need {
                return Err(MediaError::DecodeFailed);
            }
            let mut out = try_alloc_zeroed(out_len)?;
            for (dst, s) in out.chunks_exact_mut(4).zip(src[..need].chunks_exact(2)) {
                dst[0] = s[0];
                dst[1] = s[0];
                dst[2] = s[0];
                dst[3] = s[1];
            }
            Ok(out)
        }
        ColorType::Grayscale => {
            if src.len() < pixel_count {
                return Err(MediaError::DecodeFailed);
            }
            let mut out = try_alloc_zeroed(out_len)?;
            for (dst, &g) in out.chunks_exact_mut(4).zip(src[..pixel_count].iter()) {
                dst[0] = g;
                dst[1] = g;
                dst[2] = g;
                dst[3] = 255;
            }
            Ok(out)
        }
        ColorType::Indexed => Err(MediaError::DecodeFailed),
    }
}

fn jpeg_options() -> DecoderOptions {
    DecoderOptions::default()
        .jpeg_set_out_colorspace(ColorSpace::RGBA)
        .set_max_width(MAX_SOURCE_DIMENSION as usize)
        .set_max_height(MAX_SOURCE_DIMENSION as usize)
}

fn probe_jpeg_bounds(data: &[u8]) -> Result<(u32, u32), MediaError> {
    let mut decoder = JpegDecoder::new_with_options(ZCursor::new(data), jpeg_options());
    decoder
        .decode_headers()
        .map_err(|_| MediaError::DecodeFailed)?;
    let (w, h) = decoder.dimensions().ok_or(MediaError::DecodeFailed)?;
    let wu = u32::try_from(w).map_err(|_| MediaError::TooLarge)?;
    let hu = u32::try_from(h).map_err(|_| MediaError::TooLarge)?;
    validate_source_dimensions(wu, hu)?;
    Ok((wu, hu))
}

fn decode_jpeg_static(
    data: &[u8],
    req_w: u32,
    req_h: u32,
    exif_orientation: i32,
    scale_mode: i32,
) -> Result<DecodedStaticMeta, MediaError> {
    let mut decoder = JpegDecoder::new_with_options(ZCursor::new(data), jpeg_options());
    decoder
        .decode_headers()
        .map_err(|_| MediaError::DecodeFailed)?;
    let (w, h) = decoder.dimensions().ok_or(MediaError::DecodeFailed)?;
    let src_w = u32::try_from(w).map_err(|_| MediaError::TooLarge)?;
    let src_h = u32::try_from(h).map_err(|_| MediaError::TooLarge)?;
    validate_source_dimensions(src_w, src_h)?;
    let buf_size = decoder
        .output_buffer_size()
        .ok_or(MediaError::TooLarge)?;
    let mut rgba = try_alloc_zeroed(buf_size)?;
    decoder
        .decode_into(&mut rgba)
        .map_err(|_| MediaError::DecodeFailed)?;
    finish_rgba(
        rgba,
        src_w,
        src_h,
        req_w,
        req_h,
        exif_orientation,
        scale_mode,
        true,
    )
}

fn probe_webp_bounds(data: &[u8]) -> Result<(u32, u32), MediaError> {
    let mut decoder = WebPDecoder::new(Cursor::new(data)).map_err(|_| MediaError::DecodeFailed)?;
    decoder.set_memory_limit(MAX_DECODE_ALLOC_BYTES);
    let (w, h) = decoder.dimensions();
    validate_source_dimensions(w, h)?;
    Ok((w, h))
}

fn decode_webp_static(
    data: &[u8],
    req_w: u32,
    req_h: u32,
    exif_orientation: i32,
    scale_mode: i32,
) -> Result<DecodedStaticMeta, MediaError> {
    let mut decoder = WebPDecoder::new(Cursor::new(data)).map_err(|_| MediaError::DecodeFailed)?;
    decoder.set_memory_limit(MAX_DECODE_ALLOC_BYTES);
    let (src_w, src_h) = decoder.dimensions();
    validate_source_dimensions(src_w, src_h)?;

    let pixel_count = (src_w as usize)
        .checked_mul(src_h as usize)
        .ok_or(MediaError::TooLarge)?;
    let has_alpha = decoder.has_alpha();
    let channels = if has_alpha { 4 } else { 3 };
    let need = pixel_count
        .checked_mul(channels)
        .ok_or(MediaError::TooLarge)?;
    let mut raw = try_alloc_zeroed(need)?;
    decoder
        .read_image(&mut raw)
        .map_err(|_| MediaError::DecodeFailed)?;

    let rgba = if has_alpha {
        raw
    } else {
        expand_to_rgba(&raw, src_w, src_h, ColorType::Rgb)?
    };
    finish_rgba(
        rgba,
        src_w,
        src_h,
        req_w,
        req_h,
        exif_orientation,
        scale_mode,
        !has_alpha,
    )
}

fn probe_jxl_bounds(data: &[u8]) -> Result<(u32, u32), MediaError> {
    let image = JxlImage::builder()
        .read(Cursor::new(data))
        .map_err(|_| MediaError::DecodeFailed)?;
    let (w, h) = (image.width(), image.height());
    validate_source_dimensions(w, h)?;
    Ok((w, h))
}

fn decode_jxl_static(
    data: &[u8],
    req_w: u32,
    req_h: u32,
    exif_orientation: i32,
    scale_mode: i32,
) -> Result<DecodedStaticMeta, MediaError> {
    let image = JxlImage::builder()
        .read(Cursor::new(data))
        .map_err(|_| MediaError::DecodeFailed)?;
    let (src_w, src_h) = (image.width(), image.height());
    validate_source_dimensions(src_w, src_h)?;

    let render = image
        .render_frame(0)
        .map_err(|_| MediaError::DecodeFailed)?;
    let fb = render.image_all_channels();
    let fb_w = fb.width() as u32;
    let fb_h = fb.height() as u32;
    let channels = fb.channels();
    if fb_w != src_w || fb_h != src_h || channels == 0 {
        return Err(MediaError::DecodeFailed);
    }

    let pixel_count = (src_w as usize)
        .checked_mul(src_h as usize)
        .ok_or(MediaError::TooLarge)?;
    let out_len = pixel_count.checked_mul(4).ok_or(MediaError::TooLarge)?;
    let mut rgba = try_alloc_zeroed(out_len)?;
    let samples = fb.buf();
    let expected = pixel_count.checked_mul(channels).ok_or(MediaError::TooLarge)?;
    if samples.len() < expected {
        return Err(MediaError::DecodeFailed);
    }

    for (dst, src) in rgba.chunks_exact_mut(4).zip(samples.chunks_exact(channels)) {
        match channels {
            1 => {
                let g = f32_to_u8(src[0]);
                dst[0] = g;
                dst[1] = g;
                dst[2] = g;
                dst[3] = 255;
            }
            2 => {
                let g = f32_to_u8(src[0]);
                dst[0] = g;
                dst[1] = g;
                dst[2] = g;
                dst[3] = f32_to_u8(src[1]);
            }
            3 => {
                dst[0] = f32_to_u8(src[0]);
                dst[1] = f32_to_u8(src[1]);
                dst[2] = f32_to_u8(src[2]);
                dst[3] = 255;
            }
            _ => {
                dst[0] = f32_to_u8(src[0]);
                dst[1] = f32_to_u8(src[1]);
                dst[2] = f32_to_u8(src[2]);
                dst[3] = f32_to_u8(src[3]);
            }
        }
    }
    finish_rgba(
        rgba,
        src_w,
        src_h,
        req_w,
        req_h,
        exif_orientation,
        scale_mode,
        channels == 1 || channels == 3,
    )
}

#[inline]
fn f32_to_u8(sample: f32) -> u8 {
    (sample.clamp(0.0, 1.0) * 255.0 + 0.5) as u8
}

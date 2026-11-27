use std::io::Cursor;

use image_webp::WebPDecoder;
use png::{ColorType, Decoder as PngDecoder, Limits as PngLimits, Transformations};

use crate::{
    pixel_ops, premultiply_rgba_in_place,
    static_decoders::{self, DecodedStaticMeta},
    try_alloc_zeroed, validate_source_dimensions, MediaError, MAX_DECODE_ALLOC_BYTES,
    MAX_OUTPUT_DIMENSION, MAX_OUTPUT_PIXELS, MAX_RUST_LOSSY_WEBP_PIXELS, MAX_RUST_PNG_PIXELS,
};

pub(crate) fn probe_png_bounds(data: &[u8]) -> Result<(u32, u32), MediaError> {
    let decoder = PngDecoder::new_with_limits(
        Cursor::new(data),
        PngLimits {
            bytes: MAX_DECODE_ALLOC_BYTES,
        },
    );
    let reader = decoder.read_info().map_err(|_| MediaError::DecodeFailed)?;
    let info = reader.info();
    validate_source_dimensions(info.width, info.height)?;
    Ok((info.width, info.height))
}

pub(crate) fn decode_png_static(
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
    let (src_w, src_h, interlaced) = {
        let info = reader.info();
        (info.width, info.height, info.interlaced)
    };
    validate_source_dimensions(src_w, src_h)?;

    let pixel_count_u64 = u64::from(src_w) * u64::from(src_h);
    if pixel_count_u64 > MAX_RUST_PNG_PIXELS {
        if interlaced {
            return Err(MediaError::DeferToPlatform);
        }
        return decode_png_streaming_downscale(
            reader,
            src_w,
            src_h,
            req_w,
            req_h,
            exif_orientation,
            scale_mode,
        );
    }

    let buf_size = reader.output_buffer_size().ok_or(MediaError::TooLarge)?;
    let mut raw = try_alloc_zeroed(buf_size)?;
    let out_info = reader
        .next_frame(&mut raw)
        .map_err(|_| MediaError::DecodeFailed)?;
    let slice = raw
        .get(..out_info.buffer_size())
        .ok_or(MediaError::DecodeFailed)?;

    let pixel_count = (src_w as usize)
        .checked_mul(src_h as usize)
        .ok_or(MediaError::TooLarge)?;
    let (rgba, is_opaque) = match out_info.color_type {
        ColorType::Rgba => {
            let expected = pixel_count.checked_mul(4).ok_or(MediaError::TooLarge)?;
            if slice.len() < expected {
                return Err(MediaError::DecodeFailed);
            }
            (slice[..expected].to_vec(), false)
        }
        ColorType::Rgb => (expand_rgb_to_rgba(slice, pixel_count)?, true),
        ColorType::GrayscaleAlpha => (expand_ga_to_rgba(slice, pixel_count)?, false),
        ColorType::Grayscale => (expand_g_to_rgba(slice, pixel_count)?, true),
        ColorType::Indexed => return Err(MediaError::DecodeFailed),
    };

    static_decoders::finish_rgba(
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

pub(crate) fn probe_webp_bounds(data: &[u8]) -> Result<(u32, u32), MediaError> {
    let mut decoder = WebPDecoder::new(Cursor::new(data)).map_err(|_| MediaError::DecodeFailed)?;
    decoder.set_memory_limit(MAX_DECODE_ALLOC_BYTES);
    let (w, h) = decoder.dimensions();
    validate_source_dimensions(w, h)?;
    Ok((w, h))
}

pub(crate) fn decode_webp_static(
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

    let pixels = u64::from(src_w) * u64::from(src_h);
    if decoder.is_animated() {
        return Err(MediaError::DeferToPlatform);
    }
    if decoder.is_lossy() {
        if pixels > MAX_RUST_LOSSY_WEBP_PIXELS {
            return Err(MediaError::DeferToPlatform);
        }
    } else if pixels > MAX_RUST_PNG_PIXELS {
        // Lossless WebP (VP8L) must never fall back to C libwebp.
        return Err(MediaError::TooLarge);
    }

    let out_len = decoder.output_buffer_size().ok_or(MediaError::TooLarge)?;
    let mut raw = try_alloc_zeroed(out_len)?;
    decoder
        .read_image(&mut raw)
        .map_err(|_| MediaError::DecodeFailed)?;

    let pixel_count = (src_w as usize)
        .checked_mul(src_h as usize)
        .ok_or(MediaError::TooLarge)?;
    let (rgba, is_opaque) = if decoder.has_alpha() {
        let expected = pixel_count.checked_mul(4).ok_or(MediaError::TooLarge)?;
        if raw.len() < expected {
            return Err(MediaError::DecodeFailed);
        }
        raw.truncate(expected);
        (raw, false)
    } else {
        (expand_rgb_to_rgba(&raw, pixel_count)?, true)
    };

    static_decoders::finish_rgba(
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

fn expand_rgb_to_rgba(src: &[u8], pixel_count: usize) -> Result<Vec<u8>, MediaError> {
    let expected_src = pixel_count.checked_mul(3).ok_or(MediaError::TooLarge)?;
    let expected_dst = pixel_count.checked_mul(4).ok_or(MediaError::TooLarge)?;
    if src.len() < expected_src {
        return Err(MediaError::DecodeFailed);
    }
    let mut rgba = try_alloc_zeroed(expected_dst)?;
    for (dst, px) in rgba.chunks_exact_mut(4).zip(src[..expected_src].chunks_exact(3)) {
        dst[0] = px[0];
        dst[1] = px[1];
        dst[2] = px[2];
        dst[3] = 255;
    }
    Ok(rgba)
}

fn expand_ga_to_rgba(src: &[u8], pixel_count: usize) -> Result<Vec<u8>, MediaError> {
    let expected_src = pixel_count.checked_mul(2).ok_or(MediaError::TooLarge)?;
    let expected_dst = pixel_count.checked_mul(4).ok_or(MediaError::TooLarge)?;
    if src.len() < expected_src {
        return Err(MediaError::DecodeFailed);
    }
    let mut rgba = try_alloc_zeroed(expected_dst)?;
    for (dst, px) in rgba.chunks_exact_mut(4).zip(src[..expected_src].chunks_exact(2)) {
        dst[0] = px[0];
        dst[1] = px[0];
        dst[2] = px[0];
        dst[3] = px[1];
    }
    Ok(rgba)
}

fn expand_g_to_rgba(src: &[u8], pixel_count: usize) -> Result<Vec<u8>, MediaError> {
    let expected_dst = pixel_count.checked_mul(4).ok_or(MediaError::TooLarge)?;
    if src.len() < pixel_count {
        return Err(MediaError::DecodeFailed);
    }
    let mut rgba = try_alloc_zeroed(expected_dst)?;
    for (dst, &g) in rgba.chunks_exact_mut(4).zip(src[..pixel_count].iter()) {
        dst[0] = g;
        dst[1] = g;
        dst[2] = g;
        dst[3] = 255;
    }
    Ok(rgba)
}

fn decode_png_streaming_downscale(
    mut reader: png::Reader<Cursor<&[u8]>>,
    src_w: u32,
    src_h: u32,
    req_w: u32,
    req_h: u32,
    exif_orientation: i32,
    scale_mode: i32,
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

    let (color_type, _) = reader.output_color_type();
    if color_type == ColorType::Indexed {
        return Err(MediaError::DecodeFailed);
    }
    let is_opaque = matches!(color_type, ColorType::Rgb | ColorType::Grayscale);
    let line_size = reader.output_line_size(src_w).ok_or(MediaError::TooLarge)?;
    let mut raw_row = try_alloc_zeroed(line_size)?;
    let row_rgba_len = (src_w as usize).checked_mul(4).ok_or(MediaError::TooLarge)?;
    let mut row_rgba = try_alloc_zeroed(row_rgba_len)?;
    let out_len = (dst_w as usize)
        .checked_mul(dst_h as usize)
        .and_then(|n| n.checked_mul(4))
        .ok_or(MediaError::TooLarge)?;
    let mut out_rgba = try_alloc_zeroed(out_len)?;

    let dst_w_usize = dst_w as usize;
    let dst_h_u64 = u64::from(dst_h);
    let src_h_u64 = u64::from(src_h);
    let src_w_u64 = u64::from(src_w);
    let mut x_bounds = Vec::with_capacity(dst_w_usize);
    for dx in 0..dst_w_usize {
        let x0 = ((dx as u64 * src_w_u64) / u64::from(dst_w)) as usize;
        let x1 = ((((dx as u64 + 1) * src_w_u64) / u64::from(dst_w)) as usize)
            .max(x0 + 1)
            .min(src_w as usize);
        x_bounds.push((x0, x1));
    }

    let mut bucket_acc = vec![0u64; dst_w_usize * 4];
    let mut active_dy = 0usize;
    let mut active_rows = 0u64;

    for sy in 0..src_h {
        if reader
            .read_row(&mut raw_row)
            .map_err(|_| MediaError::DecodeFailed)?
            .is_none()
        {
            return Err(MediaError::DecodeFailed);
        }
        expand_png_row_to_rgba(&raw_row, color_type, src_w as usize, &mut row_rgba)?;
        if !is_opaque {
            premultiply_rgba_in_place(&mut row_rgba);
        }
        let dy = ((u64::from(sy) * dst_h_u64) / src_h_u64).min(dst_h_u64 - 1) as usize;
        if dy != active_dy && active_rows > 0 {
            flush_streaming_bucket(
                &mut out_rgba,
                dst_w_usize,
                active_dy,
                &bucket_acc,
                &x_bounds,
                active_rows,
            );
            bucket_acc.fill(0);
            active_rows = 0;
        }
        active_dy = dy;
        active_rows += 1;
        accumulate_png_row(&row_rgba, &x_bounds, &mut bucket_acc);
    }

    if active_rows > 0 {
        flush_streaming_bucket(
            &mut out_rgba,
            dst_w_usize,
            active_dy,
            &bucket_acc,
            &x_bounds,
            active_rows,
        );
    }

    let (final_rgba, final_w, final_h) =
        pixel_ops::apply_exif_orientation_rgba(out_rgba, dst_w, dst_h, exif_orientation)?;
    Ok(DecodedStaticMeta {
        width: final_w,
        height: final_h,
        src_width: src_w,
        src_height: src_h,
        rgba: final_rgba,
    })
}

fn expand_png_row_to_rgba(
    raw_row: &[u8],
    color_type: ColorType,
    width: usize,
    dst: &mut [u8],
) -> Result<(), MediaError> {
    let dst_len = width.checked_mul(4).ok_or(MediaError::TooLarge)?;
    if dst.len() < dst_len {
        return Err(MediaError::DecodeFailed);
    }
    match color_type {
        ColorType::Rgba => {
            if raw_row.len() < dst_len {
                return Err(MediaError::DecodeFailed);
            }
            dst[..dst_len].copy_from_slice(&raw_row[..dst_len]);
        }
        ColorType::Rgb => {
            let src_len = width.checked_mul(3).ok_or(MediaError::TooLarge)?;
            if raw_row.len() < src_len {
                return Err(MediaError::DecodeFailed);
            }
            for (out, px) in dst[..dst_len]
                .chunks_exact_mut(4)
                .zip(raw_row[..src_len].chunks_exact(3))
            {
                out[0] = px[0];
                out[1] = px[1];
                out[2] = px[2];
                out[3] = 255;
            }
        }
        ColorType::GrayscaleAlpha => {
            let src_len = width.checked_mul(2).ok_or(MediaError::TooLarge)?;
            if raw_row.len() < src_len {
                return Err(MediaError::DecodeFailed);
            }
            for (out, px) in dst[..dst_len]
                .chunks_exact_mut(4)
                .zip(raw_row[..src_len].chunks_exact(2))
            {
                out[0] = px[0];
                out[1] = px[0];
                out[2] = px[0];
                out[3] = px[1];
            }
        }
        ColorType::Grayscale => {
            if raw_row.len() < width {
                return Err(MediaError::DecodeFailed);
            }
            for (out, &g) in dst[..dst_len]
                .chunks_exact_mut(4)
                .zip(raw_row[..width].iter())
            {
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

fn accumulate_png_row(row_rgba: &[u8], x_bounds: &[(usize, usize)], bucket_acc: &mut [u64]) {
    for (dx, &(x0, x1)) in x_bounds.iter().enumerate() {
        let mut r = 0u64;
        let mut g = 0u64;
        let mut b = 0u64;
        let mut a = 0u64;
        for sx in x0..x1 {
            let idx = sx * 4;
            r += u64::from(row_rgba[idx]);
            g += u64::from(row_rgba[idx + 1]);
            b += u64::from(row_rgba[idx + 2]);
            a += u64::from(row_rgba[idx + 3]);
        }
        let acc_idx = dx * 4;
        bucket_acc[acc_idx] += r;
        bucket_acc[acc_idx + 1] += g;
        bucket_acc[acc_idx + 2] += b;
        bucket_acc[acc_idx + 3] += a;
    }
}

fn flush_streaming_bucket(
    out_rgba: &mut [u8],
    dst_w: usize,
    dst_y: usize,
    bucket_acc: &[u64],
    x_bounds: &[(usize, usize)],
    row_count: u64,
) {
    let row_base = dst_y * dst_w * 4;
    for (dx, &(x0, x1)) in x_bounds.iter().enumerate() {
        let samples = ((x1 - x0) as u64).saturating_mul(row_count).max(1);
        let acc_idx = dx * 4;
        let out_idx = row_base + acc_idx;
        out_rgba[out_idx] = (bucket_acc[acc_idx] / samples).min(255) as u8;
        out_rgba[out_idx + 1] = (bucket_acc[acc_idx + 1] / samples).min(255) as u8;
        out_rgba[out_idx + 2] = (bucket_acc[acc_idx + 2] / samples).min(255) as u8;
        out_rgba[out_idx + 3] = (bucket_acc[acc_idx + 3] / samples).min(255) as u8;
    }
}

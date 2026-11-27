mod gif_session;
mod jni_bridge;
mod static_decoders;

pub(crate) const FORMAT_PNG: i32 = 1;
pub(crate) const FORMAT_JPEG: i32 = 2;
pub(crate) const FORMAT_WEBP: i32 = 3;
pub(crate) const FORMAT_GIF: i32 = 4;
pub(crate) const FORMAT_JXL: i32 = 5;

pub(crate) const MAX_SOURCE_DIMENSION: u32 = 65_536;
pub(crate) const MAX_SOURCE_PIXELS: u64 = 300_000_000;
pub(crate) const MAX_OUTPUT_DIMENSION: u32 = 4096;
pub(crate) const MAX_OUTPUT_PIXELS: u64 = 16_777_216;
pub(crate) const MAX_GIF_DIMENSION: u32 = 2048;
pub(crate) const MAX_GIF_PIXELS: u64 = 4_194_304;
pub(crate) const MAX_GIF_FRAMES: usize = 4096;
pub(crate) const MAX_DECODE_ALLOC_BYTES: usize = 128 * 1024 * 1024;

#[derive(Debug)]
pub(crate) enum MediaError {
    InvalidInput,
    UnsupportedFormat,
    TooLarge,
    OutOfMemory,
    DecodeFailed,
}

pub(crate) struct DecodedRgba {
    pub width: u32,
    pub height: u32,
    pub rgba: Vec<u8>,
}

pub(crate) fn try_alloc_zeroed(len: usize) -> Result<Vec<u8>, MediaError> {
    if len > MAX_DECODE_ALLOC_BYTES {
        return Err(MediaError::TooLarge);
    }
    let mut buf = Vec::new();
    if buf.try_reserve_exact(len).is_err() {
        return Err(MediaError::OutOfMemory);
    }
    buf.resize(len, 0u8);
    Ok(buf)
}

pub(crate) fn validate_source_dimensions(width: u32, height: u32) -> Result<(), MediaError> {
    if width == 0 || height == 0 || width > MAX_SOURCE_DIMENSION || height > MAX_SOURCE_DIMENSION {
        return Err(MediaError::TooLarge);
    }
    let pixels = u64::from(width) * u64::from(height);
    if pixels > MAX_SOURCE_PIXELS {
        return Err(MediaError::TooLarge);
    }
    Ok(())
}

pub(crate) fn compute_target_dimensions(
    src_w: u32,
    src_h: u32,
    req_w: u32,
    req_h: u32,
    max_dim: u32,
    max_pixels: u64,
) -> Result<(u32, u32), MediaError> {
    validate_source_dimensions(src_w, src_h)?;
    let cap_w = if req_w > 0 { req_w.min(max_dim) } else { max_dim };
    let cap_h = if req_h > 0 { req_h.min(max_dim) } else { max_dim };

    let scale = (cap_w as f64 / src_w as f64)
        .min(cap_h as f64 / src_h as f64)
        .min(1.0);
    let mut out_w = ((src_w as f64 * scale).round() as u32).max(1);
    let mut out_h = ((src_h as f64 * scale).round() as u32).max(1);

    let px = u64::from(out_w) * u64::from(out_h);
    if px > max_pixels {
        let p_scale = (max_pixels as f64 / px as f64).sqrt();
        out_w = ((out_w as f64 * p_scale) as u32).max(1);
        out_h = ((out_h as f64 * p_scale) as u32).max(1);
    }
    Ok((out_w, out_h))
}

pub(crate) fn premultiply_rgba_in_place(rgba: &mut [u8]) {
    for px in rgba.chunks_exact_mut(4) {
        let a = px[3];
        if a == 255 {
            continue;
        }
        if a == 0 {
            px[0] = 0;
            px[1] = 0;
            px[2] = 0;
            continue;
        }
        let a16 = u16::from(a);
        px[0] = ((u16::from(px[0]) * a16 + 127) / 255) as u8;
        px[1] = ((u16::from(px[1]) * a16 + 127) / 255) as u8;
        px[2] = ((u16::from(px[2]) * a16 + 127) / 255) as u8;
    }
}

pub(crate) fn downscale_rgba_bilinear(
    src: &[u8],
    src_w: u32,
    src_h: u32,
    dst_w: u32,
    dst_h: u32,
) -> Result<Vec<u8>, MediaError> {
    let expected_src = (src_w as usize)
        .checked_mul(src_h as usize)
        .and_then(|n| n.checked_mul(4))
        .ok_or(MediaError::TooLarge)?;
    if src.len() < expected_src || dst_w == 0 || dst_h == 0 {
        return Err(MediaError::InvalidInput);
    }
    if src_w == dst_w && src_h == dst_h {
        let mut copy = try_alloc_zeroed(expected_src)?;
        copy.copy_from_slice(&src[..expected_src]);
        return Ok(copy);
    }

    let out_len = (dst_w as usize)
        .checked_mul(dst_h as usize)
        .and_then(|n| n.checked_mul(4))
        .ok_or(MediaError::TooLarge)?;
    let mut dst = try_alloc_zeroed(out_len)?;

    let sw = src_w as usize;
    let sh = src_h as usize;
    let dw = dst_w as usize;
    let dh = dst_h as usize;

    for dy in 0..dh {
        let sy_f = ((dy as f32 + 0.5) * (sh as f32 / dh as f32)) - 0.5;
        let y0 = (sy_f.floor().max(0.0) as usize).min(sh - 1);
        let y1 = (y0 + 1).min(sh - 1);
        let wy1 = (sy_f - y0 as f32).clamp(0.0, 1.0);
        let wy0 = 1.0 - wy1;

        for dx in 0..dw {
            let sx_f = ((dx as f32 + 0.5) * (sw as f32 / dw as f32)) - 0.5;
            let x0 = (sx_f.floor().max(0.0) as usize).min(sw - 1);
            let x1 = (x0 + 1).min(sw - 1);
            let wx1 = (sx_f - x0 as f32).clamp(0.0, 1.0);
            let wx0 = 1.0 - wx1;

            let i00 = (y0 * sw + x0) * 4;
            let i10 = (y0 * sw + x1) * 4;
            let i01 = (y1 * sw + x0) * 4;
            let i11 = (y1 * sw + x1) * 4;
            let di = (dy * dw + dx) * 4;

            let w00 = wx0 * wy0;
            let w10 = wx1 * wy0;
            let w01 = wx0 * wy1;
            let w11 = wx1 * wy1;

            for c in 0..4 {
                let v = src[i00 + c] as f32 * w00
                    + src[i10 + c] as f32 * w10
                    + src[i01 + c] as f32 * w01
                    + src[i11 + c] as f32 * w11;
                dst[di + c] = v.round().clamp(0.0, 255.0) as u8;
            }
        }
    }
    Ok(dst)
}

#[cfg(test)]
mod tests {
    use super::*;

    const SAMPLE_GIF_1X1: &[u8] = &[
        b'G', b'I', b'F', b'8', b'9', b'a', 0x01, 0x00, 0x01, 0x00, 0x80, 0x00, 0x00, 0xFF, 0x00,
        0x00, 0x00, 0x00, 0x00, 0x21, 0xF9, 0x04, 0x00, 0x05, 0x00, 0x00, 0x00, 0x2C, 0x00, 0x00,
        0x00, 0x00, 0x01, 0x00, 0x01, 0x00, 0x00, 0x02, 0x02, 0x44, 0x01, 0x00, 0x3B,
    ];

    fn make_test_png_2x1() -> Vec<u8> {
        let mut out = Vec::new();
        {
            let mut encoder = png::Encoder::new(&mut out, 2, 1);
            encoder.set_color(png::ColorType::Rgba);
            encoder.set_depth(png::BitDepth::Eight);
            let mut writer = encoder.write_header().unwrap();
            writer
                .write_image_data(&[255, 0, 0, 255, 0, 200, 100, 128])
                .unwrap();
        }
        out
    }

    fn make_test_webp_1x1() -> Vec<u8> {
        let mut out = Vec::new();
        image_webp::WebPEncoder::new(&mut out)
            .encode(&[10, 20, 30, 255], 1, 1, image_webp::ColorType::Rgba8)
            .unwrap();
        out
    }

    #[test]
    fn dimension_validation_enforces_decompression_bomb_caps() {
        assert!(validate_source_dimensions(0, 10).is_err());
        assert!(validate_source_dimensions(65_537, 10).is_err());
        assert!(validate_source_dimensions(20_000, 20_000).is_err());
        assert!(validate_source_dimensions(4096, 4096).is_ok());

        let scaled = compute_target_dimensions(8000, 4000, 1000, 1000, 4096, 16_777_216).unwrap();
        assert_eq!(scaled, (1000, 500));
    }

    #[test]
    fn premultiply_scales_rgb_by_alpha() {
        let mut px = [200u8, 100, 50, 128, 200, 100, 50, 0, 200, 100, 50, 255];
        premultiply_rgba_in_place(&mut px);
        assert_eq!(&px[0..4], &[100, 50, 25, 128]);
        assert_eq!(&px[4..8], &[0, 0, 0, 0]);
        assert_eq!(&px[8..12], &[200, 100, 50, 255]);
    }

    #[test]
    fn downscale_reduces_4x4_to_2x2() {
        let src = vec![255u8; 4 * 4 * 4];
        let dst = downscale_rgba_bilinear(&src, 4, 4, 2, 2).unwrap();
        assert_eq!(dst.len(), 2 * 2 * 4);
        assert!(dst.iter().all(|&b| b == 255));
    }

    #[test]
    fn png_webp_and_gif_round_trip_decodes() {
        let png_bytes = make_test_png_2x1();
        assert_eq!(
            static_decoders::probe_bounds(FORMAT_PNG, &png_bytes).unwrap(),
            (2, 1)
        );
        let decoded_png =
            static_decoders::decode_static(FORMAT_PNG, &png_bytes, 0, 0).unwrap();
        assert_eq!((decoded_png.width, decoded_png.height), (2, 1));
        assert_eq!(decoded_png.rgba, vec![255, 0, 0, 255, 0, 100, 50, 128]);

        let webp_bytes = make_test_webp_1x1();
        assert_eq!(
            static_decoders::probe_bounds(FORMAT_WEBP, &webp_bytes).unwrap(),
            (1, 1)
        );
        let decoded_webp =
            static_decoders::decode_static(FORMAT_WEBP, &webp_bytes, 0, 0).unwrap();
        assert_eq!((decoded_webp.width, decoded_webp.height), (1, 1));
        assert_eq!(decoded_webp.rgba, vec![10, 20, 30, 255]);

        assert_eq!(
            static_decoders::probe_bounds(FORMAT_GIF, SAMPLE_GIF_1X1).unwrap(),
            (1, 1)
        );
        let open = gif_session::open_session(SAMPLE_GIF_1X1.to_vec(), 0, 0).unwrap();
        assert_eq!((open.width, open.height, open.frame_count), (1, 1, 1));
        let frame = gif_session::render_frame(open.handle, 0).unwrap();
        assert_eq!(frame.duration_ms, 50);
        assert_eq!(frame.rgba, vec![255, 0, 0, 255]);
        gif_session::close_session(open.handle);
    }
}

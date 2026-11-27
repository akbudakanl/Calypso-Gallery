mod gif_session;
mod jni_bridge;
mod pixel_ops;
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
    pixel_ops::compute_target_dimensions_ex(
        src_w,
        src_h,
        req_w,
        req_h,
        pixel_ops::SCALE_MODE_FIT_INSIDE,
        max_dim,
        max_pixels,
    )
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
    pixel_ops::downscale_rgba_fast(src, src_w, src_h, dst_w, dst_h)
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
    fn fill_min_side_keeps_portrait_thumbnail_sharp() {
        let scaled = pixel_ops::compute_target_dimensions_ex(
            1080,
            2400,
            360,
            360,
            pixel_ops::SCALE_MODE_FILL_MIN_SIDE,
            MAX_OUTPUT_DIMENSION,
            MAX_OUTPUT_PIXELS,
        )
        .unwrap();
        assert_eq!(scaled, (360, 800));
    }

    #[test]
    fn exif_orientation_transforms_pixels_and_dimensions() {
        let px = vec![
            1, 0, 0, 255, // (0,0)
            2, 0, 0, 255, // (1,0)
            3, 0, 0, 255, // (0,1)
            4, 0, 0, 255, // (1,1)
            5, 0, 0, 255, // (0,2)
            6, 0, 0, 255, // (1,2)
        ];
        let (rot90, rw, rh) =
            pixel_ops::apply_exif_orientation_rgba(px.clone(), 2, 3, pixel_ops::EXIF_ROTATE_90)
                .unwrap();
        assert_eq!((rw, rh), (3, 2));
        // Row 0: (0,2)=5, (0,1)=3, (0,0)=1
        assert_eq!(rot90[0], 5);
        assert_eq!(rot90[4], 3);
        assert_eq!(rot90[8], 1);

        let (rot180, w180, h180) =
            pixel_ops::apply_exif_orientation_rgba(px.clone(), 2, 3, pixel_ops::EXIF_ROTATE_180)
                .unwrap();
        assert_eq!((w180, h180), (2, 3));
        assert_eq!(rot180[0], 6);

        let (flipped, fw, fh) =
            pixel_ops::apply_exif_orientation_rgba(px, 2, 3, pixel_ops::EXIF_FLIP_H).unwrap();
        assert_eq!((fw, fh), (2, 3));
        assert_eq!(flipped[0], 2);
        assert_eq!(flipped[4], 1);
    }

    #[test]
    fn fast_box_downscale_reduces_16x16_to_4x4() {
        let src = vec![180u8; 16 * 16 * 4];
        let dst = pixel_ops::downscale_rgba_fast(&src, 16, 16, 4, 4).unwrap();
        assert_eq!(dst.len(), 4 * 4 * 4);
        assert!(dst.iter().all(|&b| b == 180));
    }

    #[test]
    fn decode_static_ex_and_gif_render_into_work() {
        let png_bytes = make_test_png_2x1();
        let ex = static_decoders::decode_static_ex(
            FORMAT_PNG,
            &png_bytes,
            0,
            0,
            pixel_ops::EXIF_ROTATE_90,
            pixel_ops::SCALE_MODE_FIT_INSIDE,
        )
        .unwrap();
        assert_eq!((ex.width, ex.height), (1, 2));
        assert_eq!((ex.src_width, ex.src_height), (2, 1));

        let open = gif_session::open_session(SAMPLE_GIF_1X1.to_vec(), 0, 0).unwrap();
        let mut buf = vec![0u8; 4];
        let delay = gif_session::render_frame_into(open.handle, 0, &mut buf).unwrap();
        assert_eq!(delay, 50);
        assert_eq!(buf, vec![255, 0, 0, 255]);
        gif_session::close_session(open.handle);
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

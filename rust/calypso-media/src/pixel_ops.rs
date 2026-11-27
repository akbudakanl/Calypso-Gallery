use crate::{try_alloc_zeroed, validate_source_dimensions, MediaError};

pub(crate) const SCALE_MODE_FIT_INSIDE: i32 = 0;
pub(crate) const SCALE_MODE_FILL_MIN_SIDE: i32 = 1;

pub(crate) const EXIF_NORMAL: i32 = 1;
pub(crate) const EXIF_FLIP_H: i32 = 2;
pub(crate) const EXIF_ROTATE_180: i32 = 3;
pub(crate) const EXIF_FLIP_V: i32 = 4;
pub(crate) const EXIF_TRANSPOSE: i32 = 5;
pub(crate) const EXIF_ROTATE_90: i32 = 6;
pub(crate) const EXIF_TRANSVERSE: i32 = 7;
pub(crate) const EXIF_ROTATE_270: i32 = 8;

const MAX_PANORAMA_MULT: u32 = 4;
const BILINEAR_ONE: u32 = 256;
const STRATIFIED_SAMPLES: u32 = 4;

#[inline]
pub(crate) fn swaps_axes(exif_orientation: i32) -> bool {
    matches!(
        exif_orientation,
        EXIF_TRANSPOSE | EXIF_ROTATE_90 | EXIF_TRANSVERSE | EXIF_ROTATE_270
    )
}

pub(crate) fn compute_target_dimensions_ex(
    src_w: u32,
    src_h: u32,
    req_w: u32,
    req_h: u32,
    scale_mode: i32,
    max_dim: u32,
    max_pixels: u64,
) -> Result<(u32, u32), MediaError> {
    validate_source_dimensions(src_w, src_h)?;
    let bounded_max = max_dim.max(1);
    let cap_w = if req_w > 0 { req_w.min(bounded_max) } else { bounded_max };
    let cap_h = if req_h > 0 { req_h.min(bounded_max) } else { bounded_max };

    let mut scale = if scale_mode == SCALE_MODE_FILL_MIN_SIDE && req_w > 0 && req_h > 0 {
        let min_fill = (cap_w as f64 / src_w as f64).max(cap_h as f64 / src_h as f64);
        let max_side_cap_w = cap_w.saturating_mul(MAX_PANORAMA_MULT).min(bounded_max);
        let max_side_cap_h = cap_h.saturating_mul(MAX_PANORAMA_MULT).min(bounded_max);
        let hard_cap = (max_side_cap_w as f64 / src_w as f64)
            .min(max_side_cap_h as f64 / src_h as f64)
            .min(1.0);
        min_fill.min(hard_cap)
    } else {
        (cap_w as f64 / src_w as f64)
            .min(cap_h as f64 / src_h as f64)
            .min(1.0)
    };
    if !scale.is_finite() || scale <= 0.0 {
        scale = 1.0;
    }

    let mut out_w = ((src_w as f64 * scale).round() as u32).clamp(1, bounded_max);
    let mut out_h = ((src_h as f64 * scale).round() as u32).clamp(1, bounded_max);

    let px = u64::from(out_w) * u64::from(out_h);
    if max_pixels > 0 && px > max_pixels {
        let p_scale = (max_pixels as f64 / px as f64).sqrt();
        out_w = ((out_w as f64 * p_scale) as u32).max(1);
        out_h = ((out_h as f64 * p_scale) as u32).max(1);
    }
    Ok((out_w, out_h))
}

pub(crate) fn downscale_rgba_fast(
    src: &[u8],
    src_w: u32,
    src_h: u32,
    dst_w: u32,
    dst_h: u32,
) -> Result<Vec<u8>, MediaError> {
    let out_len = (dst_w as usize)
        .checked_mul(dst_h as usize)
        .and_then(|n| n.checked_mul(4))
        .ok_or(MediaError::TooLarge)?;
    let mut dst = try_alloc_zeroed(out_len)?;
    downscale_rgba_into(src, src_w, src_h, dst_w, dst_h, &mut dst)?;
    Ok(dst)
}

pub(crate) fn downscale_rgba_into(
    src: &[u8],
    src_w: u32,
    src_h: u32,
    dst_w: u32,
    dst_h: u32,
    dst: &mut [u8],
) -> Result<(), MediaError> {
    let expected_src = (src_w as usize)
        .checked_mul(src_h as usize)
        .and_then(|n| n.checked_mul(4))
        .ok_or(MediaError::TooLarge)?;
    let expected_dst = (dst_w as usize)
        .checked_mul(dst_h as usize)
        .and_then(|n| n.checked_mul(4))
        .ok_or(MediaError::TooLarge)?;
    if src.len() < expected_src || dst.len() < expected_dst || dst_w == 0 || dst_h == 0 {
        return Err(MediaError::InvalidInput);
    }
    if src_w == dst_w && src_h == dst_h {
        dst[..expected_dst].copy_from_slice(&src[..expected_dst]);
        return Ok(());
    }

    if src_w >= dst_w.saturating_mul(2) && src_h >= dst_h.saturating_mul(2) {
        downscale_box_stratified(
            &src[..expected_src],
            src_w as usize,
            src_h as usize,
            &mut dst[..expected_dst],
            dst_w as usize,
            dst_h as usize,
        );
    } else {
        downscale_bilinear_fixed(
            &src[..expected_src],
            src_w as usize,
            src_h as usize,
            &mut dst[..expected_dst],
            dst_w as usize,
            dst_h as usize,
        );
    }
    Ok(())
}

fn downscale_box_stratified(
    src: &[u8],
    sw: usize,
    sh: usize,
    dst: &mut [u8],
    dw: usize,
    dh: usize,
) {
    let x_bounds: Vec<(usize, usize, usize)> = (0..dw)
        .map(|dx| {
            let x0 = dx * sw / dw;
            let x1 = (((dx + 1) * sw + dw - 1) / dw).min(sw).max(x0 + 1);
            let step_x = ((x1 - x0) + (STRATIFIED_SAMPLES as usize - 1)) / (STRATIFIED_SAMPLES as usize);
            (x0, x1, step_x.max(1))
        })
        .collect();

    for dy in 0..dh {
        let y0 = dy * sh / dh;
        let y1 = (((dy + 1) * sh + dh - 1) / dh).min(sh).max(y0 + 1);
        let step_y = (((y1 - y0) + (STRATIFIED_SAMPLES as usize - 1)) / (STRATIFIED_SAMPLES as usize)).max(1);
        let ny = (y0..y1).step_by(step_y).count() as u32;
        let dst_row = &mut dst[dy * dw * 4..(dy + 1) * dw * 4];

        for (dx, &(x0, x1, step_x)) in x_bounds.iter().enumerate() {
            let mut r = 0u32;
            let mut g = 0u32;
            let mut b = 0u32;
            let mut a = 0u32;
            let nx = (x0..x1).step_by(step_x).count() as u32;
            let count = (nx * ny).max(1);

            let mut sy = y0;
            while sy < y1 {
                let row_off = sy * sw;
                let mut sx = x0;
                while sx < x1 {
                    let si = (row_off + sx) * 4;
                    r += u32::from(src[si]);
                    g += u32::from(src[si + 1]);
                    b += u32::from(src[si + 2]);
                    a += u32::from(src[si + 3]);
                    sx += step_x;
                }
                sy += step_y;
            }
            let half = count / 2;
            let di = dx * 4;
            dst_row[di] = ((r + half) / count) as u8;
            dst_row[di + 1] = ((g + half) / count) as u8;
            dst_row[di + 2] = ((b + half) / count) as u8;
            dst_row[di + 3] = ((a + half) / count) as u8;
        }
    }
}

fn downscale_bilinear_fixed(
    src: &[u8],
    sw: usize,
    sh: usize,
    dst: &mut [u8],
    dw: usize,
    dh: usize,
) {
    let scale_x = sw as f32 / dw as f32;
    let scale_y = sh as f32 / dh as f32;
    let x_lut: Vec<(usize, usize, u32, u32)> = (0..dw)
        .map(|dx| {
            let sx_f = ((dx as f32 + 0.5) * scale_x) - 0.5;
            let x0 = (sx_f.floor().max(0.0) as usize).min(sw - 1);
            let x1 = (x0 + 1).min(sw - 1);
            let wx1 = ((sx_f - x0 as f32).clamp(0.0, 1.0) * BILINEAR_ONE as f32).round() as u32;
            let wx0 = BILINEAR_ONE - wx1;
            (x0 * 4, x1 * 4, wx0, wx1)
        })
        .collect();

    for dy in 0..dh {
        let sy_f = ((dy as f32 + 0.5) * scale_y) - 0.5;
        let y0 = (sy_f.floor().max(0.0) as usize).min(sh - 1);
        let y1 = (y0 + 1).min(sh - 1);
        let wy1 = ((sy_f - y0 as f32).clamp(0.0, 1.0) * BILINEAR_ONE as f32).round() as u32;
        let wy0 = BILINEAR_ONE - wy1;
        let row0 = &src[y0 * sw * 4..(y0 + 1) * sw * 4];
        let row1 = &src[y1 * sw * 4..(y1 + 1) * sw * 4];
        let dst_row = &mut dst[dy * dw * 4..(dy + 1) * dw * 4];

        for (dx, &(x0_off, x1_off, wx0, wx1)) in x_lut.iter().enumerate() {
            let w00 = wx0 * wy0;
            let w10 = wx1 * wy0;
            let w01 = wx0 * wy1;
            let w11 = wx1 * wy1;
            let di = dx * 4;
            for c in 0..4 {
                let sum = u32::from(row0[x0_off + c]) * w00
                    + u32::from(row0[x1_off + c]) * w10
                    + u32::from(row1[x0_off + c]) * w01
                    + u32::from(row1[x1_off + c]) * w11;
                dst_row[di + c] = ((sum + 32_768) >> 16) as u8;
            }
        }
    }
}

pub(crate) fn apply_exif_orientation_rgba(
    src: Vec<u8>,
    width: u32,
    height: u32,
    orientation: i32,
) -> Result<(Vec<u8>, u32, u32), MediaError> {
    if !(EXIF_FLIP_H..=EXIF_ROTATE_270).contains(&orientation) {
        return Ok((src, width, height));
    }
    let w = width as usize;
    let h = height as usize;
    let needed = w.checked_mul(h).and_then(|n| n.checked_mul(4)).ok_or(MediaError::TooLarge)?;
    if src.len() < needed || w == 0 || h == 0 {
        return Err(MediaError::InvalidInput);
    }

    let (out_w, out_h) = if swaps_axes(orientation) {
        (height, width)
    } else {
        (width, height)
    };
    let ow = out_w as usize;
    let mut dst = try_alloc_zeroed(needed)?;

    for y in 0..h {
        let src_row = y * w;
        for x in 0..w {
            let (nx, ny) = match orientation {
                EXIF_FLIP_H => (w - 1 - x, y),
                EXIF_ROTATE_180 => (w - 1 - x, h - 1 - y),
                EXIF_FLIP_V => (x, h - 1 - y),
                EXIF_TRANSPOSE => (y, x),
                EXIF_ROTATE_90 => (h - 1 - y, x),
                EXIF_TRANSVERSE => (h - 1 - y, w - 1 - x),
                EXIF_ROTATE_270 => (y, w - 1 - x),
                _ => (x, y),
            };
            let si = (src_row + x) * 4;
            let di = (ny * ow + nx) * 4;
            dst[di..di + 4].copy_from_slice(&src[si..si + 4]);
        }
    }
    Ok((dst, out_w, out_h))
}

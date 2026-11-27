use std::collections::{HashMap, VecDeque};
use std::io::Cursor;
use std::num::NonZeroU64;
use std::sync::{Arc, Mutex, OnceLock};

use gif::{ColorOutput, DecodeOptions, Decoder as GifStreamDecoder, DisposalMethod, MemoryLimit};

use crate::{
    compute_target_dimensions, downscale_rgba_bilinear, premultiply_rgba_in_place,
    try_alloc_zeroed, validate_source_dimensions, DecodedRgba, MediaError, MAX_GIF_DIMENSION,
    MAX_GIF_FRAMES, MAX_GIF_PIXELS, MAX_OUTPUT_DIMENSION, MAX_OUTPUT_PIXELS,
};

const MAX_GIF_SESSIONS: usize = 3;
const GIF_DECODE_MEMORY_LIMIT: usize = 64 * 1024 * 1024;
const DEFAULT_FRAME_DELAY_MS: u32 = 100;
const MIN_FRAME_DELAY_MS: u32 = 20;
const MAX_FRAME_DELAY_MS: u32 = 10_000;

pub(crate) struct GifOpenInfo {
    pub handle: i64,
    pub width: u32,
    pub height: u32,
    pub frame_count: u32,
}

pub(crate) struct GifRenderedFrame {
    pub duration_ms: u32,
    pub rgba: Vec<u8>,
}

struct GifCompositor {
    raw_bytes: Arc<[u8]>,
    delays_ms: Vec<u32>,
    screen_w: u16,
    screen_h: u16,
    out_w: u32,
    out_h: u32,
    canvas: Vec<u8>,
    prev_canvas: Option<Vec<u8>>,
    last_dispose: DisposalMethod,
    last_rect: (u16, u16, u16, u16),
    current_frame: Option<usize>,
}

struct GifSession {
    compositor: GifCompositor,
    decoder: Option<GifStreamDecoder<Cursor<Arc<[u8]>>>>,
}

struct GifStore {
    next_handle: i64,
    order: VecDeque<i64>,
    sessions: HashMap<i64, Arc<Mutex<GifSession>>>,
}

impl GifStore {
    fn new() -> Self {
        Self {
            next_handle: 1,
            order: VecDeque::new(),
            sessions: HashMap::new(),
        }
    }
}

fn store() -> &'static Mutex<GifStore> {
    static STORE: OnceLock<Mutex<GifStore>> = OnceLock::new();
    STORE.get_or_init(|| Mutex::new(GifStore::new()))
}

fn configure_options(skip_decoding: bool) -> DecodeOptions {
    let mut opts = DecodeOptions::new();
    opts.set_color_output(ColorOutput::RGBA);
    opts.skip_frame_decoding(skip_decoding);
    if let Some(limit) = NonZeroU64::new(GIF_DECODE_MEMORY_LIMIT as u64) {
        opts.set_memory_limit(MemoryLimit::Bytes(limit));
    }
    opts
}

fn normalize_delay_ms(delay_cs: u16) -> u32 {
    let raw = u32::from(delay_cs).saturating_mul(10);
    if raw == 0 {
        DEFAULT_FRAME_DELAY_MS
    } else {
        raw.clamp(MIN_FRAME_DELAY_MS, MAX_FRAME_DELAY_MS)
    }
}

pub(crate) fn probe_gif_bounds(data: &[u8]) -> Result<(u32, u32), MediaError> {
    let dec = configure_options(true)
        .read_info(Cursor::new(data))
        .map_err(|_| MediaError::DecodeFailed)?;
    let (w, h) = (u32::from(dec.width()), u32::from(dec.height()));
    validate_source_dimensions(w, h)?;
    Ok((w, h))
}

pub(crate) fn decode_gif_first_frame(
    data: &[u8],
    req_w: u32,
    req_h: u32,
) -> Result<DecodedRgba, MediaError> {
    let mut dec = configure_options(false)
        .read_info(Cursor::new(data))
        .map_err(|_| MediaError::DecodeFailed)?;
    let screen_w = u32::from(dec.width());
    let screen_h = u32::from(dec.height());
    let (dst_w, dst_h) = compute_target_dimensions(
        screen_w,
        screen_h,
        req_w,
        req_h,
        MAX_OUTPUT_DIMENSION,
        MAX_OUTPUT_PIXELS,
    )?;

    let canvas_len = (screen_w as usize)
        .checked_mul(screen_h as usize)
        .and_then(|n| n.checked_mul(4))
        .ok_or(MediaError::TooLarge)?;
    let mut canvas = try_alloc_zeroed(canvas_len)?;

    let frame = dec
        .read_next_frame()
        .map_err(|_| MediaError::DecodeFailed)?
        .ok_or(MediaError::DecodeFailed)?;
    composite_frame(&mut canvas, screen_w, screen_h, frame);
    premultiply_rgba_in_place(&mut canvas);

    let out_rgba = if dst_w != screen_w || dst_h != screen_h {
        downscale_rgba_bilinear(&canvas, screen_w, screen_h, dst_w, dst_h)?
    } else {
        canvas
    };
    Ok(DecodedRgba {
        width: dst_w,
        height: dst_h,
        rgba: out_rgba,
    })
}

pub(crate) fn open_session(
    raw_bytes: Vec<u8>,
    req_w: u32,
    req_h: u32,
) -> Result<GifOpenInfo, MediaError> {
    if raw_bytes.is_empty() {
        return Err(MediaError::InvalidInput);
    }
    let shared: Arc<[u8]> = Arc::from(raw_bytes);

    let mut scanner = configure_options(true)
        .read_info(Cursor::new(Arc::clone(&shared)))
        .map_err(|_| MediaError::DecodeFailed)?;
    let screen_w = scanner.width();
    let screen_h = scanner.height();
    let (out_w, out_h) = compute_target_dimensions(
        u32::from(screen_w),
        u32::from(screen_h),
        req_w,
        req_h,
        MAX_GIF_DIMENSION,
        MAX_GIF_PIXELS,
    )?;

    let mut delays_ms = Vec::new();
    while let Some(frame) = scanner
        .read_next_frame()
        .map_err(|_| MediaError::DecodeFailed)?
    {
        if delays_ms.len() >= MAX_GIF_FRAMES {
            break;
        }
        delays_ms.push(normalize_delay_ms(frame.delay));
    }
    if delays_ms.is_empty() {
        return Err(MediaError::DecodeFailed);
    }

    let canvas_len = (usize::from(screen_w))
        .checked_mul(usize::from(screen_h))
        .and_then(|n| n.checked_mul(4))
        .ok_or(MediaError::TooLarge)?;
    let canvas = try_alloc_zeroed(canvas_len)?;
    let frame_count = delays_ms.len() as u32;

    let session = Arc::new(Mutex::new(GifSession {
        compositor: GifCompositor {
            raw_bytes: shared,
            delays_ms,
            screen_w,
            screen_h,
            out_w,
            out_h,
            canvas,
            prev_canvas: None,
            last_dispose: DisposalMethod::Any,
            last_rect: (0, 0, 0, 0),
            current_frame: None,
        },
        decoder: None,
    }));

    let mut guard = store().lock().map_err(|_| MediaError::DecodeFailed)?;
    while guard.sessions.len() >= MAX_GIF_SESSIONS {
        if let Some(oldest) = guard.order.pop_front() {
            guard.sessions.remove(&oldest);
        } else {
            break;
        }
    }
    let handle = guard.next_handle;
    guard.next_handle = guard.next_handle.saturating_add(1);
    guard.order.push_back(handle);
    guard.sessions.insert(handle, session);

    Ok(GifOpenInfo {
        handle,
        width: out_w,
        height: out_h,
        frame_count,
    })
}

pub(crate) fn render_frame(handle: i64, frame_index: usize) -> Result<GifRenderedFrame, MediaError> {
    let session = {
        let guard = store().lock().map_err(|_| MediaError::DecodeFailed)?;
        Arc::clone(guard.sessions.get(&handle).ok_or(MediaError::InvalidInput)?)
    };

    let mut sess = session.lock().map_err(|_| MediaError::DecodeFailed)?;
    let GifSession {
        compositor,
        decoder,
    } = &mut *sess;
    let count = compositor.delays_ms.len();
    if count == 0 {
        return Err(MediaError::InvalidInput);
    }
    let target = frame_index % count;

    let need_reset = match compositor.current_frame {
        Some(cur) if target > cur && decoder.is_some() => false,
        _ => true,
    };

    if need_reset {
        let dec = configure_options(false)
            .read_info(Cursor::new(Arc::clone(&compositor.raw_bytes)))
            .map_err(|_| MediaError::DecodeFailed)?;
        *decoder = Some(dec);
        compositor.canvas.fill(0);
        compositor.prev_canvas = None;
        compositor.last_dispose = DisposalMethod::Any;
        compositor.last_rect = (0, 0, 0, 0);
        compositor.current_frame = None;
    }

    let dec = decoder.as_mut().ok_or(MediaError::DecodeFailed)?;
    let start = compositor.current_frame.map_or(0, |c| c + 1);
    for idx in start..=target {
        let frame = dec
            .read_next_frame()
            .map_err(|_| MediaError::DecodeFailed)?
            .ok_or(MediaError::DecodeFailed)?;

        apply_previous_disposal(compositor);
        if frame.dispose == DisposalMethod::Previous {
            let mut snap = try_alloc_zeroed(compositor.canvas.len())?;
            snap.copy_from_slice(&compositor.canvas);
            compositor.prev_canvas = Some(snap);
        }
        composite_frame(
            &mut compositor.canvas,
            u32::from(compositor.screen_w),
            u32::from(compositor.screen_h),
            frame,
        );
        compositor.last_dispose = frame.dispose;
        compositor.last_rect = (frame.left, frame.top, frame.width, frame.height);
        compositor.current_frame = Some(idx);
    }

    let sw = u32::from(compositor.screen_w);
    let sh = u32::from(compositor.screen_h);
    let mut out = if compositor.out_w != sw || compositor.out_h != sh {
        downscale_rgba_bilinear(&compositor.canvas, sw, sh, compositor.out_w, compositor.out_h)?
    } else {
        let mut copy = try_alloc_zeroed(compositor.canvas.len())?;
        copy.copy_from_slice(&compositor.canvas);
        copy
    };
    premultiply_rgba_in_place(&mut out);

    Ok(GifRenderedFrame {
        duration_ms: compositor.delays_ms[target],
        rgba: out,
    })
}

pub(crate) fn close_session(handle: i64) {
    if let Ok(mut guard) = store().lock() {
        guard.sessions.remove(&handle);
        guard.order.retain(|&id| id != handle);
    }
}

pub(crate) fn close_all_sessions() {
    if let Ok(mut guard) = store().lock() {
        guard.sessions.clear();
        guard.order.clear();
    }
}

fn apply_previous_disposal(sess: &mut GifCompositor) {
    match sess.last_dispose {
        DisposalMethod::Background => {
            let (left, top, fw, fh) = sess.last_rect;
            let sw = usize::from(sess.screen_w);
            let sh = usize::from(sess.screen_h);
            let x_end = (usize::from(left) + usize::from(fw)).min(sw);
            let y_end = (usize::from(top) + usize::from(fh)).min(sh);
            for y in usize::from(top)..y_end {
                let row_start = (y * sw + usize::from(left)) * 4;
                let row_end = (y * sw + x_end) * 4;
                if row_end <= sess.canvas.len() {
                    sess.canvas[row_start..row_end].fill(0);
                }
            }
        }
        DisposalMethod::Previous => {
            if let Some(prev) = &sess.prev_canvas {
                if prev.len() == sess.canvas.len() {
                    sess.canvas.copy_from_slice(prev);
                }
            }
        }
        DisposalMethod::Any | DisposalMethod::Keep => {}
    }
}

fn composite_frame(canvas: &mut [u8], screen_w: u32, screen_h: u32, frame: &gif::Frame<'_>) {
    let fw = usize::from(frame.width);
    let fh = usize::from(frame.height);
    let left = usize::from(frame.left);
    let top = usize::from(frame.top);
    let sw = screen_w as usize;
    let sh = screen_h as usize;

    for fy in 0..fh {
        let cy = top + fy;
        if cy >= sh {
            break;
        }
        for fx in 0..fw {
            let cx = left + fx;
            if cx >= sw {
                break;
            }
            let si = (fy * fw + fx) * 4;
            let di = (cy * sw + cx) * 4;
            if si + 4 > frame.buffer.len() || di + 4 > canvas.len() {
                continue;
            }
            let alpha = frame.buffer[si + 3];
            if alpha != 0 {
                canvas[di..di + 4].copy_from_slice(&frame.buffer[si..si + 4]);
            }
        }
    }
}

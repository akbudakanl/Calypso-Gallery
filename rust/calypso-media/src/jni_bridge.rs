use core::ptr::null_mut;
use std::panic::{catch_unwind, AssertUnwindSafe};

use jni_sys::{
    jboolean, jbyte, jbyteArray, jclass, jint, jintArray, jlong, jobject, jsize, JNIEnv,
    JNINativeInterface__1_2, JNINativeInterface__1_4, JNI_FALSE, JNI_TRUE,
};

use crate::{gif_session, static_decoders, try_alloc_zeroed, MediaError, MAX_DECODE_ALLOC_BYTES};

unsafe fn jni_table<'a>(env: *mut JNIEnv) -> Option<&'a JNINativeInterface__1_2> {
    if env.is_null() {
        return None;
    }
    let table_ptr = *env;
    if table_ptr.is_null() {
        return None;
    }
    Some(&(*table_ptr).v1_2)
}

unsafe fn jni_table_1_4<'a>(env: *mut JNIEnv) -> Option<&'a JNINativeInterface__1_4> {
    if env.is_null() {
        return None;
    }
    let table_ptr = *env;
    if table_ptr.is_null() {
        return None;
    }
    Some(&(*table_ptr).v1_4)
}

unsafe fn exception_occurred(env: *mut JNIEnv, table: &JNINativeInterface__1_2) -> bool {
    (table.ExceptionCheck)(env)
}

unsafe fn read_jbyte_array(env: *mut JNIEnv, arr: jbyteArray) -> Result<Vec<u8>, MediaError> {
    let table = jni_table(env).ok_or(MediaError::InvalidInput)?;
    if arr.is_null() {
        return Err(MediaError::InvalidInput);
    }
    let len = (table.GetArrayLength)(env, arr);
    if exception_occurred(env, table) || len <= 0 || (len as usize) > MAX_DECODE_ALLOC_BYTES {
        return Err(MediaError::InvalidInput);
    }
    let usize_len = len as usize;
    let mut buf = try_alloc_zeroed(usize_len)?;
    (table.GetByteArrayRegion)(env, arr, 0, len, buf.as_mut_ptr() as *mut jbyte);
    if exception_occurred(env, table) {
        return Err(MediaError::InvalidInput);
    }
    Ok(buf)
}

unsafe fn new_jbyte_array(env: *mut JNIEnv, bytes: &[u8]) -> jbyteArray {
    let Some(table) = jni_table(env) else {
        return null_mut();
    };
    let Ok(len) = jsize::try_from(bytes.len()) else {
        return null_mut();
    };
    let arr = (table.NewByteArray)(env, len);
    if arr.is_null() || exception_occurred(env, table) {
        return null_mut();
    }
    if !bytes.is_empty() {
        (table.SetByteArrayRegion)(env, arr, 0, len, bytes.as_ptr() as *const jbyte);
        if exception_occurred(env, table) {
            return null_mut();
        }
    }
    arr
}

unsafe fn new_jint_array(env: *mut JNIEnv, values: &[jint]) -> jintArray {
    let Some(table) = jni_table(env) else {
        return null_mut();
    };
    let Ok(len) = jsize::try_from(values.len()) else {
        return null_mut();
    };
    let arr = (table.NewIntArray)(env, len);
    if arr.is_null() || exception_occurred(env, table) {
        return null_mut();
    }
    if !values.is_empty() {
        (table.SetIntArrayRegion)(env, arr, 0, len, values.as_ptr());
        if exception_occurred(env, table) {
            return null_mut();
        }
    }
    arr
}

unsafe fn write_jint_slice(env: *mut JNIEnv, arr: jintArray, values: &[jint]) -> bool {
    let Some(table) = jni_table(env) else {
        return false;
    };
    if arr.is_null() {
        return false;
    }
    let Ok(need_len) = jsize::try_from(values.len()) else {
        return false;
    };
    let actual_len = (table.GetArrayLength)(env, arr);
    if exception_occurred(env, table) || actual_len < need_len {
        return false;
    }
    (table.SetIntArrayRegion)(env, arr, 0, need_len, values.as_ptr());
    !exception_occurred(env, table)
}

#[no_mangle]
pub unsafe extern "system" fn Java_org_fossify_gallery_helpers_media_NativeMediaJni_probeBoundsNative(
    env: *mut JNIEnv,
    _class: jclass,
    format_code: jint,
    data: jbyteArray,
) -> jintArray {
    let result = catch_unwind(AssertUnwindSafe(|| {
        let bytes = read_jbyte_array(env, data).ok()?;
        let (w, h) = static_decoders::probe_bounds(format_code, &bytes).ok()?;
        let vals = [w as jint, h as jint];
        Some(new_jint_array(env, &vals))
    }));
    result.ok().flatten().unwrap_or_else(null_mut)
}

#[no_mangle]
pub unsafe extern "system" fn Java_org_fossify_gallery_helpers_media_NativeMediaJni_decodeStaticRgbaNative(
    env: *mut JNIEnv,
    _class: jclass,
    format_code: jint,
    data: jbyteArray,
    max_width: jint,
    max_height: jint,
    out_dims: jintArray,
) -> jbyteArray {
    let result = catch_unwind(AssertUnwindSafe(|| {
        let bytes = read_jbyte_array(env, data).ok()?;
        let req_w = u32::try_from(max_width).unwrap_or(0);
        let req_h = u32::try_from(max_height).unwrap_or(0);
        let decoded = static_decoders::decode_static(format_code, &bytes, req_w, req_h).ok()?;
        let dims = [decoded.width as jint, decoded.height as jint];
        if !write_jint_slice(env, out_dims, &dims) {
            return None;
        }
        Some(new_jbyte_array(env, &decoded.rgba))
    }));
    result.ok().flatten().unwrap_or_else(null_mut)
}

#[no_mangle]
pub unsafe extern "system" fn Java_org_fossify_gallery_helpers_media_NativeMediaJni_decodeStaticRgbaExNative(
    env: *mut JNIEnv,
    _class: jclass,
    format_code: jint,
    data: jbyteArray,
    max_width: jint,
    max_height: jint,
    exif_orientation: jint,
    scale_mode: jint,
    out_meta: jintArray,
) -> jbyteArray {
    let result = catch_unwind(AssertUnwindSafe(|| {
        let bytes = read_jbyte_array(env, data).ok()?;
        let req_w = u32::try_from(max_width).unwrap_or(0);
        let req_h = u32::try_from(max_height).unwrap_or(0);
        let decoded = static_decoders::decode_static_ex(
            format_code,
            &bytes,
            req_w,
            req_h,
            exif_orientation,
            scale_mode,
        )
        .ok()?;
        let meta = [
            decoded.width as jint,
            decoded.height as jint,
            decoded.src_width as jint,
            decoded.src_height as jint,
        ];
        if !write_jint_slice(env, out_meta, &meta) {
            return None;
        }
        Some(new_jbyte_array(env, &decoded.rgba))
    }));
    result.ok().flatten().unwrap_or_else(null_mut)
}

#[no_mangle]
pub unsafe extern "system" fn Java_org_fossify_gallery_helpers_media_NativeMediaJni_openGifSessionNative(
    env: *mut JNIEnv,
    _class: jclass,
    data: jbyteArray,
    max_width: jint,
    max_height: jint,
    out_info: jintArray,
) -> jlong {
    let result = catch_unwind(AssertUnwindSafe(|| -> Option<jlong> {
        let bytes = read_jbyte_array(env, data).ok()?;
        let req_w = u32::try_from(max_width).unwrap_or(0);
        let req_h = u32::try_from(max_height).unwrap_or(0);
        let info = gif_session::open_session(bytes, req_w, req_h).ok()?;
        let vals = [info.width as jint, info.height as jint, info.frame_count as jint];
        if !write_jint_slice(env, out_info, &vals) {
            gif_session::close_session(info.handle);
            return None;
        }
        Some(info.handle as jlong)
    }));
    result.ok().flatten().unwrap_or(0)
}

#[no_mangle]
pub unsafe extern "system" fn Java_org_fossify_gallery_helpers_media_NativeMediaJni_renderGifFrameNative(
    env: *mut JNIEnv,
    _class: jclass,
    handle: jlong,
    frame_index: jint,
    out_meta: jintArray,
) -> jbyteArray {
    let result = catch_unwind(AssertUnwindSafe(|| {
        if handle <= 0 || frame_index < 0 {
            return None;
        }
        let frame = gif_session::render_frame(handle as i64, frame_index as usize).ok()?;
        let meta = [frame.duration_ms as jint];
        if !write_jint_slice(env, out_meta, &meta) {
            return None;
        }
        Some(new_jbyte_array(env, &frame.rgba))
    }));
    result.ok().flatten().unwrap_or_else(null_mut)
}

#[no_mangle]
pub unsafe extern "system" fn Java_org_fossify_gallery_helpers_media_NativeMediaJni_renderGifFrameIntoBufferNative(
    env: *mut JNIEnv,
    _class: jclass,
    handle: jlong,
    frame_index: jint,
    direct_buf: jobject,
    out_meta: jintArray,
) -> jboolean {
    let result = catch_unwind(AssertUnwindSafe(|| -> Option<jboolean> {
        if handle <= 0 || frame_index < 0 || direct_buf.is_null() {
            return Some(JNI_FALSE);
        }
        let table = jni_table_1_4(env)?;
        let ptr = (table.GetDirectBufferAddress)(env, direct_buf) as *mut u8;
        let cap = (table.GetDirectBufferCapacity)(env, direct_buf);
        if ptr.is_null() || cap <= 0 {
            return Some(JNI_FALSE);
        }
        let len = usize::try_from(cap).ok()?.min(MAX_DECODE_ALLOC_BYTES);
        let dst = core::slice::from_raw_parts_mut(ptr, len);
        let duration_ms = gif_session::render_frame_into(handle as i64, frame_index as usize, dst).ok()?;
        let meta = [duration_ms as jint];
        if !write_jint_slice(env, out_meta, &meta) {
            return Some(JNI_FALSE);
        }
        Some(JNI_TRUE)
    }));
    result.ok().flatten().unwrap_or(JNI_FALSE)
}

#[no_mangle]
pub extern "system" fn Java_org_fossify_gallery_helpers_media_NativeMediaJni_closeGifSessionNative(
    _env: *mut JNIEnv,
    _class: jclass,
    handle: jlong,
) {
    let _ = catch_unwind(AssertUnwindSafe(|| {
        if handle > 0 {
            gif_session::close_session(handle as i64);
        }
    }));
}

#[no_mangle]
pub extern "system" fn Java_org_fossify_gallery_helpers_media_NativeMediaJni_closeAllGifSessionsNative(
    _env: *mut JNIEnv,
    _class: jclass,
) {
    let _ = catch_unwind(AssertUnwindSafe(gif_session::close_all_sessions));
}

use jni::JNIEnv;
use jni::objects::{JByteBuffer, JClass};
use jni::sys::{jint, jlong};

use super::{amethyst_pcm_output_queued_frames, amethyst_pcm_output_write_direct};

#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_anthonyhfm_amethyst_nativeengine_audio_PcmOutputDirectBridge_writeInterleaved(
    env: JNIEnv,
    _class: JClass,
    handle: jlong,
    samples: JByteBuffer,
    byte_offset: jint,
    sample_count: jint,
) -> jint {
    if handle == 0 || byte_offset < 0 || sample_count <= 0 {
        return 0;
    }
    let Ok(capacity) = env.get_direct_buffer_capacity(&samples) else {
        return 0;
    };
    let byte_offset = byte_offset as usize;
    let Some(byte_count) = (sample_count as usize).checked_mul(size_of::<f32>()) else {
        return 0;
    };
    let Some(end_offset) = byte_offset.checked_add(byte_count) else {
        return 0;
    };
    if end_offset > capacity || byte_offset % align_of::<f32>() != 0 {
        return 0;
    }
    let Ok(address) = env.get_direct_buffer_address(&samples) else {
        return 0;
    };
    let address = unsafe { address.add(byte_offset) }.cast::<f32>();
    if address as usize % align_of::<f32>() != 0 {
        return 0;
    }
    unsafe {
        amethyst_pcm_output_write_direct(handle as u64, address, sample_count as u32) as jint
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_anthonyhfm_amethyst_nativeengine_audio_PcmOutputDirectBridge_queuedFrames(
    _env: JNIEnv,
    _class: JClass,
    handle: jlong,
) -> jlong {
    unsafe { amethyst_pcm_output_queued_frames(handle as u64) as jlong }
}

unsafe extern "C" {
    fn madvise(address: *mut core::ffi::c_void, length: usize, advice: core::ffi::c_int) -> core::ffi::c_int;
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_anthonyhfm_amethyst_nativeengine_audio_PcmOutputDirectBridge_releaseMappedPages(
    env: JNIEnv,
    _class: JClass,
    samples: JByteBuffer,
) -> jint {
    let Ok(capacity) = env.get_direct_buffer_capacity(&samples) else {
        return -1;
    };
    let Ok(address) = env.get_direct_buffer_address(&samples) else {
        return -1;
    };
    unsafe { madvise(address.cast(), capacity, 4) }
}

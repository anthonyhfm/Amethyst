use std::ffi::c_void;
use std::sync::OnceLock;

use jni::objects::{GlobalRef, JClass, JObject};
use jni::sys::{JNI_FALSE, JNI_TRUE, jboolean};
use jni::{JNIEnv, JavaVM};

struct AndroidContextOwner {
    _java_vm: JavaVM,
    _application_context: GlobalRef,
}

static ANDROID_CONTEXT_OWNER: OnceLock<AndroidContextOwner> = OnceLock::new();

/// Initializes the process-wide context required by CPAL's Android backend.
///
/// `ndk-context` only stores raw JNI pointers, so this module retains a global
/// reference to the application context for the lifetime of the process.
#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_anthonyhfm_amethyst_nativeengine_AndroidNativeContext_initializeAndroidContext(
    env: JNIEnv,
    _class: JClass,
    application_context: JObject,
) -> jboolean {
    if ANDROID_CONTEXT_OWNER.get().is_some() {
        return JNI_TRUE;
    }

    let java_vm = match env.get_java_vm() {
        Ok(java_vm) => java_vm,
        Err(_) => return JNI_FALSE,
    };
    let global_context = match env.new_global_ref(application_context) {
        Ok(global_context) => global_context,
        Err(_) => return JNI_FALSE,
    };
    let java_vm_pointer = java_vm.get_java_vm_pointer().cast::<c_void>();
    let context_pointer = global_context.as_obj().as_raw().cast::<c_void>();

    if ANDROID_CONTEXT_OWNER
        .set(AndroidContextOwner {
            _java_vm: java_vm,
            _application_context: global_context,
        })
        .is_err()
    {
        return JNI_TRUE;
    }

    // SAFETY: both pointers originate from the current JNI environment. The
    // JavaVM and global application-context reference are retained above for
    // the process lifetime, and OnceLock guarantees exactly one initialization.
    unsafe {
        ndk_context::initialize_android_context(java_vm_pointer, context_pointer);
    }
    JNI_TRUE
}

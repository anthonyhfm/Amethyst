
pub fn get_active_drag_file() -> Option<String> {
    #[cfg(target_os = "macos")]
    {
        macos::get_active_drag_file()
    }

    #[cfg(target_os = "windows")]
    {
        windows_impl::get_active_drag_file()
    }

    #[cfg(target_os = "linux")]
    {
        linux_impl::get_active_drag_file()
    }

    #[cfg(not(any(target_os = "macos", target_os = "windows", target_os = "linux")))]
    {
        None
    }
}

#[cfg(target_os = "macos")]
mod macos {
    use std::ffi::{c_char, c_void, CStr};

    #[link(name = "objc", kind = "dylib")]
    unsafe extern "C" {
        fn objc_getClass(name: *const c_char) -> *mut c_void;
        fn sel_registerName(name: *const c_char) -> *mut c_void;
        fn objc_msgSend();
    }

    #[link(name = "AppKit", kind = "framework")]
    unsafe extern "C" {
        static NSPasteboardNameDrag: *const c_void;
        static NSFilenamesPboardType: *const c_void;
    }

    pub fn get_active_drag_file() -> Option<String> {
        unsafe {
            let cls_nspasteboard = objc_getClass(b"NSPasteboard\0".as_ptr() as *const c_char);
            if cls_nspasteboard.is_null() {
                return None;
            }

            let sel_pasteboard_with_name = sel_registerName(b"pasteboardWithName:\0".as_ptr() as *const c_char);
            let sel_property_list_for_type = sel_registerName(b"propertyListForType:\0".as_ptr() as *const c_char);
            let sel_count = sel_registerName(b"count\0".as_ptr() as *const c_char);
            let sel_object_at_index = sel_registerName(b"objectAtIndex:\0".as_ptr() as *const c_char);
            let sel_utf8_string = sel_registerName(b"UTF8String\0".as_ptr() as *const c_char);

            // [NSPasteboard pasteboardWithName:NSPasteboardNameDrag]
            let msg_send_pb: unsafe extern "C" fn(*mut c_void, *mut c_void, *const c_void) -> *mut c_void =
                std::mem::transmute(objc_msgSend as *const ());
            let pboard = msg_send_pb(cls_nspasteboard, sel_pasteboard_with_name, NSPasteboardNameDrag);
            if pboard.is_null() {
                return None;
            }

            // [pboard propertyListForType:NSFilenamesPboardType]
            let plist = msg_send_pb(pboard, sel_property_list_for_type, NSFilenamesPboardType);
            if plist.is_null() {
                return None;
            }

            // [plist count]
            let msg_send_count: unsafe extern "C" fn(*mut c_void, *mut c_void) -> usize =
                std::mem::transmute(objc_msgSend as *const ());
            let count = msg_send_count(plist, sel_count);
            if count == 0 {
                return None;
            }

            // [plist objectAtIndex:0]
            let msg_send_at: unsafe extern "C" fn(*mut c_void, *mut c_void, usize) -> *mut c_void =
                std::mem::transmute(objc_msgSend as *const ());
            let item = msg_send_at(plist, sel_object_at_index, 0);
            if item.is_null() {
                return None;
            }

            // [item UTF8String]
            let msg_send_utf8: unsafe extern "C" fn(*mut c_void, *mut c_void) -> *const c_char =
                std::mem::transmute(objc_msgSend as *const ());
            let utf8_ptr = msg_send_utf8(item, sel_utf8_string);
            if utf8_ptr.is_null() {
                return None;
            }

            let c_str = CStr::from_ptr(utf8_ptr);
            let path = c_str.to_string_lossy().to_string();
            if path.is_empty() {
                None
            } else {
                Some(path)
            }
        }
    }
}

#[cfg(target_os = "windows")]
mod windows_impl {
    use std::ffi::c_void;

    const CF_HDROP: u32 = 15;

    #[link(name = "user32")]
    unsafe extern "system" {
        fn OpenClipboard(hWndNewOwner: *mut c_void) -> i32;
        fn CloseClipboard() -> i32;
        fn GetClipboardData(uFormat: u32) -> *mut c_void;
    }

    #[link(name = "shell32")]
    unsafe extern "system" {
        fn DragQueryFileW(hDrop: *mut c_void, iFile: u32, lpszFile: *mut u16, cch: u32) -> u32;
    }

    pub fn get_active_drag_file() -> Option<String> {
        unsafe {
            if OpenClipboard(std::ptr::null_mut()) == 0 {
                return None;
            }
            let hdrop = GetClipboardData(CF_HDROP);
            if hdrop.is_null() {
                CloseClipboard();
                return None;
            }

            let len = DragQueryFileW(hdrop, 0, std::ptr::null_mut(), 0);
            if len == 0 {
                CloseClipboard();
                return None;
            }

            let mut buffer = vec![0u16; (len + 1) as usize];
            let copied = DragQueryFileW(hdrop, 0, buffer.as_mut_ptr(), len + 1);
            CloseClipboard();

            if copied > 0 {
                String::from_utf16(&buffer[..copied as usize]).ok()
            } else {
                None
            }
        }
    }
}

#[cfg(target_os = "linux")]
mod linux_impl {
    pub fn get_active_drag_file() -> Option<String> {
        // Safe fallback under Linux X11/Wayland
        None
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_drag_file() {
        let f = get_active_drag_file();
        println!("TEST DRAG FILE: {:?}", f);
    }
}

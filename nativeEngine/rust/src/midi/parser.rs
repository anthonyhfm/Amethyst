pub fn split_midi_messages(data: &[u8]) -> Vec<Vec<u8>> {
    let mut messages = Vec::new();
    let mut i = 0;
    while i < data.len() {
        let status = data[i];
        if status < 0x80 {
            i += 1;
            continue;
        }

        let len = match status & 0xF0 {
            0x80 | 0x90 | 0xA0 | 0xB0 | 0xE0 => 3,
            0xC0 | 0xD0 => 2,
            0xF0 => {
                if status == 0xF0 {
                    let mut sys_len = 1;
                    while i + sys_len < data.len() && data[i + sys_len] != 0xF7 {
                        sys_len += 1;
                    }
                    if i + sys_len < data.len() {
                        sys_len += 1;
                    }
                    sys_len
                } else if status == 0xF2 {
                    3
                } else if status == 0xF1 || status == 0xF3 {
                    2
                } else {
                    1
                }
            }
            _ => 1,
        };

        let end = std::cmp::min(i + len, data.len());
        messages.push(data[i..end].to_vec());
        i += len;
    }
    messages
}

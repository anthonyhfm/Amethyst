const MAX_SYSEX_SIZE: usize = 1024 * 1024;

/// Incrementally turns a MIDI 1.0 byte stream into complete messages.
///
/// Backend callback boundaries are not MIDI message boundaries: CoreMIDI may
/// split SysEx across packets, while raw byte producers may combine messages or
/// use running status. System real-time bytes are emitted immediately without
/// disturbing the message currently being assembled.
#[derive(Debug, Default)]
pub struct MidiStreamParser {
    running_status: Option<u8>,
    pending: Vec<u8>,
    pending_data_len: usize,
    pending_timestamp_us: Option<u64>,
    sysex: Option<Vec<u8>>,
    sysex_timestamp_us: Option<u64>,
    discard_sysex: bool,
    malformed: bool,
}

impl MidiStreamParser {
    pub fn new() -> Self {
        Self::default()
    }

    pub fn push(&mut self, data: &[u8]) -> Vec<Vec<u8>> {
        self.push_with_timestamp(data, 0)
            .into_iter()
            .map(|(message, _)| message)
            .collect()
    }

    /// Parses a callback buffer while retaining the first-byte timestamp when
    /// one logical message is fragmented across multiple callbacks.
    pub(crate) fn push_with_timestamp(
        &mut self,
        data: &[u8],
        timestamp_us: u64,
    ) -> Vec<(Vec<u8>, u64)> {
        let mut messages = Vec::new();
        for &byte in data {
            self.push_byte(byte, timestamp_us, &mut messages);
        }
        messages
    }

    pub fn reset(&mut self) {
        *self = Self::default();
    }

    /// Whether all bytes seen so far form complete, valid MIDI messages.
    pub(crate) fn is_complete_and_valid(&self) -> bool {
        !self.malformed && self.pending.is_empty() && self.sysex.is_none() && !self.discard_sysex
    }

    fn push_byte(&mut self, byte: u8, timestamp_us: u64, messages: &mut Vec<(Vec<u8>, u64)>) {
        // Real-time bytes do not affect the message being assembled.
        if byte >= 0xF8 {
            messages.push((vec![byte], timestamp_us));
            return;
        }

        if self.discard_sysex {
            if byte == 0xF7 {
                self.discard_sysex = false;
            } else if byte & 0x80 != 0 {
                self.discard_sysex = false;
                self.handle_status(byte, timestamp_us, messages);
            }
            return;
        }

        if let Some(sysex) = self.sysex.as_mut() {
            if byte == 0xF7 {
                sysex.push(byte);
                messages.push((
                    self.sysex.take().expect("SysEx exists"),
                    self.sysex_timestamp_us.take().unwrap_or(timestamp_us),
                ));
            } else if byte & 0x80 != 0 {
                // A non-real-time status aborts an unterminated SysEx.
                self.malformed = true;
                self.sysex = None;
                self.sysex_timestamp_us = None;
                self.handle_status(byte, timestamp_us, messages);
            } else if sysex.len() < MAX_SYSEX_SIZE {
                sysex.push(byte);
            } else {
                self.malformed = true;
                self.sysex = None;
                self.sysex_timestamp_us = None;
                self.discard_sysex = true;
            }
            return;
        }

        if byte & 0x80 != 0 {
            self.handle_status(byte, timestamp_us, messages);
        } else {
            self.handle_data(byte, timestamp_us, messages);
        }
    }

    fn handle_status(&mut self, status: u8, timestamp_us: u64, messages: &mut Vec<(Vec<u8>, u64)>) {
        if !self.pending.is_empty() {
            self.malformed = true;
        }
        self.pending.clear();
        self.pending_data_len = 0;
        self.pending_timestamp_us = None;

        match status {
            0x80..=0xEF => {
                self.running_status = Some(status);
                self.start_pending(status, timestamp_us);
            }
            0xF0 => {
                self.running_status = None;
                self.sysex = Some(vec![status]);
                self.sysex_timestamp_us = Some(timestamp_us);
            }
            0xF1 | 0xF3 => {
                self.running_status = None;
                self.pending.push(status);
                self.pending_data_len = 1;
                self.pending_timestamp_us = Some(timestamp_us);
            }
            0xF2 => {
                self.running_status = None;
                self.pending.push(status);
                self.pending_data_len = 2;
                self.pending_timestamp_us = Some(timestamp_us);
            }
            0xF4..=0xF7 => {
                self.running_status = None;
                messages.push((vec![status], timestamp_us));
            }
            _ => unreachable!("real-time statuses are handled before this method"),
        }
    }

    fn handle_data(&mut self, data: u8, timestamp_us: u64, messages: &mut Vec<(Vec<u8>, u64)>) {
        if self.pending.is_empty() {
            let Some(status) = self.running_status else {
                self.malformed = true;
                return;
            };
            self.start_pending(status, timestamp_us);
        }

        self.pending.push(data);
        if self.pending.len() == self.pending_data_len + 1 {
            messages.push((
                std::mem::take(&mut self.pending),
                self.pending_timestamp_us.take().unwrap_or(timestamp_us),
            ));
            self.pending_data_len = 0;
        }
    }

    fn start_pending(&mut self, status: u8, timestamp_us: u64) {
        self.pending.clear();
        self.pending.push(status);
        self.pending_timestamp_us = Some(timestamp_us);
        self.pending_data_len = match status & 0xF0 {
            0xC0 | 0xD0 => 1,
            _ => 2,
        };
    }
}

/// Splits a self-contained buffer into complete MIDI messages.
///
/// Use [`MidiStreamParser`] directly when messages may span buffers.
pub fn split_midi_messages(data: &[u8]) -> Vec<Vec<u8>> {
    MidiStreamParser::new().push(data)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn splits_multiple_messages_in_one_buffer() {
        assert_eq!(
            split_midi_messages(&[0x90, 60, 127, 0x80, 60, 0]),
            vec![vec![0x90, 60, 127], vec![0x80, 60, 0]],
        );
    }

    #[test]
    fn expands_running_status_into_independent_messages() {
        let mut parser = MidiStreamParser::new();
        assert_eq!(
            parser.push(&[0x90, 60, 100, 61, 101, 62]),
            vec![vec![0x90, 60, 100], vec![0x90, 61, 101]],
        );
        assert_eq!(parser.push(&[102]), vec![vec![0x90, 62, 102]]);
    }

    #[test]
    fn preserves_fragmented_messages_across_buffers() {
        let mut parser = MidiStreamParser::new();
        assert!(parser.push(&[0xB3, 7]).is_empty());
        assert_eq!(parser.push(&[99]), vec![vec![0xB3, 7, 99]]);
    }

    #[test]
    fn fragmented_message_keeps_its_first_buffer_timestamp() {
        let mut parser = MidiStreamParser::new();
        assert!(parser.push_with_timestamp(&[0xB3, 7], 100).is_empty());
        assert_eq!(
            parser.push_with_timestamp(&[99, 0xF8], 200),
            vec![(vec![0xB3, 7, 99], 100), (vec![0xF8], 200)],
        );
    }

    #[test]
    fn preserves_fragmented_sysex_across_buffers() {
        let mut parser = MidiStreamParser::new();
        assert!(parser.push(&[0xF0, 0x00, 0x20, 0x29]).is_empty());
        assert_eq!(
            parser.push(&[0x02, 0x0E, 0xF7]),
            vec![vec![0xF0, 0x00, 0x20, 0x29, 0x02, 0x0E, 0xF7]],
        );
    }

    #[test]
    fn realtime_does_not_disturb_channel_message_or_running_status() {
        let mut parser = MidiStreamParser::new();
        assert_eq!(
            parser.push(&[0x90, 60, 0xF8, 100, 61, 0xFE, 101]),
            vec![
                vec![0xF8],
                vec![0x90, 60, 100],
                vec![0xFE],
                vec![0x90, 61, 101],
            ],
        );
    }

    #[test]
    fn realtime_inside_sysex_is_emitted_separately() {
        let mut parser = MidiStreamParser::new();
        assert_eq!(
            parser.push(&[0xF0, 0x7E, 0xF8, 0x7F, 0xF7]),
            vec![vec![0xF8], vec![0xF0, 0x7E, 0x7F, 0xF7]],
        );
    }

    #[test]
    fn system_common_cancels_running_status() {
        let mut parser = MidiStreamParser::new();
        assert_eq!(
            parser.push(&[0x90, 60, 100, 0xF1, 4, 61, 101]),
            vec![vec![0x90, 60, 100], vec![0xF1, 4]],
        );
    }

    #[test]
    fn incomplete_messages_are_not_emitted() {
        assert!(split_midi_messages(&[0x90, 60]).is_empty());
        assert!(split_midi_messages(&[0xF0, 0x7E]).is_empty());
    }

    #[test]
    fn output_is_invariant_under_every_buffer_partition() {
        let stream = [
            0x90, 60, 0xF8, 100, 61, 101, 0xF0, 1, 0xF9, 2, 0xF7, 0xF1, 7,
        ];
        let expected = MidiStreamParser::new().push(&stream);

        // Each bit chooses whether a callback boundary exists after that byte.
        for boundaries in 0usize..(1usize << (stream.len() - 1)) {
            let mut parser = MidiStreamParser::new();
            let mut actual = Vec::new();
            let mut start = 0;
            for index in 0..stream.len() - 1 {
                if boundaries & (1 << index) != 0 {
                    actual.extend(parser.push(&stream[start..=index]));
                    start = index + 1;
                }
            }
            actual.extend(parser.push(&stream[start..]));
            assert_eq!(actual, expected, "partition mask {boundaries:#x}");
        }
    }

    #[test]
    fn non_realtime_status_aborts_unterminated_sysex_and_recovers() {
        let mut parser = MidiStreamParser::new();
        assert_eq!(
            parser.push(&[0xF0, 1, 2, 0x90, 60, 100]),
            vec![vec![0x90, 60, 100]],
        );
    }

    #[test]
    fn oversized_sysex_is_discarded_without_poisoning_later_messages() {
        let mut parser = MidiStreamParser::new();
        assert!(parser.push(&[0xF0]).is_empty());
        assert!(parser.push(&vec![1; MAX_SYSEX_SIZE]).is_empty());
        assert!(parser.push(&[0xF7]).is_empty());
        assert_eq!(parser.push(&[0x90, 60, 100]), vec![vec![0x90, 60, 100]]);
        assert!(!parser.is_complete_and_valid());
    }

    #[test]
    fn validation_detects_stray_and_incomplete_bytes() {
        let mut valid = MidiStreamParser::new();
        valid.push(&[0x90, 60, 100, 61, 101, 0xF8]);
        assert!(valid.is_complete_and_valid());

        let mut stray = MidiStreamParser::new();
        stray.push(&[60, 100]);
        assert!(!stray.is_complete_and_valid());

        let mut incomplete = MidiStreamParser::new();
        incomplete.push(&[0x90, 60]);
        assert!(!incomplete.is_complete_and_valid());
    }
}

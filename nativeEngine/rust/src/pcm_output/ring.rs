use std::cell::UnsafeCell;
use std::sync::atomic::{AtomicU64, AtomicUsize, Ordering};

#[repr(align(64))]
struct CachePadded<T>(T);

/// A preallocated single-producer/single-consumer ring of interleaved PCM samples.
///
/// The producer and consumer may run concurrently without locks. Calling `write`
/// from more than one thread, or any of the read methods from more than one thread,
/// violates the SPSC contract.
pub(crate) struct SpscFloatRing {
    samples: Box<[UnsafeCell<f32>]>,
    capacity: usize,
    channels: usize,
    read_position: CachePadded<AtomicUsize>,
    write_position: CachePadded<AtomicUsize>,
    written_frames: AtomicU64,
}

// Access to individual slots is synchronized by the acquire/release publication
// of read_position and write_position. The SPSC contract prevents concurrent
// access to the same slot.
unsafe impl Send for SpscFloatRing {}
unsafe impl Sync for SpscFloatRing {}

impl SpscFloatRing {
    pub(crate) fn new(capacity_frames: usize, channels: usize) -> Self {
        assert!(capacity_frames > 0, "ring capacity must be non-zero");
        assert!(channels > 0, "channel count must be non-zero");
        let capacity = capacity_frames
            .checked_mul(channels)
            .expect("ring sample capacity overflow");
        let samples = (0..capacity)
            .map(|_| UnsafeCell::new(0.0))
            .collect::<Vec<_>>()
            .into_boxed_slice();
        Self {
            samples,
            capacity,
            channels,
            read_position: CachePadded(AtomicUsize::new(0)),
            write_position: CachePadded(AtomicUsize::new(0)),
            written_frames: AtomicU64::new(0),
        }
    }

    pub(crate) fn channels(&self) -> usize {
        self.channels
    }

    pub(crate) fn available_read_frames(&self) -> usize {
        self.available_read_samples() / self.channels
    }

    pub(crate) fn available_write_frames(&self) -> usize {
        (self.capacity - self.available_read_samples()) / self.channels
    }

    pub(crate) fn written_frames(&self) -> u64 {
        self.written_frames.load(Ordering::Relaxed)
    }

    pub(crate) fn write_interleaved(&self, input: &[f32]) -> usize {
        let frame_aligned_input = input.len() - input.len() % self.channels;
        let writable_samples = self.available_write_samples().min(frame_aligned_input);
        let writable_samples = writable_samples - writable_samples % self.channels;
        if writable_samples == 0 {
            return 0;
        }

        let write_position = self.write_position.0.load(Ordering::Relaxed);
        for (index, sample) in input[..writable_samples].iter().copied().enumerate() {
            let slot = (write_position.wrapping_add(index)) % self.capacity;
            // SAFETY: only the producer writes slots that the consumer has
            // published as free.
            unsafe { *self.samples[slot].get() = sample };
        }
        self.write_position.0.store(
            write_position.wrapping_add(writable_samples),
            Ordering::Release,
        );
        self.written_frames
            .fetch_add((writable_samples / self.channels) as u64, Ordering::Relaxed);
        writable_samples
    }

    pub(crate) fn read_f32(&self, output: &mut [f32]) -> usize {
        self.read_mapped(output.len(), |index, sample| output[index] = sample)
    }

    pub(crate) fn read_i16(&self, output: &mut [i16]) -> usize {
        self.read_mapped(output.len(), |index, sample| {
            output[index] = float_to_i16(sample);
        })
    }

    pub(crate) fn read_u16(&self, output: &mut [u16]) -> usize {
        self.read_mapped(output.len(), |index, sample| {
            output[index] = float_to_u16(sample);
        })
    }

    fn available_read_samples(&self) -> usize {
        let read_position = self.read_position.0.load(Ordering::Relaxed);
        let write_position = self.write_position.0.load(Ordering::Acquire);
        write_position
            .wrapping_sub(read_position)
            .min(self.capacity)
    }

    fn available_write_samples(&self) -> usize {
        let write_position = self.write_position.0.load(Ordering::Relaxed);
        let read_position = self.read_position.0.load(Ordering::Acquire);
        let used_samples = write_position
            .wrapping_sub(read_position)
            .min(self.capacity);
        self.capacity - used_samples
    }

    fn read_mapped(&self, requested_samples: usize, mut write: impl FnMut(usize, f32)) -> usize {
        let frame_aligned_request = requested_samples - requested_samples % self.channels;
        let readable_samples = self.available_read_samples().min(frame_aligned_request);
        let readable_samples = readable_samples - readable_samples % self.channels;
        if readable_samples == 0 {
            return 0;
        }

        let read_position = self.read_position.0.load(Ordering::Relaxed);
        for index in 0..readable_samples {
            let slot = (read_position.wrapping_add(index)) % self.capacity;
            // SAFETY: only the consumer reads slots that the producer has
            // published as initialized.
            let sample = unsafe { *self.samples[slot].get() };
            write(index, sample);
        }
        self.read_position.0.store(
            read_position.wrapping_add(readable_samples),
            Ordering::Release,
        );
        readable_samples
    }
}

pub(crate) fn float_to_i16(sample: f32) -> i16 {
    let sample = sample.clamp(-1.0, 1.0);
    if sample <= -1.0 {
        i16::MIN
    } else {
        (sample * i16::MAX as f32).round() as i16
    }
}

pub(crate) fn float_to_u16(sample: f32) -> u16 {
    let normalized = sample.clamp(-1.0, 1.0) * 0.5 + 0.5;
    (normalized * u16::MAX as f32).round() as u16
}

#[cfg(test)]
mod tests {
    use super::SpscFloatRing;
    use std::sync::Arc;

    #[test]
    fn preserves_interleaved_frames_across_wraparound() {
        let ring = SpscFloatRing::new(3, 2);
        assert_eq!(ring.write_interleaved(&[0.0, 1.0, 2.0, 3.0]), 4);

        let mut first = [0.0; 2];
        assert_eq!(ring.read_f32(&mut first), 2);
        assert_eq!(first, [0.0, 1.0]);

        assert_eq!(ring.write_interleaved(&[4.0, 5.0, 6.0, 7.0]), 4);
        let mut second = [0.0; 6];
        assert_eq!(ring.read_f32(&mut second), 6);
        assert_eq!(second, [2.0, 3.0, 4.0, 5.0, 6.0, 7.0]);
    }

    #[test]
    fn never_splits_an_interleaved_frame() {
        let ring = SpscFloatRing::new(2, 2);
        assert_eq!(ring.write_interleaved(&[1.0, 2.0, 3.0]), 2);
        assert_eq!(ring.available_read_frames(), 1);

        let mut output = [9.0; 3];
        assert_eq!(ring.read_f32(&mut output), 2);
        assert_eq!(output, [1.0, 2.0, 9.0]);
    }

    #[test]
    fn converts_signed_and_unsigned_hardware_samples() {
        let signed = SpscFloatRing::new(5, 1);
        signed.write_interleaved(&[-1.5, -1.0, 0.0, 1.0, 1.5]);
        let mut i16_output = [0; 5];
        signed.read_i16(&mut i16_output);
        assert_eq!(i16_output, [i16::MIN, i16::MIN, 0, i16::MAX, i16::MAX]);

        let unsigned = SpscFloatRing::new(3, 1);
        unsigned.write_interleaved(&[-1.0, 0.0, 1.0]);
        let mut u16_output = [0; 3];
        unsigned.read_u16(&mut u16_output);
        assert_eq!(u16_output, [0, 32768, u16::MAX]);
    }

    #[test]
    fn survives_concurrent_producer_consumer_wraparound() {
        const FRAMES: usize = 100_000;
        let ring = Arc::new(SpscFloatRing::new(31, 1));
        let producer_ring = Arc::clone(&ring);
        let producer = std::thread::spawn(move || {
            let mut next = 0;
            while next < FRAMES {
                let written = producer_ring.write_interleaved(&[next as f32]);
                if written == 1 {
                    next += 1;
                } else {
                    std::hint::spin_loop();
                }
            }
        });

        let mut expected = 0;
        let mut output = [0.0; 1];
        while expected < FRAMES {
            if ring.read_f32(&mut output) == 1 {
                assert_eq!(output[0], expected as f32);
                expected += 1;
            } else {
                std::hint::spin_loop();
            }
        }
        producer.join().expect("producer thread");
    }
}

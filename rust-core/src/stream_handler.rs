use tokio::sync::mpsc;
use std::sync::Mutex;
use lazy_static::lazy_static;
use std::collections::HashMap;

lazy_static! {
    static ref STREAMS: Mutex<HashMap<u64, mpsc::Receiver<String>>> = Mutex::new(HashMap::new());
    static ref NEXT_STREAM_ID: Mutex<u64> = Mutex::new(1);
}

pub struct StreamHandler;

impl StreamHandler {
    /// Registers a new receiver and returns a unique stream identifier
    pub fn register_receiver(rx: mpsc::Receiver<String>) -> u64 {
        let mut id_lock = NEXT_STREAM_ID.lock().unwrap();
        let stream_id = *id_lock;
        *id_lock += 1;

        let mut streams_lock = STREAMS.lock().unwrap();
        streams_lock.insert(stream_id, rx);
        stream_id
    }

    /// Pulls all available logs from the stream queue without blocking.
    /// Returns None if the stream has finished and has been closed.
    pub fn pull_stream_output(stream_id: u64) -> Option<String> {
        let mut streams_lock = STREAMS.lock().unwrap();
        let rx = streams_lock.get_mut(&stream_id)?;

        let mut combined = String::new();
        // Try reading all currently queued outputs in non-blocking fashion
        while let Ok(msg) = rx.try_recv() {
            combined.push_str(&msg);
        }

        // If the channel is disconnected and empty, clean it up
        if rx.is_closed() && combined.is_empty() {
            streams_lock.remove(&stream_id);
            None
        } else {
            Some(combined)
        }
    }
}

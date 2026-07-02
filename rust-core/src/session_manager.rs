use std::sync::Mutex;
use lazy_static::lazy_static;
use std::collections::HashMap;

lazy_static! {
    static ref SESSIONS: Mutex<HashMap<u64, AdbSession>> = Mutex::new(HashMap::new());
    static ref NEXT_SESSION_ID: Mutex<u64> = Mutex::new(1);
}

pub struct AdbSession {
    pub session_id: u64,
    pub host: String,
    pub port: u16,
    pub secure: bool,
    pub active: bool,
}

pub struct SessionManager;

impl SessionManager {
    pub fn create_session(host: String, port: u16, secure: bool) -> u64 {
        let mut id_lock = NEXT_SESSION_ID.lock().unwrap();
        let session_id = *id_lock;
        *id_lock += 1;

        let mut sessions_lock = SESSIONS.lock().unwrap();
        sessions_lock.insert(
            session_id,
            AdbSession {
                session_id,
                host,
                port,
                secure,
                active: true,
            },
        );
        session_id
    }

    pub fn destroy_session(session_id: u64) {
        let mut sessions_lock = SESSIONS.lock().unwrap();
        if let Some(mut session) = sessions_lock.remove(&session_id) {
            session.active = false;
        }
    }

    pub fn is_session_active(session_id: u64) -> bool {
        let sessions_lock = SESSIONS.lock().unwrap();
        sessions_lock.get(&session_id).map_or(false, |s| s.active)
    }

    pub fn get_session_info(session_id: u64) -> Option<(String, u16)> {
        let sessions_lock = SESSIONS.lock().unwrap();
        let s = sessions_lock.get(&session_id)?;
        Some((s.host.clone(), s.port))
    }
}

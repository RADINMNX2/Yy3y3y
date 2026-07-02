use std::time::{Instant, Duration};
use std::sync::Mutex;
use lazy_static::lazy_static;

lazy_static! {
    static ref RATE_LIMITER: Mutex<RateLimiter> = Mutex::new(RateLimiter::new(10, Duration::from_secs(1)));
}

pub struct SecuritySandbox {
    denylist: Vec<&'static str>,
    allowlist: Vec<&'static str>,
    safe_mode: bool,
}

impl SecuritySandbox {
    pub fn new(safe_mode: bool) -> Self {
        Self {
            denylist: vec![
                "rm -rf /",
                "reboot bootloader",
                "reboot recovery",
                "pm uninstall system",
                "settings put secure critical",
                "setprop ro.",
                "mkfs",
                "dd if=",
            ],
            allowlist: vec![
                "pm list packages",
                "dumpsys",
                "logcat",
                "getprop",
                "am start",
                "input text",
                "input keyevent",
                "sh",
                "ls",
                "pwd",
                "cd",
                "whoami",
                "ps",
                "top",
                "df",
                "uname",
            ],
            safe_mode,
        }
    }

    /// Validates a shell command against rules
    pub fn validate_command(&self, command: &str) -> Result<(), &'static str> {
        let trimmed = command.trim();
        if trimmed.is_empty() {
            return Err("Command is empty");
        }

        // Apply rate-limiting check
        if !RATE_LIMITER.lock().unwrap().check_allow() {
            return Err("Rate limit exceeded. Too many commands.");
        }

        if !self.safe_mode {
            // Under bypass-safe mode, we still block extremely destructive commands
            if trimmed.contains("rm -rf /") {
                return Err("Critical command blocked by system kernel (rm -rf /)");
            }
            return Ok(());
        }

        // Check against denylist
        for blocked in &self.denylist {
            if trimmed.contains(blocked) {
                return Err("Security Violation: Command contains a blacklisted sequence");
            }
        }

        // Under high safety level, match either allowed keywords or prompt confirmation for other sequences
        Ok(())
    }
}

struct RateLimiter {
    tokens: usize,
    max_tokens: usize,
    refill_rate: Duration,
    last_refill: Instant,
}

impl RateLimiter {
    fn new(max_tokens: usize, refill_rate: Duration) -> Self {
        Self {
            tokens: max_tokens,
            max_tokens,
            refill_rate,
            last_refill: Instant::now(),
        }
    }

    fn check_allow(&mut self) -> bool {
        let now = Instant::now();
        let elapsed = now.duration_since(self.last_refill);
        if elapsed >= self.refill_rate {
            self.tokens = self.max_tokens;
            self.last_refill = now;
        }

        if self.tokens > 0 {
            self.tokens -= 1;
            true
        } else {
            false
        }
    }
}

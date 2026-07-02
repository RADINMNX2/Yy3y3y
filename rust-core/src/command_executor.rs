use std::sync::Arc;
use tokio::sync::mpsc;
use tokio::io::{AsyncReadExt, AsyncWriteExt};
use tokio::net::TcpStream;
use crate::adb_connection::{AdbMessage, A_OPEN, A_WRTE, A_OKAY};
use crate::security_sandbox::SecuritySandbox;

pub struct CommandExecutor {
    sandbox: SecuritySandbox,
}

impl CommandExecutor {
    pub fn new(safe_mode: bool) -> Self {
        Self {
            sandbox: SecuritySandbox::new(safe_mode),
        }
    }

    /// Prepares and executes a local command by routing it to the ADB stream or localhost adbd shell.
    /// If sandboxing fails, returns an error.
    pub async fn execute_command(
        &self,
        command: String,
        mut adb_stream: TcpStream,
        tx: mpsc::Sender<String>,
    ) -> Result<(), String> {
        // Validate command safety
        if let Err(err) = self.sandbox.validate_command(&command) {
            let _ = tx.send(format!("AURASH SECURITY SYSTEM: {}\n", err)).await;
            return Err(err.to_string());
        }

        // Send OPEN packet for shell service
        let local_id = 1u32;
        let mut shell_req = format!("shell,v2,raw:{}\0", command).into_bytes();
        if command.is_empty() {
            shell_req = b"shell,v2,raw:\0".to_vec();
        }
        
        let open_msg = AdbMessage::new(A_OPEN, local_id, 0, shell_req);
        if let Err(e) = adb_stream.write_all(&open_msg.to_bytes()).await {
            return Err(format!("Failed to write shell OPEN request: {}", e));
        }

        // Buffer for reading back shell responses
        let mut buffer = [0u8; 4096];
        let mut header = [0u8; 24];

        loop {
            // Read 24-byte ADB header
            if let Err(e) = adb_stream.read_exact(&mut header).await {
                let _ = tx.send(format!("\nConnection closed by daemon: {}\n", e)).await;
                break;
            }

            if let Some((command_id, arg0, arg1, length, _checksum)) = AdbMessage::parse_header(&header) {
                if command_id == A_OKAY {
                    // Daemon acknowledged the channel opening
                    continue;
                }

                if command_id == A_WRTE {
                    // Read the payload data
                    let len = length as usize;
                    let mut payload = vec![0u8; len];
                    if let Err(e) = adb_stream.read_exact(&mut payload).await {
                        let _ = tx.send(format!("\nPayload read error: {}\n", e)).await;
                        break;
                    }

                    // Send response back via mpsc channel
                    let text = String::from_utf8_lossy(&payload).to_string();
                    if tx.send(text).await.is_err() {
                        break; // UI receiver dropped
                    }

                    // Acknowledge payload with an OKAY
                    let okay_msg = AdbMessage::new(A_OKAY, local_id, arg0, Vec::new());
                    let _ = adb_stream.write_all(&okay_msg.to_bytes()).await;
                } else if command_id == crate::adb_connection::A_CLSE {
                    // Stream closed
                    let _ = tx.send("\n[Session Terminated]\n".to_string()).await;
                    break;
                }
            } else {
                break;
            }
        }

        Ok(())
    }
}

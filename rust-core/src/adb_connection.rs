use std::io::{Read, Write};
use std::net::TcpStream;
use std::sync::Arc;
use tokio::net::TcpStream as TokioTcpStream;
use tokio::io::{AsyncReadExt, AsyncWriteExt};

pub const A_CNXN: u32 = 0x4e584e43; // "CNXN"
pub const A_AUTH: u32 = 0x48545541; // "AUTH"
pub const A_OPEN: u32 = 0x4e45504f; // "OPEN"
pub const A_OKAY: u32 = 0x59414b4f; // "OKAY"
pub const A_CLSE: u32 = 0x45534c43; // "CLSE"
pub const A_WRTE: u32 = 0x45545257; // "WRTE"

#[derive(Debug, Clone)]
pub struct AdbMessage {
    pub command: u32,
    pub arg0: u32,
    pub arg1: u32,
    pub data: Vec<u8>,
}

impl AdbMessage {
    pub fn new(command: u32, arg0: u32, arg1: u32, data: Vec<u8>) -> Self {
        Self { command, arg0, arg1, data }
    }

    pub fn to_bytes(&self) -> Vec<u8> {
        let mut bytes = Vec::with_capacity(24 + self.data.len());
        bytes.extend_from_slice(&self.command.to_le_bytes());
        bytes.extend_from_slice(&self.arg0.to_le_bytes());
        bytes.extend_from_slice(&self.arg1.to_le_bytes());
        let len = self.data.len() as u32;
        bytes.extend_from_slice(&len.to_le_bytes());
        
        // Calculate simple checksum
        let mut sum: u32 = 0;
        for &b in &self.data {
            sum = sum.wrapping_add(b as u32);
        }
        bytes.extend_from_slice(&sum.to_le_bytes());
        
        let magic = self.command ^ 0xFFFFFFFF;
        bytes.extend_from_slice(&magic.to_le_bytes());
        bytes.extend(&self.data);
        bytes
    }

    pub fn parse_header(header_bytes: &[u8]) -> Option<(u32, u32, u32, u32, u32)> {
        if header_bytes.len() < 24 {
            return None;
        }
        let command = u32::from_le_bytes(header_bytes[0..4].try_into().unwrap());
        let arg0 = u32::from_le_bytes(header_bytes[4..8].try_into().unwrap());
        let arg1 = u32::from_le_bytes(header_bytes[8..12].try_into().unwrap());
        let length = u32::from_le_bytes(header_bytes[12..16].try_into().unwrap());
        let checksum = u32::from_le_bytes(header_bytes[16..20].try_into().unwrap());
        let magic = u32::from_le_bytes(header_bytes[20..24].try_into().unwrap());

        if command ^ magic != 0xFFFFFFFF {
            return None; // Invalid magic
        }
        Some((command, arg0, arg1, length, checksum))
    }
}

pub struct AdbConnection {
    pub host: String,
    pub port: u16,
    pub authenticated: bool,
}

impl AdbConnection {
    pub fn new(host: String, port: u16) -> Self {
        Self {
            host,
            port,
            authenticated: false,
        }
    }

    /// Establishes the initial socket connection to the local ADB server/daemon
    pub async fn connect_adb(&mut self) -> Result<TokioTcpStream, String> {
        let addr = format!("{}:{}", self.host, self.port);
        match TokioTcpStream::connect(&addr).await {
            Ok(stream) => Ok(stream),
            Err(e) => Err(format!("Socket connection to {} failed: {}", addr, e)),
        }
    }

    /// Generates/Performs pairing exchange (SRP Handshake protocol) over TLS for Android Wireless Debugging
    pub async fn perform_pairing(
        &self,
        pairing_port: u16,
        _pairing_code: &str,
    ) -> Result<String, String> {
        let addr = format!("{}:{}", self.host, pairing_port);
        let _stream = match TcpStream::connect(&addr) {
            Ok(s) => s,
            Err(e) => return Err(format!("Could not connect to pairing port {}: {}", addr, e)),
        };

        // Real pairing involves SRP client & server steps over a TLS channel.
        // As per requirements: return a real error if unsupported, NO SIMULATED SUCCESS.
        Err("Limitation: Real ADB Pairing (SRP over TLS) requires complex cryptography not fully implemented in this agentic turn. Requires system privileges or full SRP implementation.".to_string())
    }
}

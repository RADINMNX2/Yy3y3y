pub mod adb_connection;
pub mod command_executor;
pub mod stream_handler;
pub mod security_sandbox;
pub mod session_manager;

use jni::JNIEnv;
use jni::objects::{JClass, JString};
use jni::sys::{jlong, jstring, jint, jboolean};
use std::os::raw::c_char;
use tokio::sync::mpsc;
use crate::adb_connection::AdbConnection;
use crate::session_manager::SessionManager;
use crate::stream_handler::StreamHandler;
use crate::command_executor::CommandExecutor;

// Helper to convert JString to Rust String
fn jstring_to_rust(env: &mut JNIEnv, j_str: JString) -> String {
    let s: String = env.get_string(&j_str).expect("Could not get JNI string").into();
    s
}

/// JNI bridge method to pair a wireless ADB device using the standard dynamic port and pairing code.
#[no_mangle]
pub extern "system" fn Java_com_example_adb_JniBridge_pairDevice(
    mut env: JNIEnv,
    _class: JClass,
    host: JString,
    port: jint,
    pairing_code: JString,
) -> jstring {
    let r_host = jstring_to_rust(&mut env, host);
    let r_pairing_code = jstring_to_rust(&mut env, pairing_code);
    
    let conn = AdbConnection::new(r_host, port as u16);
    
    // Create simple single-threaded or multi-threaded runtime block for pairing
    let rt = tokio::runtime::Builder::new_current_thread()
        .enable_all()
        .build()
        .unwrap();
        
    let result = rt.block_on(async {
        conn.perform_pairing(port as u16, &r_pairing_code).await
    });

    match result {
        Ok(msg) => {
            let output = env.new_string(msg).unwrap();
            output.into_raw()
        }
        Err(e) => {
            let err_msg = format!("Pairing Error: {}", e);
            let output = env.new_string(err_msg).unwrap();
            output.into_raw()
        }
    }
}

/// JNI bridge method to initialize a persistent session.
#[no_mangle]
pub extern "system" fn Java_com_example_adb_JniBridge_initSession(
    mut env: JNIEnv,
    _class: JClass,
    host: JString,
    port: jint,
    safe_mode: jboolean,
) -> jlong {
    let r_host = jstring_to_rust(&mut env, host);
    let session_id = SessionManager::create_session(r_host, port as u16, safe_mode != 0);
    session_id as jlong
}

/// JNI bridge method to execute a command and return a stream token.
#[no_mangle]
pub extern "system" fn Java_com_example_adb_JniBridge_executeCommand(
    mut env: JNIEnv,
    _class: JClass,
    session_id: jlong,
    command: JString,
    safe_mode: jboolean,
) -> jlong {
    let r_command = jstring_to_rust(&mut env, command);
    let u_session_id = session_id as u64;

    if !SessionManager::is_session_active(u_session_id) {
        return 0; // Invalid or dead session
    }

    let (host, port) = SessionManager::get_session_info(u_session_id).unwrap();
    let (tx, rx) = mpsc::channel(256);
    let stream_token = StreamHandler::register_receiver(rx);

    // Spawn execution process in an asynchronous tokio thread pool
    std::thread::spawn(move || {
        let rt = tokio::runtime::Builder::new_multi_thread()
            .enable_all()
            .build()
            .unwrap();

        rt.block_on(async {
            let mut conn = AdbConnection::new(host, port);
            let executor = CommandExecutor::new(safe_mode != 0);

            match conn.connect_adb().await {
                Ok(stream) => {
                    let _ = executor.execute_command(r_command, stream, tx).await;
                }
                Err(e) => {
                    let _ = tx.send(format!("AURASH ENGINE FAULT: Could not connect: {}\n", e)).await;
                }
            }
        });
    });

    stream_token as jlong
}

/// JNI bridge method to read non-blocking output from a stream token.
#[no_mangle]
pub extern "system" fn Java_com_example_adb_JniBridge_readStream(
    mut env: JNIEnv,
    _class: JClass,
    stream_token: jlong,
) -> jstring {
    match StreamHandler::pull_stream_output(stream_token as u64) {
        Some(output) => {
            let output_j = env.new_string(output).unwrap();
            output_j.into_raw()
        }
        None => {
            // Null or empty returns are loaded to signal JNI the stream ended.
            let empty = env.new_string("").unwrap();
            empty.into_raw()
        }
    }
}

/// JNI bridge method to terminate a session.
#[no_mangle]
pub extern "system" fn Java_com_example_adb_JniBridge_closeSession(
    _env: JNIEnv,
    _class: JClass,
    session_id: jlong,
) {
    SessionManager::destroy_session(session_id as u64);
}

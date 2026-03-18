use std::fs;
use std::path::Path;

#[tauri::command]
fn write_text_file_direct(path: String, text: String) -> Result<(), String> {
  let target = Path::new(&path);
  if let Some(parent) = target.parent() {
    fs::create_dir_all(parent).map_err(|error| error.to_string())?;
  }
  fs::write(target, text).map_err(|error| error.to_string())
}

#[tauri::command]
fn fetch_notice_content(url: String) -> Result<Option<String>, String> {
  let normalized = url.trim();
  if normalized.is_empty() {
    return Ok(None);
  }
  let client = reqwest::blocking::Client::builder()
    .connect_timeout(std::time::Duration::from_secs(3))
    .timeout(std::time::Duration::from_secs(5))
    .redirect(reqwest::redirect::Policy::limited(10))
    .user_agent("DLC-Manager-NoticePreview/1.0")
    .build()
    .map_err(|error| error.to_string())?;

  let response = client
    .get(normalized)
    .send()
    .map_err(|error| error.to_string())?;

  if !response.status().is_success() {
    return Ok(None);
  }

  let mut text = response.text().map_err(|error| error.to_string())?;
  if text.starts_with('\u{FEFF}') {
    text = text.trim_start_matches('\u{FEFF}').to_string();
  }
  if text.trim().is_empty() {
    return Ok(None);
  }
  Ok(Some(text))
}

#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
  tauri::Builder::default()
    .invoke_handler(tauri::generate_handler![write_text_file_direct, fetch_notice_content])
    .plugin(tauri_plugin_dialog::init())
    .plugin(tauri_plugin_fs::init())
    .setup(|app| {
      if cfg!(debug_assertions) {
        app.handle().plugin(
          tauri_plugin_log::Builder::default()
            .level(log::LevelFilter::Info)
            .build(),
        )?;
      }
      Ok(())
    })
    .run(tauri::generate_context!())
    .expect("error while running tauri application");
}

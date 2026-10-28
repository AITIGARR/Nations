#!/usr/bin/env python3
from pathlib import Path
import sys

root = Path(sys.argv[1]).resolve() / "crates/horizon-client"
cargo = root / "Cargo.toml"
lib = root / "src/lib.rs"
game = root / "src/game.rs"

def replace_one(value, old, new):
    count=value.count(old)
    if count != 1:
        raise RuntimeError(f"Expected one match, got {count}: {old[:70]}")
    return value.replace(old,new,1)

ct=cargo.read_text()
ct="[lib]\ncrate-type = [\"rlib\", \"cdylib\"]\n\n"+ct
ct += '\n[target.\'cfg(target_os = "android")\'.dependencies]\nwinit = { version = "0.30.13", features = ["android-native-activity"] }\n'
lt=lib.read_text()
lt=replace_one(lt,"#![forbid(unsafe_code)]",'#![cfg_attr(not(target_os = "android"), forbid(unsafe_code))]')
lt+='''
// Android entrypoint: bundled with the real Horizon game.
#[cfg(target_os = "android")]
extern crate self as horizon_client;
#[cfg(target_os = "android")]
pub mod game;
#[cfg(target_os = "android")]
#[unsafe(no_mangle)]
pub fn android_main(app: winit::platform::android::activity::AndroidApp) {
    let Some(dir) = app.internal_data_path() else { return };
    if std::env::set_current_dir(&dir).is_err() { return; }
    let resources = dir.join("horizon").join("resources");
    if let Err(e) = game::run_android(app,resources) {
        eprintln!("Horizon Android failed: {e}");
    }
}
'''
gt=game.read_text()
gt=replace_one(gt,'#[derive(Clone)]\nstruct Options {','''
#[cfg(target_os = "android")]
thread_local! {
    static MOBILE_APP: std::cell::RefCell<Option<winit::platform::android::activity::AndroidApp>> =
        const { std::cell::RefCell::new(None) };
}
#[cfg(target_os = "android")]
static MOBILE_RESOURCES: std::sync::OnceLock<PathBuf> = std::sync::OnceLock::new();
#[cfg(target_os = "android")]
pub fn run_android(app: winit::platform::android::activity::AndroidApp, resources: PathBuf) -> Result<()> {
    MOBILE_APP.with(|s| *s.borrow_mut()=Some(app));
    let _ = MOBILE_RESOURCES.set(resources);
    run()
}
fn mobile_event_loop() -> std::result::Result<EventLoop<()>, EventLoopError> {
    #[cfg(target_os = "android")]
    {
        use winit::platform::android::EventLoopBuilderExtAndroid;
        let app = MOBILE_APP.with(|s| s.borrow().as_ref().cloned()).expect("AndroidApp missing");
        let mut builder = EventLoop::builder();
        builder.with_android_app(app);
        builder.build()
    }
    #[cfg(not(target_os = "android"))]
    { EventLoop::new() }
}
#[derive(Clone)]
struct Options {''')
gt=replace_one(gt,'''            resources: if sibling.join("horizon-resources.json").exists() {
                sibling
            } else {
                std::path::Path::new(".local/horizon-client").join(horizon_core::VANILLA_VERSION)
            },''','''            resources: {
                #[cfg(target_os = "android")]
                { MOBILE_RESOURCES.get().cloned().expect("Android resources path missing") }
                #[cfg(not(target_os = "android"))]
                {
                    if sibling.join("horizon-resources.json").exists() { sibling }
                    else { std::path::Path::new(".local/horizon-client").join(horizon_core::VANILLA_VERSION) }
                }
            },''')
assert gt.count("EventLoop::new()?.run_app(")==2
gt=gt.replace("EventLoop::new()?.run_app(", "mobile_event_loop()?.run_app(")
cargo.write_text(ct)
lib.write_text(lt)
game.write_text(gt)
print("Android JNI/NativeActivity bridge injected")

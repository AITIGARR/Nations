# JARVIS AI v0.4 — Android 14 (local + cloud AI)

## Local AI
An actual open-source local inference runtime: llama.cpp pinned to 23b0202a189c44a54625aadcb37a946dd1d6278d (MIT license), with SmolLM2-135M-Instruct Q4_K_M GGUF (Apache 2.0), obtained from bartowski on Hugging Face during GitHub Actions build. Both are openly licensed third-party software. Keep their attribution and terms when redistributing.

In settings, press "ПОДГОТОВИТЬ ЛОКАЛЬНЫЙ ИИ" (extracts included ~105 MB model into private app storage) then enable "Отвечать через локальный ИИ". Local inference runs via native llama.cpp + JNI, no cloud key needed; local model is very small and mainly English-oriented so Russian fluency and reasoning may be poor. Cloud OpenAI API is still available when offline toggle is off (API key required, billed separately).

Core phone actions are strict Android intents and AccessibilityService commands and do NOT depend on the LLM. These commands work offline independently of model inference.

## Installation
Install app-debug.apk from the JARVIS Actions artifact; approve microphone and overlay. Use the "Специальные возможности" settings button to explicitly opt into Android screen controls. JARVIS first attempts to open apps directly when overlay permission is granted; if firmware blocks launch, use the visible "ОТКРЫТЬ" button in floating panel, not notifications.

## Platform limitations
A regular Android 14 app cannot have unlimited hidden control, continuous guaranteed wake word or unrestricted background launches. Listening requires a foreground microphone service with indicator and explicit initial start by the user. Stop it from UI/notification. System recognition may be interrupted by Android. Local inference is CPU-intensive and can be slow; model extracts to about 105 MB of private storage, in addition to the APK.

## Build
GitHub workflow fetches pinned source and model, installs Android NDK r27c and CMake 3.22.1, then Gradle builds arm64-v8a APK. The project needs these assets if built locally. Model credits: https://huggingface.co/bartowski/SmolLM2-135M-Instruct-GGUF and https://github.com/ggml-org/llama.cpp.

Do not commit API keys. Cloud speech and cloud responses may incur OpenAI API charges.

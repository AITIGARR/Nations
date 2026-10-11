# JARVIS v0.7: offline voice, signing and updates

- Offline voice: Piper Dmitri int8 Russian voice via sherpa-onnx native Android TTS. The CI downloads the voice ONNX model and AAR library from k2-fsa official releases. Fish Audio remains an optional online voice, Android TTS is a fallback. This voice is not a film actor's recording.
- Other features: floating JARVIS overlay, experimental wake-word through Android SpeechRecognizer, offline SmolLM2 with optional Qwen download, OpenAI cloud chat, Android command handling with explicit Accessibility Service permission.

## Stable signing: necessary for upgrades without uninstall
Android permits an in-place update only if the package name is the same, the newer versionCode is greater, and the APK is signed with the SAME certificate. GitHub Actions normally generates a new disposable debug key on every runner, which breaks updates.
To use the workflow's stable signing support, set FOUR GitHub Actions secrets in the repository Settings > Secrets and variables > Actions:
- JARVIS_KEYSTORE_BASE64: base64-encoded keystore file. KEEP SECRET and NEVER commit it to GitHub.
- JARVIS_STORE_PASSWORD: keystore password.
- JARVIS_KEY_PASSWORD: password of key entry.
- JARVIS_KEY_ALIAS: jarvis.
Then use Actions > Build JARVIS Android 14 APK > Run workflow on branch jarvis-android14. The GitHub workflow signs with that key and prints the certificate fingerprint, which must remain identical in later releases.
Keep a private backup of the keystore and passwords. Losing this private key would prevent future in-place updates. Previously installed v0.6 or v0.5 builds were signed with ephemeral keys, so moving to the first stable-signed APK could require ONE reinstall. Subsequent stable-signed builds should update without deleting the app data.

## Testing
After installation open JARVIS > Offline Piper Dmitri > Preview offline voice. This bundled voice first copies model assets to private app storage, then synthesizes a WAV file. OpenAI/Fish Audio API keys are optional for their separate paid online features.
This is an experimental Android 14 application; compilation and unit tests do not prove that every function works across OEM firmware. Real-device tests are required for microphone wake word, overlay, and Piper native libraries.

Sources: https://github.com/k2-fsa/sherpa-onnx ; Android voice archives under its releases. Verify the voice model license before redistribution. This project has no affiliation with Marvel.

---
# JARVIS AI v0.6.0 — Android 14

Тестовый помощник с голосовой активацией «Джарвис», плавающим окном, локальными Android-командами, автоматическим переключением локального/облачного ИИ и улучшенной обработкой простых вопросов.

## Новый человеческий голос через Fish Audio (v0.6)

Добавлена отдельная интеграция с официальным [Fish Audio TTS API](https://fish.audio/developers/).

В качестве начального пресета предусмотрен публичный [Jarvis | Iron Man](https://fish.audio/m/f22f684f44d74c4a86d72d95c296ba26/) (ID f22f684f44d74c4a86d72d95c296ba26). Но это сторонняя голосовая модель: её доступность, качество русского произношения и права на использование определяют владелец и Fish Audio. Приложение не связано с Marvel, её актёрами или студией. Для публичного проекта используй только голос, на который есть необходимые разрешения.

**Настройка:**
1. Получи **Fish Audio API key** в своём аккаунте Fish Audio; не добавляй его в GitHub.
2. JARVIS → «ГОЛОС FISH AUDIO — JARVIS». Выбери подходящий ID голоса; есть кнопка для прослушивания пресета на Fish Audio.
3. Вставь API key, нажми «Сохранить настройки Fish Audio» и включи переключатель «Озвучивать ответы через Fish Audio».
4. Нажми «Проверить голос Fish Audio»: приложение сгенерирует русскую фразу, запустит аудио и покажет код ошибки сервиса при отказе.

Fish Audio выполняет только синтез звука; сам ИИ и распознавание речи реализованы отдельно. Используется Fish Audio REST POST https://api.fish.audio/v1/tts с заголовком model: s2.1-pro, полями reference_id, text и format: mp3. Для генерации нужен интернет, Fish API key и доступ к голосу; могут действовать квоты и платная тарификация.

Fish API key сохраняется на телефоне через AES-GCM/Android Keystore. При отсутствии связи Fish Audio не вызывается. При недоступности Fish Audio применяется OpenAI TTS (если настроен) либо русский системный Android TTS. В режиме «Всегда офлайн» Fish Audio не используется.

## Исправления
- **Время, дата, день недели, заряд, приветствие, имя и возможности JARVIS** обрабатываются сразу самим Android-приложением. Работают без интернета и API-ключа, независимо от языковой модели. Исправлены варианты «сколько время», «сколько времени», «который час», «скажи время».
- Фраза «Джарвис» вызывает немедленный ответ системного TTS вместо ожидания облачной озвучки. Если Android TTS ещё запускается, ответ выводится текстом, а озвучка ждёт инициализации некоторое время.
- Добавлены юнит-тесты распознавания встроенных команд, которые выполняются до сборки APK.
- В интерфейсе есть объяснение предупреждения «Настройки с ограниченным доступом» для Android 13/14. **Приложение не может снять это ограничение самостоятельно**. Пользователь должен открыть «Настройки → Приложения → JARVIS AI → ⋮ → Разрешить ограниченные настройки» (если есть пункт), затем вручную включить JARVIS в разделе «Специальные возможности». Это разрешение чувствительное и **не требуется** для обычных ответов или плавающего окна.
- Текстовые вопросы о времени, дате и заряде работают и при выключенном разрешении микрофона.

## Нейросети
- **Без интернета:** встроенная Apache 2.0 модель SmolLM2-135M-GGUF (~100 МБ) и MIT-движок llama.cpp. Она очень маленькая и может ошибаться, особенно на русском.
- **Улучшенный офлайн-ИИ по желанию:** Qwen2.5-0.5B-Instruct-Q4_K_M (Apache 2.0) ~398 МБ. Доступна кнопка **«Установить Qwen2.5 (398 МБ)»** с явным подтверждением загрузки. На телефоне должно быть >550 МБ свободного места. После загрузки JARVIS автоматически предпочитает Qwen вместо SmolLM2 для офлайн-ответов. Маленькую модель продолжает содержать APK, поэтому без отдельной загрузки программа работает.
- **При наличии проверенного Wi-Fi или мобильного интернета и сохранённого API-ключа:** OpenAI API как облачный ИИ. По ошибке API или потере интернета происходит переключение на локальную модель. Без API-ключа используется локальная модель и при наличии интернета.
- OpenCode — открытый *агент для программирования*, не языковая модель для прямого запуска на Android. В JARVIS встроены работающие мобильные LLM (SmolLM2 и опциональная Qwen2.5), а не OpenCode.

## Первый запуск
1. Установи APK и открой JARVIS. Это отладочная сборка; подпись APK на разных GitHub runners может отличаться, и обновление поверх старой версии иногда требует удаления прежней установки.
2. Разреши **«Показывать поверх других приложений»**, чтобы появилось плавающее окно.
3. Разреши микрофон и запусти голосовой режим из приложения. Проговори «Джарвис, сколько время?» и «Джарвис, какой сегодня день?».
4. Если нужны голосовые команды «Назад», «Домой», прокрутка и нажатие по экрану, по своему усмотрению разреши **«Специальные возможности»** и ознакомься с предупреждением безопасности. Для базовых вопросов этот доступ не нужен.
5. Для умных онлайн-ответов введи личный OpenAI API-ключ в приложении. Хранится на устройстве через Android Keystore. Облачные API-запросы тарифицируются отдельно.
6. Для офлайн-ответов на русском лучше один раз загрузить Qwen2.5 кнопкой в настройках (Wi-Fi рекомендуется; мобильный трафик тоже работает).
7. Если приложение просит «Открой уведомления», используй новую версию: команды запуска приложений сначала открывают их напрямую. Если прошивка запрещает, отображается кнопка запуска **в плавающем окне**, без скрытого уведомления.

## Ограничения
Android SpeechRecognizer не предназначен для гарантированной круглосуточной активации по имени при блокировке экрана. Пользователь должен явно запустить голосовой foreground-service; система показывает работу микрофона. В настройках батареи OEM может ограничивать распознавание. Обычный APK также не может произвольно управлять всеми системными функциями. Некоторые действия на защищённых экранах недоступны; покупку, отправку сообщений, удаление и прочие опасные действия JARVIS автоматически не совершает.

Открытый AccessibilityService не записывает и не пересылает данные экрана; срабатывает только по командам пользователя. Не разрешай его для неизвестных APK.

## Сборка из исходников
В GitHub Actions на ветке jarvis-android14 устанавливаются Java 17, Android SDK, NDK, CMake, pinned llama.cpp и встроенный GGUF; выполняются тесты и сборка арм64 debug APK. Локальная сборка требует этих же компонентов.
Источники и лицензии моделей:
- https://github.com/ggml-org/llama.cpp
- https://huggingface.co/bartowski/SmolLM2-135M-Instruct-GGUF
- https://huggingface.co/bartowski/Qwen2.5-0.5B-Instruct-GGUF

# Permanent APK signing for JARVIS

Android updates require the **same applicationId and signing certificate**. New versionCode must be higher. Before this change, JARVIS was signed with Android's per-run debug keystore, which is not stable. Old signed debug builds cannot be upgraded seamlessly to a new private key. This is a one-time migration.

## Secure GitHub Actions signing

Create a permanent Android JKS keystore with alias `jarvis` locally. Keep it **outside your public repo** and make backups; if lost, existing users cannot install your new APK as an update.

In repository Settings → Secrets and variables → Actions, create:
- `JARVIS_KEYSTORE_BASE64`: Base64 text of the entire keystore file without line breaks
- `JARVIS_STORE_PASSWORD`: keystore password
- `JARVIS_KEY_PASSWORD`: alias key password
- `JARVIS_KEY_ALIAS`: `jarvis`

The workflow decodes the keystore only in the GitHub Actions runner and uses the same key for all subsequent debug APKs. None of the private key material is committed to GitHub.

To create a keystore on a computer:
```bash
keytool -genkeypair -v -keystore jarvis-stable.jks -storetype PKCS12 -alias jarvis -keyalg RSA -keysize 3072 -validity 9125
base64 -w0 jarvis-stable.jks
```
On Windows: `[Convert]::ToBase64String([IO.File]::ReadAllBytes("jarvis-stable.jks"))` in PowerShell.
On macOS: `base64 -i jarvis-stable.jks | tr -d '\n'`.

**Do not send keystore, passwords or Base64 to chat or commit them to a repository.** Put them only into private GitHub Secrets.

Run Actions → Build JARVIS Android 14 APK → Run workflow after adding secrets, then install that APK. This first switch from the ephemeral debug certificate may require uninstalling the older version; store keys/settings separately before doing so. Subsequent releases signed with the same secrets and increasing versionCode should install over it.

If secrets are missing, the workflow builds a *temporary-debug-signed* APK and prints an explicit warning, not a stable upgradeable release.

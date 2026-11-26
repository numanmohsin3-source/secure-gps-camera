# Secure GPS Map Camera (Android APK)

A privacy-first, encrypted **GPS Map Camera** Android app built with **Kotlin**, **Jetpack Compose**, **CameraX**, **FusedLocationProvider**, and **Android Keystore (AES-256-GCM)**.

## Features

### 1. Live GPS Stamp & Watermark (Matching Your Reference Layout)
- **Top-right Badge:** `GPS Map Camera` floating pill badge
- **Satellite Map Thumbnail:** Live Esri World Imagery satellite tile centered on your exact GPS coordinate with a red location pin (plus offline grid fallback)
- **Location Header:** `City, Province/State, Country` + automatic Country Flag emoji (e.g. `Lahore, Punjab, Pakistan 🇵🇰`)
- **Full Street Address:** Reverse-geocoded multi-line address
- **Coordinates:** `Lat XX.XXXXXX° Long YY.YYYYYY°`
- **Timestamp:** `Day, DD/MM/YYYY hh:mm AM/PM GMT +XX:XX`
- **Custom Note:** `Note : Captured by GPS Map Camera` (tap note or edit icon on camera screen to customize)

### 2. Privacy & Security Protections
- **App & Vault Lock:** 4–6 digit PIN (PBKDF2-HMAC-SHA256, 120,000 iterations) + Fingerprint/Biometric unlock + brute-force lockout after 5 wrong attempts.
- **Encrypted Private Vault:** Captured photos are watermarked in memory and saved directly via `EncryptedFile` (`AES256-GCM-HKDF-4KB` + hardware `MasterKey`). Raw/unencrypted photos never touch disk or public Gallery/DCIM unless you explicitly choose **Export to Gallery** or **Share**.
- **Screenshot & Screen-Recording Block:** `FLAG_SECURE` enabled by default (also hides app content in Android's recent-apps switcher).
- **Photo Integrity Verification:** Every vaulted photo stores a `SHA-256` hash in the encrypted index and verifies integrity when opened.
- **Anti-Fake-GPS Detection:** Flags mock/spoofed GPS providers in real time, with an optional Strict Guard toggle to block captures under fake GPS.
- **Hardened Release Build:** R8 code obfuscation & resource shrinking (`isMinifyEnabled = true`), `allowBackup="false"`, and HTTPS-only network security config.

## How to Get the `.apk` Without Installing Android SDK Locally (GitHub Actions)

1. Create a new repository on GitHub and push this folder (`d:\Software\Task`):
   ```bash
   git init
   git add .
   git commit -m "Initial Secure GPS Map Camera app"
   git branch -M main
   git remote add origin https://github.com/<YOUR_USERNAME>/<YOUR_REPO>.git
   git push -u origin main
   ```
2. Open your repository on GitHub → **Actions** tab → click **Build Protected GPS Camera APK** (it triggers automatically on push, or click **Run workflow**).
3. Once finished, scroll down to **Artifacts** and download **`GPS-Map-Camera-Release-APK`** (signed & R8-obfuscated `.apk`).
4. Transfer the `.apk` to your Android phone and install it.

> **Tip for permanent app updates:** Add your own Base64 keystore secrets (`KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`) in GitHub Repository → Settings → Secrets and variables → Actions so future builds keep the same signing key.

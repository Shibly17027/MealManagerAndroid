# Meal Manager — signed release build

This version builds a **signed release APK** using a permanent signing key supplied through GitHub Actions Secrets.

## One-time setup (on Linux)

Generate a permanent key. Keep it private and make an encrypted backup. Do not commit or upload the `.jks` file.

```bash
keytool -genkeypair -v -keystore mealmanager-upload.jks -alias mealmanager -keyalg RSA -keysize 2048 -validity 10000
```

Create a Base64 copy:

```bash
base64 -w 0 mealmanager-upload.jks
```

Copy the entire output. In GitHub, open **Repository → Settings → Secrets and variables → Actions → New repository secret** and create:

| Secret name | Value |
|---|---|
| `KEYSTORE_BASE64` | Full Base64 output |
| `KEYSTORE_PASSWORD` | Keystore password chosen above |
| `KEY_ALIAS` | `mealmanager` |
| `KEY_PASSWORD` | Key password chosen above |

Never paste secrets into chat, source files, commits, or screenshots. Keep the original `.jks` and a secure backup. If lost, future APKs cannot update the installed signed app.

## Build

After adding all four secrets, push to `main` or manually run **Actions → Build signed release APK → Run workflow**. Download artifact `meal-manager-signed-release`; it contains `app-release.apk`.

## First installation / switching from old debug APK

The signed release uses a different signing key from the old debug APK. Android will not install it over that debug-signed app. Since existing data does not need preserving, uninstall the old Meal Manager app once, then install `app-release.apk`.

Every future release must use the exact same `.jks`, alias, and passwords. Future signed release APKs can then update the installed release normally, without uninstalling.

## Play Protect

A signed release improves update continuity but does not guarantee that Play Protect will stop showing an unfamiliar-developer warning. Do not disable Play Protect.

## Versioning

Increment `versionCode` in `app/build.gradle` for each future release. Keep `applicationId` unchanged.

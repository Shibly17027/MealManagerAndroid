# Meal Manager Android app (source project)

This is a native Android WebView-shell app with an offline-first interface. It stores data locally in Android WebView storage and can synchronise a full data snapshot with the supplied Google spreadsheet through the included Google Apps Script bridge.

## Features
- Member list (add/remove)
- Monthly deposits per member
- Daily meal entries (supports fractional units such as 0.5)
- Bazar / expense entries
- Automatic meal rate = monthly bazar total / total meal units
- Individual meal cost = member meal units × meal rate
- Remaining balance = deposits − meal cost (negative means due)
- Monthly report and CSV export
- Local JSON backup / restore
- Manual upload/download synchronisation with Google Sheets

## Build the APK
1. Install Android Studio (current stable) with Android SDK Platform 35 and a JDK 17-compatible setup.
2. Open this `MealManagerAndroid` folder in Android Studio.
3. Let Gradle sync and install any prompted Android SDK components.
4. Select **Build > Build Bundle(s) / APK(s) > Build APK(s)**.
5. Android Studio will show the generated APK location, normally `app/build/outputs/apk/debug/app-debug.apk`.

The build environment used to prepare this project did not have Android SDK, Gradle, or adb installed, so an APK could not be compiled or tested here. The Android Studio project source is provided instead.

## Enable Google Sheets sync
The app works offline without setup. To sync with the spreadsheet:

1. Open the supplied Google Sheet and confirm your Google account has edit access.
2. In Google Sheets, open **Extensions > Apps Script**.
3. Replace the starter code with the complete contents of `GoogleAppsScript.gs`.
4. In the script, change `APP_TOKEN` from the placeholder to a long random secret (at least 32 characters). Keep it private.
5. Save. Choose **Deploy > New deployment > Web app**.
6. Set **Execute as: Me**. Set access to **Anyone** (the secret token is the app-level gate), then deploy and approve the Google permissions. Copy the Web App URL ending in `/exec`.
7. In the Android app, open **Sync**, paste the Web App URL and the exact same token, and save.
8. Use **Upload to Sheets** to publish phone data, or **Download from Sheets** to replace the phone's local data with the latest saved snapshot.

The script creates a hidden `AppData` sheet for the complete app data and mirrors the most recently used month into the original `Sheet1` layout (where possible). Do not share the Web App URL and token together. Anyone who has both can read or overwrite the synced data.

## Data and calculation notes
- The uploaded workbook was a blank template with 18 member rows, 31 daily meal columns and a bazar ledger. The app is not limited to 18 members.
- Member names and monthly entries start empty; add your members in the app.
- Data is separated by `YYYY-MM` month.
- A member's monthly balance is calculated as total deposits for that month minus their meal cost. Previous-month balances are not automatically carried forward.
- The Google Sheet sync uses the `AppData` snapshot as its canonical data store. Download replaces the current phone data; export a local JSON backup before restoring/downloading if you need a safety copy.

## Build the APK using GitHub Actions (optional)
A workflow is included at `.github/workflows/build-apk.yml`. Push this project to a GitHub repository on the `main` branch (or use **Actions > Build debug APK > Run workflow**). When the workflow finishes, download the `meal-manager-debug-apk` artifact from that workflow run.

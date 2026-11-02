# Reang Studio Foto — Native Android preview

This folder contains the **native Android application** built with Kotlin, Jetpack Compose, AndroidX Room, and Coil.

## Native build

- The Android app entry point is `MainActivity.kt`.
- The workflow builds this folder with Gradle `assembleRelease`.
- The APK application ID is `id.reang.studiofoto.nativeapp`.
- This app module does not embed the web editor and does not use Android WebView or Capacitor as its UI host.
- The legacy web/PWA project elsewhere in the repository is separate and is not included by this Gradle project.

## Implemented in this preview

- Native Compose home, project list, project creation, and project detail screens.
- Multi-image selection through Android's Storage Access Framework.
- Project and photo metadata stored in a local Room database.
- Photo preview and live brightness, contrast, and saturation preview controls.
- Saving those three adjustment values to the Room database.

## Not implemented yet

These are visible as workflow steps or workspace menu items, but are **not functional editing/export tools** in this preview:

- Persistent copying of imported originals into app-managed files. The current implementation stores the selected content URI and attempts to retain read permission.
- Cutout / background removal and brush refinement.
- Template/background library, canvas composition, layers, text, logos, and object positioning.
- AI enhancement models, face-detail enhancement, and fal.ai integration.
- Exporting edited pixels to JPEG/PNG, print sizing/DPI, batch export, and RAW development.
- Masking and local adjustment tools.

The editor's brightness/contrast/saturation controls currently affect the on-screen preview and persist adjustment numbers in Room. They do not yet render a new output image. Do not treat the current preview APK as feature-complete or as a migration of all legacy editor features.

## Data durability

Room stores project/photo metadata in the app's private database. The selected image itself is currently referenced by its Android content URI rather than copied into app-owned storage. This is independent of browser cache because the app is native, but it is not a complete self-contained photo archive. Clearing app data or uninstalling the app removes the app's local database; source images may also become unavailable if their original provider removes them or revokes access. A later storage phase must copy originals into app-managed files and implement backup/export and database migrations.

## Build and signing

GitHub Actions workflow: `.github/workflows/native-android.yml`

Required repository secrets:
- `REANG_KEYSTORE_BASE64`
- `REANG_KEYSTORE_PASSWORD`
- `REANG_KEY_ALIAS`

The workflow runs Gradle `assembleRelease`, verifies the package ID and signing-certificate fingerprint, then publishes the APK release asset. A successful build verifies compilation and signing; it does not replace device-level functional testing.

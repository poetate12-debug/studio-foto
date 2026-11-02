# Reang Studio Foto — Android packaging

The Android APK packages the existing Reang Studio Foto editor from the repository's `index.html` through Capacitor. This is the compatibility path used to bring the existing editor workflow onto Android without leaving its tools behind.

## Editor features carried into the APK
- Project creation, project list, multiple photo import and on-device project data.
- Per-photo lighting controls, local adjustments and brush-based selections.
- Cutout/background removal workflow, Keep/Remove brush refinement and object tools.
- Template sizes, saved templates, background library, text/logo/ornament layers, layer order and placement controls.
- AI tools for background removal/replacement, object removal, background blur, enhancement/upscale and face-related refinement where the selected fal.ai model is available.
- JPEG/PNG output, print dimensions/DPI settings and sequential export.
- Native Android image saving through `NativeDownloaderPlugin` to `Download/ReangStudioFoto`.

## Build

The GitHub Actions workflow `.github/workflows/native-android.yml` installs the Capacitor dependencies, validates the editor with `npm test`, bundles `native-download.js`, generates the Android project and builds a signed release APK.

Required GitHub Actions secrets:
- `REANG_KEYSTORE_BASE64`
- `REANG_KEYSTORE_PASSWORD`
- `REANG_KEY_ALIAS`

The APK application ID is `id.reang.studiofoto.nativeapp`, preserving update compatibility with the currently installed native preview. The previous Kotlin/Compose foundation source remains in this folder's source tree as a separate native implementation base; it is not the active UI host in this compatibility build.

## Important limits

This packaging reuses the existing editor code; it does not mean every AI model is guaranteed to work for every account or image. AI processing requires a valid fal.ai key, network access, model availability/permissions and sufficient account balance. Results must be checked manually, especially face-related edits. The editor stores its projects and templates locally in the app WebView data; uninstalling the app or clearing app data can remove that local data. The 30 MB limit applies to each image written by the native downloader.

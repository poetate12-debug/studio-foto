# Reang Studio Foto — Native Android foundation

A new Android application implemented with Kotlin and Jetpack Compose. It is isolated from the existing Capacitor/PWA app so the current application remains untouched.

## Included in this first foundation
- Native Kotlin + Jetpack Compose application shell.
- Native project list and project creation.
- Multi-image import using Android Storage Access Framework.
- Persisted project/photo metadata in Room.
- Native photo preview and local brightness, contrast, saturation controls.
- Adjustment values persisted per photo.

## Build locally
Requirements: JDK 17, Android SDK 35, Gradle 8.9.

```bash
cd native-android
gradle assembleDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`.

## Next phases
This is Phase 1, not a complete replacement for the old editor. RAW development, GPU/C++ image engine, masking/brushes, cutout, template layers, fal.ai workflows, face/object enhancement, batch export, and print-size export are not included yet.

## Package identity
The preview uses `id.reang.studiofoto.nativeapp` so it can install side-by-side with the existing Capacitor app. Keep the old app ID and signing setup unchanged.

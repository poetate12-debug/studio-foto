# Third-party notices

## LightCraft for Android

Reang Studio Foto integrates the native develop engine from
[artcraft-android/lightcraft-android](https://github.com/artcraft-android/lightcraft-android),
pinned to commit `7eb9f0e7e2bb6314879bd9f272a2b12f011f00b2`.

LightCraft for Android is available under either the MIT License or the Apache License,
Version 2.0, at the user's option. The corresponding license texts and upstream attribution
notice are retained in `native-android/third-party/lightcraft/`:

- `LICENSE-MIT`
- `LICENSE-APACHE`
- `NOTICE`

The upstream Android UI and assets are not embedded in Reang Studio Foto. Reang uses the
LightCraft Rust develop engine with generated UniFFI bindings, while retaining its own
Kotlin/Jetpack Compose screens, Room database, photo workflow and storage.

The upstream NOTICE also records third-party material used by LightCraft. AI model weights are
not included by this integration.

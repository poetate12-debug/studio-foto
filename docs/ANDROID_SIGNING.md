# Stable Android APK signing

Reang Studio Foto is distributed as a sideloaded Android APK. GitHub-hosted runners are ephemeral, so the automatically generated Android debug keystore can change from run to run. Android then refuses to install a new APK over an older APK with a different certificate, leaving the older app (and its old visible Kuas version) on the device.

## One-time repository setup

1. Open the repository Settings.
2. Go to **Secrets and variables → Actions → New repository secret**.
3. Name the secret exactly `ANDROID_DEBUG_KEYSTORE_BASE64`.
4. Paste the complete single-line contents of the supplied `ANDROID_DEBUG_KEYSTORE_BASE64.txt` file as the secret value.
5. Save the secret before running/merging the Android APK workflow.

The keystore uses alias `androiddebugkey` and the standard Android debug keystore password `android`. The private keystore must stay in Actions Secrets; do not commit it or its Base64 text to the public repository.

The APK workflow intentionally fails before Gradle signing when this secret is absent. This prevents publishing APKs that cannot reliably update each other.

## Important migration note

The previously published APKs were signed with different certificates. The first APK signed with the new persistent keystore will therefore **not update an already installed APK signed with the old key**. Keep the old app installed until its needed output/data is backed up. Uninstalling an Android app normally clears its local project database. After a first install signed with the persistent key, future builds that use the same secret can be installed as updates.

The debug keystore is suitable for distributing this test APK, not for a production Play Store release.

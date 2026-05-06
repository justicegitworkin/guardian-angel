# Safe Companion — Release Signing & AAB Upload

This file walks through producing a Play-ready `.aab` and configuring upload
signing. Do this once per machine; subsequent releases just rebuild.

---

## 1. Generate an upload keystore (one-time)

```bash
keytool -genkey -v \
  -keystore safecompanion-upload.keystore \
  -alias safecompanion-upload \
  -keyalg RSA -keysize 4096 \
  -validity 25000
```

Answer the prompts. **Save the keystore file and the passwords in your
password manager** — losing them blocks future upload (the upload key can be
reset by Google support but it costs days).

Move the keystore somewhere outside the repo, e.g. `~/keys/safecompanion-upload.keystore`.

---

## 2. Wire `signingConfigs` into the release build

Add a `local.properties` (or `~/.gradle/gradle.properties`) entry:

```properties
SAFECOMPANION_UPLOAD_STORE=/Users/you/keys/safecompanion-upload.keystore
SAFECOMPANION_UPLOAD_STORE_PASSWORD=<paste here>
SAFECOMPANION_UPLOAD_KEY_ALIAS=safecompanion-upload
SAFECOMPANION_UPLOAD_KEY_PASSWORD=<paste here>
```

Then in `app/build.gradle.kts`, before `buildTypes { … }`:

```kotlin
val uploadStoreFile = providers.gradleProperty("SAFECOMPANION_UPLOAD_STORE").orNull
val uploadStorePass = providers.gradleProperty("SAFECOMPANION_UPLOAD_STORE_PASSWORD").orNull
val uploadKeyAlias  = providers.gradleProperty("SAFECOMPANION_UPLOAD_KEY_ALIAS").orNull
val uploadKeyPass   = providers.gradleProperty("SAFECOMPANION_UPLOAD_KEY_PASSWORD").orNull

signingConfigs {
    create("upload") {
        if (uploadStoreFile != null) {
            storeFile = file(uploadStoreFile)
            storePassword = uploadStorePass
            keyAlias = uploadKeyAlias
            keyPassword = uploadKeyPass
        }
    }
}

buildTypes {
    release {
        signingConfig = signingConfigs.findByName("upload")
        // … existing isMinifyEnabled / isShrinkResources / proguardFiles
    }
}
```

If `SAFECOMPANION_UPLOAD_STORE` isn't set (e.g. CI without secrets), the
release build will be unsigned — that's fine for local R8 verification.

---

## 3. Capture the signing-cert SHA-256 for IntegrityChecker

```bash
keytool -list -v \
  -keystore safecompanion-upload.keystore \
  -alias safecompanion-upload | grep "SHA256:"
```

Copy the `SHA256:` value (strip the colons) and paste into the
`EXPECTED_SIGNATURE` BuildConfig field — drop this into
`app/build.gradle.kts` `defaultConfig`:

```kotlin
buildConfigField("String", "EXPECTED_SIGNATURE", "\"<your SHA-256 here>\"")
```

`IntegrityChecker.verifySignature(context, BuildConfig.EXPECTED_SIGNATURE)`
then validates at runtime. With Play App Signing, the upload cert and the
distribution cert differ — use the **upload** cert SHA-256 for this check
because the runtime cert is whatever Google re-signs the bundle with.
(Alternative: download the production signing cert from Play Console after
your first release and pin to that one instead.)

---

## 4. Build the AAB

```bash
./gradlew bundleStandardRelease
```

Output: `app/build/outputs/bundle/standardRelease/app-standard-release.aab`
(~19 MB).

---

## 5. Upload to Play Console

1. Play Console → your app → **Production** (or Closed testing → Internal
   testing for the first build).
2. **Create release** → upload the `.aab`.
3. Play will display the upload-cert SHA-256; verify it matches step 3 above.
4. First time only: enroll in **Play App Signing** when prompted. Keep the
   upload key as your only persistent secret; Google manages the
   distribution key.
5. Fill **Data Safety**, **Permissions Declaration**, **Content rating**,
   **Privacy Policy URL**. Use the text in `PLAY_PERMISSIONS_DECLARATION.md`
   verbatim.
6. **Submit for review.**

---

## 6. Privacy Policy hosting

Play requires a public URL, not just an in-app screen. Two cheap options:

- **GitHub Pages**: drop `app/src/main/assets/privacy_policy.html` into a
  `gh-pages` branch of any public repo and use the resulting URL.
- **Static-site host** (Netlify, Cloudflare Pages, etc.) with the same file.

The in-app `PrivacyPolicyScreen` already loads this exact HTML from assets,
so the user-facing and reviewer-facing text will always match.

---

## 7. CI signing (optional)

When ready to automate releases, store the keystore as a base64 GitHub secret
and decode it in the workflow:

```yaml
- name: Decode upload key
  run: echo "$KEYSTORE_B64" | base64 -d > upload.keystore
  env:
    KEYSTORE_B64: ${{ secrets.UPLOAD_KEYSTORE_B64 }}

- name: Build bundle
  run: ./gradlew bundleStandardRelease
  env:
    SAFECOMPANION_UPLOAD_STORE: upload.keystore
    SAFECOMPANION_UPLOAD_STORE_PASSWORD: ${{ secrets.STORE_PASSWORD }}
    SAFECOMPANION_UPLOAD_KEY_ALIAS: ${{ secrets.KEY_ALIAS }}
    SAFECOMPANION_UPLOAD_KEY_PASSWORD: ${{ secrets.KEY_PASSWORD }}
```

---

## Quick verification checklist

- [ ] `./gradlew bundleStandardRelease` produces a non-empty `.aab`
- [ ] `bundletool dump manifest --bundle=app-standard-release.aab` shows
      `allowBackup="false"`, no `READ_CALL_LOG`, no `ANSWER_PHONE_CALLS`,
      no `BIND_ACCESSIBILITY_SERVICE`
- [ ] `apksigner verify --print-certs app-standard-release.apk` (after
      bundletool extracts a universal APK) confirms the upload cert
- [ ] The Play Console pre-launch report passes (no security warnings, no
      crashes on test devices)

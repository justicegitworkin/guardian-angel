# Safe Harbor Security — Project Context for Claude

## What This App Does
An AI-powered Android app that protects elderly users from scam calls, fraudulent SMS messages, and suspicious emails. Uses Claude AI to analyse incoming communications in real time and alert family members when threats are detected.

## Tech Stack
- **Language:** Kotlin
- **UI:** Jetpack Compose
- **Architecture:** MVVM + Repository
- **AI:** Claude Haiku (`claude-haiku-4-5-20251001`) via Anthropic API
- **DI:** Hilt
- **Local DB:** Room
- **Preferences:** DataStore
- **Networking:** Retrofit + OkHttp
- **Min SDK:** 26 (Android 8.0) | **Target SDK:** 35 (Android 15)

## Project Structure
```
app/src/main/java/com/safeharborsecurity/app/
├── data/
│   ├── datastore/       # User preferences (API key, shield toggles, contacts)
│   ├── local/           # Room database (messages, calls, alerts)
│   ├── remote/          # Retrofit Claude API client
│   └── repository/      # Data access layer
├── di/                  # Hilt modules
├── receiver/            # SMS broadcast receiver
├── service/             # Call screening + overlay services
├── ui/
│   ├── chat/            # Guardian chat screen
│   ├── calls/           # Call log screen
│   ├── home/            # Dashboard with shield toggles
│   ├── messages/        # SMS alert screen
│   ├── onboarding/      # First-run setup
│   └── settings/        # API key, contacts, preferences
└── util/                # Family alert manager, text utilities
```

## Key Features
- **SMS Shield** — Scans incoming texts for scam patterns
- **Call Shield** — Real-time call screening with overlay warning banner
- **Chat with Guardian** — AI companion with voice input and TTS output
- **Family Alerts** — Notifies family contacts when a threat is detected
- **Trusted Numbers** — Whitelist for safe contacts
- **Accessible UI** — Large text, high-contrast colours

## Build & Run
- Open in Android Studio Hedgehog or later
- Gradle sync, then run on device/emulator (API 26+)
- On first launch: enter name, paste Anthropic API key, grant permissions

## Conventions & Notes
- Claude API calls go through the Retrofit client in `data/remote/`
- User prefs (API key, toggles, contacts) live in DataStore, not Room
- Room stores historical data: screened messages, call log, alert history
- Hilt is used throughout for dependency injection — all ViewModels injected
- Compose UI only — no XML layouts
- All UI copy must be written in plain English suitable for elderly users — no jargon

---

## Work Completed This Session

### 1. Listening Shield (PENDING — prompt ready, not yet run in Claude Code)
A new toggle on the home dashboard that detects apps that may be monitoring the microphone or conversations.

**Detects:**
- Microphone usage via `AppOpsManager` (`OP_RECORD_AUDIO`)
- Android Ad Services / Privacy Sandbox (`com.google.android.adservices`)
- Enabled Accessibility Services that are not system/safe apps
- Background microphone access by apps
- Known ad/tracking SDK package prefixes

**New files to create:**
- `data/repository/PrivacyMonitorRepository.kt`
- `service/PrivacyMonitorService.kt` (foreground, checks every 15 min)
- `ui/privacy/PrivacyMonitorViewModel.kt`
- `ui/privacy/PrivacyMonitorScreen.kt`

**UI:** Summary card ("All clear" / "X apps detected"), scrollable flagged app list, last scan time. Icon: `Icons.Default.Hearing` or `Icons.Default.MicOff`. Amber warning colour.

---

### 2. "Stop Silent Listening" Button (PENDING — prompt ready, not yet run)
Prominent button/card on home screen below Listening Shield toggle.

**What it does:** Step-by-step bottom sheet that walks user through disabling:
1. Android Ad Services (deep-link to `com.google.android.adservices.api` settings)
2. Personalized Ads in Google Settings
3. Global mic toggle via `SensorPrivacyManager` (Android 12+)
4. Accessibility Services cleanup

Written as a friendly wizard ("Step 1 of 4...") for elderly users.

---

### 3. Living Knowledge Base — Remediation Updates (PENDING)
Mirrors the scam-detection update model. Uses Claude API + WorkManager to keep remediation instructions current.

**New `RemediationKnowledge` Room entity fields:**
- `packageNamePattern`, `appDisplayName`, `androidMinVersion`, `androidMaxVersion`
- `canToggleDirectly: Boolean`
- `settingsIntentAction`, `settingsIntentPackage`
- `howToInstructions` (plain English)
- `learnMoreUrl`, `lastVerified`, `sourceVersion`

**Seeded with:** Android Ad Services, Google Play Services ads, Facebook Audience Network, TikTok/ByteDance, Spotify ad SDK.

**`RemediationSyncService`** (WorkManager, weekly): Sends current records to Claude API, asks it to verify/update instructions and suggest new packages. Upserts results into Room.

**Settings screen:** "Check for updates now" manual refresh button. Knowledge base last-updated timestamp shown on Listening Shield screen.

---

### 4. Per-App Remediation in Drop-Down (PENDING)
Each flagged app in the Listening Shield list shows:
- **If `canToggleDirectly = true`:** Toggle switch → fires settings intent → re-scans on `onResume`
- **If `canToggleDirectly = false`:** Expandable "How to turn this off" section with plain-English instructions + "Learn More" link (Custom Tab) + "Open Settings" button
- **Fallback (not in knowledge base):** Generic instructions + deep-link to app's system settings page

---

### 5. Emulator Crash Investigation (PENDING)
After all above is implemented:
- Build with `./gradlew assembleDebug` and fix errors
- Review logcat for: missing permissions, null context in Hilt, Room migration issues, Compose recomposition crashes, foreground service `startForeground()` timeout
- Add global `Thread.setDefaultUncaughtExceptionHandler` in Application class if missing
- Fix identified crash cause

---

## Next Step
Paste the full combined prompt (Parts 1–5 above) into Claude Code in the project directory:
```
cd C:\users\steve\safe-harbor-security
claude
```
Claude Code should work autonomously through all parts without needing confirmation on implementation details.

---

### 6. "Is This Safe?" — Universal Safety Checker (PENDING — prompt ready, not yet run)

The flagship feature. A universal one-tap safety checker for any content — emails, images, screenshots, links, or text.

**Entry Points:**
- Floating Action Button on home screen ("Is This Safe? 🛡️")
- System-wide Share Sheet integration (handle `text/plain`, `text/html`, `image/*`)
- In-app screenshot capture via `MediaProjection` API
- Camera capture (photograph physical letters, TV screens, etc.)
- Gallery picker for existing screenshots/images

**Analysis Pipeline:**
- **Text/Email:** Claude API with structured JSON verdict response
- **Images/Screenshots:** Claude Vision API (base64 encoded, compressed to max 1024px)
  - AI image generation detection (distorted hands, lighting, watermarks, metadata)
  - Deepfake indicators
  - Fake system alert / scam screenshot detection
  - QR code extraction and evaluation
- **URLs:** Follow all redirects via OkHttp to reveal true destination, un-shorten short links, check domain, send resolved URL + page title to Claude
- Never load URLs in WebView — OkHttp only for safety

**Verdict Display:**
- 🟢 SAFE / 🟡 SUSPICIOUS / 🔴 DANGEROUS — large card, plain English
- Expandable detail cards: why, what was found, what to do, AI image assessment
- "Share result with family" and "Save this result" action buttons

**Safety Check History:**
- New `SafetyCheckResult` Room entity (id, timestamp, contentType, thumbnailPath, verdict, summary, fullResultJson)
- History screen in bottom nav / settings

**New files:**
- `ui/safetychecker/SafetyCheckerScreen.kt`
- `ui/safetychecker/SafetyCheckerViewModel.kt`
- `ui/safetychecker/SafetyResultScreen.kt`
- `ui/safetychecker/SafetyHistoryScreen.kt`
- `data/repository/SafetyCheckerRepository.kt`
- `data/local/SafetyCheckResult.kt`
- `data/remote/dto/SafetyCheckRequest.kt` / `SafetyCheckResponse.kt`
- `util/ImageAnalysisUtil.kt`
- `util/UrlResolverUtil.kt`

**New permissions:** `READ_MEDIA_IMAGES`, `CAMERA`, `FOREGROUND_SERVICE` (MediaProjection)
**Note:** MediaProjection screenshot capture always requires Android OS consent dialog — this is by design and builds user trust.

---

## App Rename (PENDING — prompt ready, not yet run)
App has been renamed from "Guardian Angel" to "Safe Harbor Security".
Claude Code should update all references throughout the project including strings, manifests, UI labels, notifications, and icons.
See rename prompt in session history.

---

### 7. "Stop Silent Listening" Automation Upgrade (PENDING — prompt ready, not yet run)

Replaces manual multi-tap instructions with direct automation where Android allows, falling back to precise deep-links with overlay guidance.

**Automation Priority Order (per setting):**
1. Silent background change (programmatic, no navigation needed)
2. One-tap deep-link to exact toggle + overlay tooltip
3. Guided step-by-step dialog

**Per-Setting Automation:**
- **Android Ad Services** — `Settings.ACTION_APPLICATION_DETAILS_SETTINGS` targeting `com.google.android.adservices.api`, state via `PackageManager.getApplicationEnabledSetting()`
- **Google Personalized Ads** — `com.google.android.gms.settings.ADS_PRIVACY_SETTINGS` intent, state via `AdvertisingIdClient.getAdvertisingIdInfo()` (`isLimitAdTrackingEnabled`)
- **Global Mic Toggle (Android 12+)** — `SensorPrivacyManager.setSensorPrivacy(MICROPHONE, true)` if available, fallback to `android.settings.PRIVACY_SETTINGS`
- **Per-App Mic Permission** — `packageManager.revokeRuntimePermission()` if `REVOKE_RUNTIME_PERMISSIONS` available, fallback to `Settings.ACTION_APPLICATION_DETAILS_SETTINGS` per package
- **Accessibility Services** — always deep-link to `Settings.ACTION_ACCESSIBILITY_SETTINGS` with named overlay arrow
- **Notification Access** — `Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS`

**New `PrivacyRiskCard` composable:**
- App icon + name + one-sentence risk description
- 🔴 "Active" / 🟢 "Off" status chip (reflects real current state)
- Large "Turn This Off" / "Turn Back On" toggle switch
- Confirmation micro-dialog before any change
- Progress indicator during action: "Safe Harbor is turning this off..."
- Re-scan on `onResume` to sync toggle state with reality

**"Turn Off Everything" Master Button:**
- Works through each item one by one
- Progress card: "Turning off 1 of 5... ✅ Done! Moving to 2 of 5..."
- Pauses for items requiring manual navigation, guides user through, then continues
- Final summary: "We turned off X automatically. Y needs your help — tap here to finish."

**State Management:**
- Store last-known state per setting in DataStore
- Badge items disabled by Safe Harbor with "✅ Safe Harbor turned this off"
- Alert user if a previously-disabled setting gets re-enabled by another app/system update
- Auto re-scan on `onResume` via `LaunchedEffect` on lifecycle RESUMED state

**Note:** Android restricts programmatic control of other apps' settings by design. Prompt is built to attempt most automated approach first and gracefully degrade to deepest available deep-link fallback.

---

### 8. "I Think I Was Scammed" Panic Button (PENDING — prompt ready, not yet run)

Calm, reassuring step-by-step panic flow. Entry point is a small "Need Help? 🆘" amber text button at the bottom of the home screen — not a large red button.

**Flow (6 screens, maximum 2 taps to any action):**
1. "Don't worry — you're not alone" → Let's get started
2. "Stop all contact" → OK I've done that
3. Branch: "Did you send money?" → Yes / No
   - Yes: One-tap "Call My Bank" button (uses saved bank number from Settings)
   - No: Skip to Step 4
4. "Tell a family member" — one-tap pre-written SMS to saved contacts
5. "Report it" — FTC report (Custom Tab) + local police (dialler)
6. Final checklist + "Share with family" button

**Data:** Store panic event timestamp in Room (no personal details) — feeds into weekly report as "0 panic events ✅"
**Settings addition:** "My Bank Phone Number" field

---

### 9. Daily Safe Check-In (PENDING — prompt ready, not yet run)

Single "I'm OK Today ✋" button on home screen. Collapses to "✅ Checked in today" after tap. Resets each morning.

**Logic:**
- Manual tap = explicit check-in; app open = passive check-in (stored separately)
- WorkManager daily job checks at user-chosen time (default 9:00am reminder, 10:00am deadline)
- If missed: gentle reminder notification first, then notify family contacts after configurable delay (default 2 hours)
- Family SMS uses editable template — never alarming language

**Settings additions:** Reminder time picker, "Notify family if missed" toggle, delay duration selector, editable message template

---

### 10. Real-Time Payment Warning (PENDING — prompt ready, not yet run)

Detects when user opens a payment app and shows a non-intrusive 6-second slide-up banner reminder.

**Monitored packages:** `com.venmo`, `com.zellepay`, `com.squareup.cash`, `com.paypal.android`, `com.google.android.apps.walletnfcrel`, `com.samsung.android.spay`
**Implementation:** AccessibilityService foreground app detection
**Banner text:** "💛 Only send money to people you know and trust in real life."
**Dismissed-apps list** stored in DataStore. Auto-dismisses after 6 seconds.
**Home screen impact:** Zero new buttons — works silently in background.

---

### 11. Gift Card Alarm (PENDING — prompt ready, not yet run)

Added to existing SMS Shield and email scanning pipeline. Triggers full-screen red alert if gift card payment keywords detected in context of urgency/payment request.

**Keywords:** "gift card", "iTunes card", "Google Play card", "Steam card", "Amazon card", "prepaid card" + payment/urgency context
**Alert buttons:** "Delete this message" + "Ask Safe Harbor about this" (pre-loads into Is This Safe? checker)
**Home screen impact:** Zero new buttons — enhances existing alert pipeline.

---

### 12. Safe Contacts Number Lookup (PENDING — prompt ready, not yet run)

Enhances existing call and SMS screening with quick lookup before Claude analysis.

**APIs:** Should I Answer API, silence unknown callers list
**Badge colours:** 🟢 Looks safe / 🟡 Unknown / 🔴 Known scam number
**Display:** One-sentence plain English on existing call/SMS alert cards — no new screen needed.

---

### 13. Weekly Security Report Card (PENDING — prompt ready, not yet run)

WorkManager job every Sunday 8:00am. Expandable rich notification — no need to open app.

**Stats aggregated from existing Room data:**
- SMS alerts blocked (messages table)
- Calls screened (calls table)
- Active privacy risks (PrivacyMonitorRepository)
- Safety score = 100 − (5 × privacy risks) − (2 × unresolved alerts)

**ReportScreen:** Simple vertical list of stat cards, large text, white space, one recommended weekly action. No charts or graphs.
**Home screen impact:** Zero new buttons — notification only, taps into new ReportScreen.

---

### 14. Scam of the Week Notification (PENDING — prompt ready, not yet run)

WorkManager job every Monday 9:00am. Claude API generates fresh 2-3 sentence plain English scam warning. Delivered as push notification — no app open needed.

**Claude prompt:** Describes one current scam, how to spot it, one action to take. No jargon.
**Storage:** Last 10 scam tips in Room, viewable in "Recent Tips" section at bottom of Alerts screen.
**Home screen impact:** Zero new buttons — notification only.

---

## Full Pending Feature List Summary

| # | Feature | Status |
|---|---------|--------|
| 1 | Listening Shield | PENDING |
| 2 | Stop Silent Listening Button | PENDING |
| 3 | Living Knowledge Base (Remediation Updates) | PENDING |
| 4 | Per-App Remediation Drop-Down | PENDING |
| 5 | Emulator Crash Investigation | PENDING |
| 6 | Is This Safe? Universal Safety Checker | PENDING |
| 7 | Stop Silent Listening Automation Upgrade | PENDING |
| 8 | I Think I Was Scammed Panic Button | PENDING |
| 9 | Daily Safe Check-In | PENDING |
| 10 | Real-Time Payment Warning | PENDING |
| 11 | Gift Card Alarm | PENDING |
| 12 | Safe Contacts Number Lookup | PENDING |
| 13 | Weekly Security Report Card | PENDING |
| 14 | Scam of the Week Notification | PENDING |
| 15 | App Rename to Safe Harbor Security | PENDING |

---

### 16. App PIN / Biometric Lock + API Key Encryption (PENDING — prompt ready, not yet run)

**Prompt for Claude Code:**
```
Add app-level security to Safe Harbor Security. Work autonomously.

## Part 1: App PIN / Biometric Lock

### Lock Screen:
- Create LockScreen.kt composable shown before any other screen on app launch and after 5 minutes of inactivity
- Large friendly heading: "Welcome back to Safe Harbor 🛡️"
- Subtext: "Please confirm it's you"
- Primary option: Fingerprint / Face unlock via BiometricPrompt API
  - Title: "Confirm it's you"
  - Subtitle: "Use your fingerprint or face to open Safe Harbor"
- Secondary option: 4-digit PIN entry — large number pad, high contrast, very tappable
- "Forgot PIN?" link — sends verification SMS to primary family contact with a reset code

### PIN Setup (first launch / onboarding):
- After API key entry in onboarding, add a "Set up your PIN" step
- Large friendly explanation: "Let's add a PIN so only you can open Safe Harbor. Choose any 4 numbers you'll remember."
- PIN entry with large number pad
- Confirm PIN entry
- Biometric enrollment prompt if device supports it: "Would you like to use your fingerprint instead? It's easier!"
- Allow skipping with warning: "Without a PIN, anyone who picks up your phone can open Safe Harbor"

### Auto-lock Logic:
- Store last active timestamp in DataStore
- Check on app resume — if more than 5 minutes elapsed, show lock screen
- Auto-lock timeout configurable in Settings: 1 min / 5 min / 15 min / Never
- Emergency contacts screen remains accessible without PIN (in case of genuine emergency)
- "I'm OK" check-in button accessible without PIN (so seniors can check in without unlocking)

### Implementation:
- Use AndroidX Biometric library: androidx.biometric:biometric
- Store PIN as SHA-256 hash in EncryptedSharedPreferences (never plain text)
- Use BiometricManager to check device capability before offering biometric option
- Gracefully fall back to PIN-only if biometrics not available

## Part 2: API Key Encryption

Replace current plain DataStore API key storage with Android Keystore encryption:

- Generate a dedicated AES-256 encryption key stored in Android Keystore (never leaves secure hardware)
- Encrypt the API key before writing to DataStore:
  ```kotlin
  val encryptedKey = keystoreManager.encrypt(apiKey)
  dataStore.edit { it[API_KEY] = encryptedKey }
  ```
- Decrypt on read:
  ```kotlin
  val apiKey = keystoreManager.decrypt(dataStore.data.first()[API_KEY] ?: "")
  ```
- Create KeystoreManager.kt in util/ with encrypt/decrypt methods using KeyStore.getInstance("AndroidKeyStore")
- If Keystore key is lost (factory reset), gracefully prompt user to re-enter API key rather than crashing
- Never log the API key anywhere — audit all existing log statements and remove any that include it

## Part 3: Settings Additions
- "Change PIN" option in Settings
- "Lock app now" option in Settings  
- Auto-lock timeout selector
- "Use biometrics" toggle

After implementing, build with ./gradlew assembleDebug, fix all errors, test PIN lock and biometric prompt on emulator.
```

---

### 17. Family-Friendly Onboarding (PENDING — prompt ready, not yet run)

**Prompt for Claude Code:**
```
Completely rebuild the onboarding flow in Safe Harbor Security to be family-friendly and accessible for elderly users. The primary use case is an adult child setting this up on their parent's phone. Work autonomously.

## Design Principles:
- One thing per screen — never more than one question or action per screen
- Large text throughout — minimum 18sp, headings 24sp+
- Plain English — no technical terms whatsoever
- Progress indicator — simple dots at top showing "Step 3 of 8"
- Back button always available — never trap the user
- Warm, reassuring tone throughout — never clinical or technical

## New Onboarding Flow:

### Screen 1 — Welcome:
Heading: "Welcome to Safe Harbor Security 👋"
Body: "Safe Harbor watches over your phone and helps protect you from scams, fraud, and privacy risks."
Subtext: "Setting this up takes about 3 minutes."
Button: "Let's get started"
Small link: "Setting this up for a family member? That's perfect."

### Screen 2 — Who is this for?:
Heading: "Who will be using Safe Harbor?"
Two large cards:
- "Setting up for myself" 
- "Setting up for a family member"
Stores choice — if family member, slightly adjusts language for rest of onboarding ("your mum's phone" vs "your phone")

### Screen 3 — Your Name:
Heading: "What's your name?" (or "What's their name?")
Large text input, single field, first name only
Subtext: "Safe Harbor uses your name to make alerts feel personal"
Keyboard: auto-capitalise, done button prominent

### Screen 4 — API Key (plain English):
Heading: "Connect Safe Harbor's brain 🧠"
Body: "Safe Harbor uses an AI called Claude to spot scams. You need a free connection key to make it work."
Step by step with numbered large text:
"1. Tap the button below to open the Anthropic website"
"2. Sign in or create a free account"  
"3. Copy your API key"
"4. Come back here and paste it in the box below"
Large "Open Anthropic Website" button (Custom Tab)
Large paste field below
"Test Connection" button — shows ✅ "Connected! Safe Harbor's brain is working" or ❌ friendly error
Note: "Your key is stored securely on this phone only. We never see it."

### Screen 5 — Permissions (one at a time, 4 separate screens):

#### 5a — SMS Permission:
Icon: 💬 large, centred
Heading: "Reading your messages"
Body: "Safe Harbor needs to read your text messages so it can spot scams before you do."
Reassurance: "Safe Harbor never stores or shares your messages. They are checked and immediately forgotten."
Button: "OK, allow messages" → triggers system permission dialog
Skip link: "Not right now" (with warning that SMS Shield won't work)

#### 5b — Phone Permission:
Icon: 📞 large, centred
Heading: "Screening your calls"
Body: "Safe Harbor needs access to your calls so it can warn you about scam callers."
Reassurance: "Safe Harbor never records your calls. It only listens for warning signs."
Button: "OK, allow calls"

#### 5c — Microphone Permission:
Icon: 🎤 large, centred  
Heading: "Your voice"
Body: "Safe Harbor needs your microphone so you can talk to it using your voice instead of typing."
Reassurance: "The microphone is only used when you are actively using Safe Harbor."
Button: "OK, allow microphone"

#### 5d — Notifications Permission:
Icon: 🔔 large, centred
Heading: "Sending you alerts"
Body: "Safe Harbor needs to send you notifications so it can warn you about scams immediately."
Reassurance: "We only send important alerts — we won't spam you."
Button: "OK, allow notifications"

### Screen 6 — Family Contact:
Heading: "Who should Safe Harbor call for help?"
Body: "If Safe Harbor spots something serious, or if you haven't checked in, who should we contact?"
Large input fields: Name + Phone number for up to 3 contacts
"Add another contact" link
Skip link: "I'll do this later" (gentle warning shown)

### Screen 7 — PIN Setup:
(Feeds into the PIN/biometric lock feature — see Feature 16)
Heading: "Let's protect Safe Harbor"
Body: "Set a 4-number PIN so only you can open Safe Harbor"

### Screen 8 — Test Drive:
Heading: "Let's try it out! 🎉"
Body: "Here's a pretend scam message. Let's see Safe Harbor in action."
Show a fake SMS: "URGENT: Your bank account has been suspended. Call 0800-FAKE-NUM immediately."
Animate Safe Harbor detecting it — red alert card appears
Heading: "See? Safe Harbor caught that! 🛡️"
Body: "That's exactly what happens when a real scam arrives."
Button: "I'm ready — take me to Safe Harbor"

## Technical Notes:
- Each permission screen should check if permission already granted and skip automatically
- Store onboarding completion flag in DataStore
- If onboarding is interrupted, resume from last completed step on next launch
- All screens support TalkBack accessibility
- After completing onboarding, mark all PENDING first-run tasks as complete

After implementing, build with ./gradlew assembleDebug, fix all errors, walk through complete onboarding flow on emulator and report any issues.
```

---

### 18. Privacy Promise Screen (PENDING — prompt ready, not yet run)

**Prompt for Claude Code:**
```
Add a Privacy Promise screen to Safe Harbor Security that clearly explains in plain English exactly what the app does and does not do with user data. Work autonomously.

## Entry Points:
- Shown automatically at the END of onboarding (before the test drive screen) as a non-skippable single screen with a checkbox
- Accessible anytime from Settings → "Our Privacy Promise"
- Linked from the API key entry screen in onboarding with small "How is my data used?" link

## The Screen:

### Header:
Large shield icon centred at top
Heading: "Our Privacy Promise to You 🛡️"
Subtext: "Safe Harbor will always be honest about how your information is used."

### Promise Cards (large, clear, icon-led):

Card 1 — ✅ What Safe Harbor sees:
"Safe Harbor reads your text messages and call information to check for scams. This happens on your phone — your messages are sent to the Claude AI service only to be analysed, then immediately forgotten. We do not store your messages."

Card 2 — ✅ Your API key:
"Your Claude AI connection key is stored securely on your phone using military-grade encryption. We never see it, store it on our servers, or share it with anyone."

Card 3 — ✅ Your personal information:
"Your name and phone contacts stay on your phone. We do not upload them to any server. Ever."

Card 4 — ✅ Family alerts:
"When Safe Harbor sends alerts to your family, those messages go directly from your phone via SMS. They do not pass through our servers."

Card 5 — ✅ No advertising. Ever.:
"Safe Harbor does not show ads. We do not sell your data to advertisers. We do not share your information with third parties for marketing. Your data is yours."

Card 6 — ✅ No hidden listening:
"Safe Harbor only uses your microphone when you are actively speaking to it. It does not listen in the background."

Card 7 — ✅ You are in control:
"You can delete all Safe Harbor data at any time from Settings → Delete My Data. This removes everything from your phone immediately."

### Acknowledgement (onboarding version only):
Large checkbox: "I understand and I trust Safe Harbor with my privacy"
Button: "Continue" (only enabled after checkbox ticked)

### Footer (always shown):
Small text: "Questions? Email us at privacy@safeharborsecurity.app"
"View full privacy policy" link (opens simple web page — create a placeholder URL)

## Settings — Delete My Data:
Add "Delete All My Data" option to Settings with:
- Confirmation dialog: "This will remove all your Safe Harbor data from this phone including your settings, alert history, and contacts. Safe Harbor will restart as if newly installed. Are you sure?"
- Two buttons: "Yes, delete everything" and "No, keep my data"
- On confirm: clear all Room tables, clear all DataStore preferences, clear all encrypted storage, restart app to onboarding

## Technical Notes:
- Privacy Promise screen content should be stored as string resources so it can be updated without code changes
- The acknowledgement checkbox state should be stored in DataStore — if user has already acknowledged, show read-only version without checkbox
- "Delete My Data" should use a Room transaction to clear all tables atomically
- After delete, navigate to onboarding start and clear back stack completely

After implementing, build with ./gradlew assembleDebug, fix all errors, verify Delete My Data works correctly on emulator.
```


---

## Updated Full Pending Feature List Summary

| # | Feature | Status |
|---|---------|--------|
| 1 | Listening Shield | PENDING |
| 2 | Stop Silent Listening Button | PENDING |
| 3 | Living Knowledge Base (Remediation Updates) | PENDING |
| 4 | Per-App Remediation Drop-Down | PENDING |
| 5 | Emulator Crash Investigation | PENDING |
| 6 | Is This Safe? Universal Safety Checker | PENDING |
| 7 | Stop Silent Listening Automation Upgrade | PENDING |
| 8 | I Think I Was Scammed Panic Button | PENDING |
| 9 | Daily Safe Check-In | PENDING |
| 10 | Real-Time Payment Warning | PENDING |
| 11 | Gift Card Alarm | PENDING |
| 12 | Safe Contacts Number Lookup | PENDING |
| 13 | Weekly Security Report Card | PENDING |
| 14 | Scam of the Week Notification | PENDING |
| 15 | App Rename to Safe Harbor Security | PENDING |
| 16 | App PIN / Biometric Lock + API Key Encryption | PENDING |
| 17 | Family-Friendly Onboarding | PENDING |
| 18 | Privacy Promise Screen | PENDING |

---

## ⚠️ IMPORTANT REMINDERS — Do After All Features Are Final

---

### REMINDER A: Security Hardening (DO NOT SKIP — Critical)

**Context:** Safe Harbor Security will be a direct threat to scammers, fraud operations, and spyware vendors. This app WILL be targeted. Hardening must be treated as a feature, not an afterthought.

**When to do this:** After all 18 features are complete and stable. Before any public release.

**Prompt for Claude Code (run when ready):**
```
Perform a full security hardening pass on Safe Harbor Security. This app will be targeted by malicious actors. Work autonomously and be thorough.

## 1. Code Obfuscation & Tamper Detection
- Enable R8 full mode obfuscation in release build (proguard-rules.pro)
- Add root detection — if device is rooted, warn user that some protections may be weaker
- Add emulator detection for release builds — warn if running on emulator in production
- Implement tamper detection using PackageManager signature verification — if APK signature doesn't match, refuse to run and alert user
- Add SafetyNet / Play Integrity API check to verify device integrity on first launch

## 2. Network Security
- Add network_security_config.xml enforcing certificate pinning for all Claude API calls
- Pin the Anthropic API certificate — if certificate changes unexpectedly, refuse connection and alert user
- Enforce TLS 1.2+ only — no fallback to older protocols
- Add OkHttp interceptor to verify all outbound requests go only to expected domains (anthropic.com and configured APIs)
- Block all cleartext HTTP traffic in manifest

## 3. Data Security Audit
- Audit ALL DataStore and Room storage — ensure nothing sensitive is stored in plain text
- Verify API key is encrypted via Android Keystore (from Feature 16)
- Ensure Room database is encrypted using SQLCipher
- Add database integrity check on startup — if database is tampered with, reset and re-onboard
- Verify no sensitive data is written to Android logs in release builds — strip all Log.d / Log.v calls
- Check that no sensitive data appears in Android's recent apps screenshot — add FLAG_SECURE to all activities

## 4. Input Validation & Injection Prevention
- Sanitise all user inputs before sending to Claude API
- Validate and sanitise all content received FROM Claude API before rendering in UI
- Prevent prompt injection — if scam content being analysed contains text trying to manipulate Claude's response, strip and flag it
- Add maximum length limits to all text inputs
- Validate all Intents received by the app — reject unexpected or malformed Intents

## 5. Anti-Reverse Engineering
- Detect if app is being run under a debugger in release builds
- Add integrity checks to critical security functions (PIN verification, API key decryption)
- Ensure release builds have debugging disabled in manifest
- Add runtime checks that security-critical classes haven't been replaced or hooked (basic anti-hooking)

## 6. Permissions Audit
- Review ALL declared permissions — remove any not strictly necessary
- Ensure no exported activities, services, or receivers are accessible without explicit permission
- Add android:exported="false" to all components that don't need external access
- Audit BroadcastReceiver registrations — ensure SMS and call receivers validate sender

## 7. Dependency Security
- Run ./gradlew dependencyCheckAnalyze to find known CVEs in dependencies
- Update any dependencies with known vulnerabilities
- Document minimum acceptable versions for all security-critical dependencies

## 8. Penetration Testing Checklist
After hardening, verify resistance to:
- Man-in-the-middle attacks (certificate pinning test)
- API key extraction from APK (obfuscation + keystore test)
- SQL injection via malicious SMS content
- Prompt injection via scam message content
- Intent spoofing attacks
- Backup extraction attack (set android:allowBackup="false")

Report all findings and fixes applied.
```

---

### REMINDER B: Automated Testing Suite (DO AFTER FEATURES STABLE)

**When to do this:** After all 18 features are complete. Before security hardening. A good test suite will catch regressions during hardening.

**Prompt for Claude Code (run when ready):**
```
Create a comprehensive automated test suite for Safe Harbor Security. Work autonomously.

## 1. Unit Tests (JUnit + MockK)

### Claude API & Analysis:
- Test scam detection accuracy with 20+ sample messages:
  - Known scam patterns (bank fraud, Medicare, IRS, gift cards, tech support)
  - Legitimate messages that should NOT be flagged (real bank alerts, family messages)
  - Edge cases (partial matches, foreign language scams)
- Test AI image detection response parsing
- Test URL resolution and un-shortening logic
- Test gift card keyword detection with boundary cases
- Test SafetyCheckResponse JSON parsing with malformed responses

### Data Layer:
- Test all Room DAOs (insert, update, delete, query)
- Test DataStore read/write for all preference keys
- Test RemediationKnowledge upsert logic
- Test weekly stats aggregation formula
- Test database migration between versions

### Security:
- Test PIN hash storage and verification
- Test KeystoreManager encrypt/decrypt round-trip
- Test that plain text API key is never stored
- Test auto-lock timeout logic
- Test tamper detection triggers

### Utilities:
- Test UrlResolverUtil with known redirect chains
- Test ImageAnalysisUtil compression and base64 encoding
- Test family alert message template substitution
- Test safety score calculation formula

## 2. Integration Tests (AndroidX Test + Hilt Testing)

- Test complete SMS scanning pipeline end-to-end with mock Claude API
- Test complete call screening pipeline
- Test privacy monitor scan cycle
- Test remediation sync WorkManager job
- Test check-in notification scheduling and delivery
- Test panic flow data persistence across screens
- Test onboarding completion flag and resume logic

## 3. UI Tests (Compose Testing + Espresso)

### Critical User Journeys:
- Complete onboarding flow start to finish
- PIN setup and unlock flow
- Biometric prompt flow (mock)
- "Is This Safe?" flow with text input
- "Is This Safe?" flow with image from gallery
- "Stop Silent Listening" wizard all steps
- Panic flow all 6 screens
- Daily check-in tap and reset
- Weekly report notification tap to screen
- Privacy Promise acknowledgement
- Delete My Data flow

### Accessibility:
- Verify all interactive elements have content descriptions for TalkBack
- Verify minimum touch target sizes (48dp minimum)
- Verify text contrast ratios meet WCAG AA standard
- Verify large text option doesn't break any layouts

## 4. Security Tests

- Verify FLAG_SECURE prevents screenshots in recent apps
- Verify no sensitive data in logcat output
- Verify database file is encrypted (attempt to open without key)
- Verify app locks after configured timeout
- Verify certificate pinning rejects invalid certificates (use MockWebServer)
- Verify Intent validation rejects spoofed Intents

## 5. Test Data & Fixtures

Create a TestFixtures.kt file with:
- 20 sample scam SMS messages (various types)
- 10 sample legitimate messages
- 5 sample phishing URLs
- 5 sample legitimate URLs
- Mock Claude API responses for each scenario
- Sample RemediationKnowledge records

## 6. CI Configuration

Create a GitHub Actions workflow (.github/workflows/test.yml) that:
- Runs all unit tests on every push
- Runs integration tests on pull requests
- Generates a test coverage report
- Fails the build if coverage drops below 70%
- Posts test results summary to PR comments

## Coverage Targets:
- Overall: 70% minimum
- Security-critical classes (KeystoreManager, PIN verification): 95% minimum
- Claude API parsing: 90% minimum
- UI flows: all critical journeys covered

After creating the test suite, run ./gradlew test and fix any failures. Report final coverage numbers.
```


---

## Complete Build Order (Recommended Sequence for Claude Code)

```
Phase 1 — Foundation (do first):
16 → 17 → 18 → 15

Phase 2 — Core Protection Features:
1 → 2 → 3 → 4 → 5

Phase 3 — Safety Checker:
6 → 7

Phase 4 — Senior Companion Features:
8 → 9 → 10 → 11 → 12 → 13 → 14

Phase 5 — Quality & Stability:
REMINDER B (Automated Testing)

Phase 6 — Pre-Launch Hardening:
REMINDER A (Security Hardening)
```

**Master Claude Code instruction:**
```
Read CLAUDE.md. Implement all PENDING features in the recommended build order shown at the bottom of this file (Phase 1 through 4). Work autonomously. Mark each feature DONE in the summary table as you finish it. Run ./gradlew assembleDebug every 3 features and fix all errors before continuing. Do not start Phase 5 or Phase 6 until explicitly instructed.
```

---

### 19. Camera Permission Guidance + Gallery Image Fix + Chat Agent Fix (PENDING — prompt ready, not yet run)

Found during physical device testing of the "Is This Safe?" feature.

**Bug 1 — Camera Permission No Guidance:**
- Show friendly explanation screen BEFORE triggering system permission dialog
- Handle 3 states: never asked, denied once, permanently denied ("Don't ask again")
- Permanently denied state: show step-by-step instructions + "Open Settings" button pointing to app's own settings page via `Settings.ACTION_APPLICATION_DETAILS_SETTINGS`
- "Use Gallery instead" fallback link on permission denied screen
- On return from Settings (onResume): auto re-check permission and update UI
- Use `shouldShowRequestPermissionRationale()` to distinguish denial states
- Create reusable `PermissionGuidanceCard` composable for future permission needs

**Bug 2a — Gallery Image Not Attaching:**
- Audit `ActivityResultContracts.GetContent()` callback — verify URI not null
- Add `takePersistableUriPermission()` so URI stays accessible after picker closes
- Verify image preview composable observes correct StateFlow and recomposes on URI update
- Add friendly error if URI null or unreadable: "We couldn't open that photo. Please try choosing it again."

**Bug 2b — Chat Agent Giving Wrong Instructions:**
- Chat agent incorrectly tells users to look for a + sign or paperclip to attach images — those controls don't exist
- Fix chat system prompt to remove any suggestion of attachment controls
- Updated system prompt must tell users: "To check a photo, go back to the home screen and tap 'Is This Safe?'"
- Explicitly instruct chat agent: never mention + signs, paperclips, or attachment buttons
- Add a small camera icon shortcut button in the chat UI toolbar that navigates directly to Safety Checker screen

---

## Updated Full Pending Feature List Summary

| # | Feature | Status |
|---|---------|--------|
| 1 | Listening Shield | PENDING |
| 2 | Stop Silent Listening Button | PENDING |
| 3 | Living Knowledge Base (Remediation Updates) | PENDING |
| 4 | Per-App Remediation Drop-Down | PENDING |
| 5 | Emulator Crash Investigation | PENDING |
| 6 | Is This Safe? Universal Safety Checker | PENDING |
| 7 | Stop Silent Listening Automation Upgrade | PENDING |
| 8 | I Think I Was Scammed Panic Button | PENDING |
| 9 | Daily Safe Check-In | PENDING |
| 10 | Real-Time Payment Warning | PENDING |
| 11 | Gift Card Alarm | PENDING |
| 12 | Safe Contacts Number Lookup | PENDING |
| 13 | Weekly Security Report Card | PENDING |
| 14 | Scam of the Week Notification | PENDING |
| 15 | App Rename to Safe Harbor Security | PENDING |
| 16 | App PIN / Biometric Lock + API Key Encryption | PENDING |
| 17 | Family-Friendly Onboarding | PENDING |
| 18 | Privacy Promise Screen | PENDING |
| 19 | Camera Permission Guidance + Gallery Fix + Chat Agent Fix | PENDING |

---

## Complete Build Order (Recommended Sequence for Claude Code)

```
Phase 1 — Foundation (do first):
16 → 17 → 18 → 15

Phase 2 — Core Protection Features:
1 → 2 → 3 → 4 → 5

Phase 3 — Safety Checker + Bug Fixes:
6 → 7 → 19

Phase 4 — Senior Companion Features:
8 → 9 → 10 → 11 → 12 → 13 → 14

Phase 5 — Quality & Stability:
REMINDER B (Automated Testing)

Phase 6 — Pre-Launch Hardening:
REMINDER A (Security Hardening)
```

**Master Claude Code instruction:**
```
Read CLAUDE.md. Implement all PENDING features in the recommended build order shown at the bottom of this file (Phase 1 through 4). Work autonomously. Mark each feature DONE in the summary table as you finish it. Run ./gradlew assembleDebug every 3 features and fix all errors before continuing. Do not start Phase 5 or Phase 6 until explicitly instructed.
```

---

### 20. Icon Update — Shield with Smiley Face (PENDING — prompt ready, not yet run)

Replace current icon with a friendly shield containing a smiley face.

**Design — Shield with Palm Tree / Peace of Mind Theme:**
- Concept: Warm security shield containing a palm tree silhouette — "safe haven", peaceful, protected like a calm beach
- Shield body: warm sky blue gradient (#4A90D9 top to #1A5276 bottom) with navy border (#154360) and subtle white highlight for 3D depth
- Palm tree: sandy brown trunk (#8B6914), lush green fronds (#2ECC71 / #27AE60)
- Ground: small warm sand arc (#F4D03F) at shield base
- Sun: small yellow circle (#F9E04B) with rays in top right of shield
- Background: soft warm gradient light sky blue (#D6EAF8) to white
- Fallback if palm tree unclear at 48dp: anchor symbol OR house+shield overlay
- Style: warm, tropical, peaceful — NOT aggressive or military
- Must be recognisable at 48dp home screen size

**Files to update:**
- `drawable/ic_launcher_foreground.xml` — shield + palm tree vector (viewportWidth/Height 108x108, safe zone 18-90dp)
- `drawable/ic_launcher_background.xml` — soft warm sky gradient
- `mipmap-anydpi-v26/ic_launcher.xml` and `ic_launcher_round.xml` — adaptive icon definitions
- All mipmap PNG sizes (mdpi through xxxhdpi), regular and round variants
- `drawable/ic_notification.xml` — flat single-colour white shield outline only (Android status bar requirement)
- Verify `android:icon` and `android:roundIcon` in AndroidManifest.xml
- Verify `app_name` = "Safe Harbor Security" in strings.xml
- Requires full uninstall + reinstall for icon cache to clear on device
- If icon still shows old version after reinstall: ./gradlew clean && ./gradlew installDebug

---

### 21. SMS Shield Fix — NotificationListenerService (PENDING — prompt ready, not yet run)

**Problem:** Android 10+ blocks READ_SMS and RECEIVE_SMS permissions for non-default SMS apps. No "Allow" option appears for the user. Direct SMS reading approach must be replaced.

**Solution:** Replace SMS BroadcastReceiver with NotificationListenerService which CAN be granted by users and does not require being the default SMS app.

**Part 1 — NotificationListenerService:**
- Create `SafeHarborNotificationListener.kt` in `service/`
- Monitor notifications from known SMS apps: Google Messages, Samsung Messages, AOSP MMS, OnePlus MMS, Sony Conversations, any `CATEGORY_MESSAGE` notification
- Extract sender + content from notification extras (`EXTRA_TITLE`, `EXTRA_TEXT`, `EXTRA_BIG_TEXT`)
- Pass to existing `SmsAnalysisRepository` for Claude analysis
- Post Safe Harbor alert if scam detected
- Update `AndroidManifest.xml` — remove `RECEIVE_SMS`/`READ_SMS`, add `NotificationListenerService` declaration with `BIND_NOTIFICATION_LISTENER_SERVICE` permission

**Part 2 — Permission Guidance Screen:**
Notification listener cannot use runtime permission flow — user must enable manually in Settings.
- Show friendly full-screen guidance when SMS Shield toggled ON without permission
- Step-by-step instructions + "Open Notification Settings" button → `Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS`
- On `onResume`: auto-check via `NotificationManagerCompat.getEnabledListenerPackages()` and update toggle state
- Reassurance text: "Safe Harbor only looks at messages — we never store or share them"

**Part 3 — "Check This Message" Share Target Fallback:**
- Register Safe Harbor as `text/plain` share target (may already exist from Is This Safe?)
- Shared messages route directly to Safety Checker with text pre-loaded
- Add tip on SMS Shield screen explaining how to manually forward suspicious messages

**Part 4 — Update Onboarding:**
- Remove system SMS permission dialog trigger from onboarding screen 5a (won't work anyway)
- Replace with Notification Access guidance flow
- Update explanation text to match new approach

---

## Updated Full Pending Feature List Summary

| # | Feature | Status |
|---|---------|--------|
| 1 | Listening Shield | PENDING |
| 2 | Stop Silent Listening Button | PENDING |
| 3 | Living Knowledge Base (Remediation Updates) | PENDING |
| 4 | Per-App Remediation Drop-Down | PENDING |
| 5 | Emulator Crash Investigation | PENDING |
| 6 | Is This Safe? Universal Safety Checker | PENDING |
| 7 | Stop Silent Listening Automation Upgrade | PENDING |
| 8 | I Think I Was Scammed Panic Button | PENDING |
| 9 | Daily Safe Check-In | PENDING |
| 10 | Real-Time Payment Warning | PENDING |
| 11 | Gift Card Alarm | PENDING |
| 12 | Safe Contacts Number Lookup | PENDING |
| 13 | Weekly Security Report Card | PENDING |
| 14 | Scam of the Week Notification | PENDING |
| 15 | App Rename to Safe Harbor Security | PENDING |
| 16 | App PIN / Biometric Lock + API Key Encryption | PENDING |
| 17 | Family-Friendly Onboarding | PENDING |
| 18 | Privacy Promise Screen | PENDING |
| 19 | Camera Permission Guidance + Gallery Fix + Chat Agent Fix | PENDING |
| 20 | Icon Update — Shield with Smiley Face | PENDING |
| 21 | SMS Shield Fix — NotificationListenerService | PENDING |

---

## Complete Build Order (Recommended Sequence for Claude Code)

```
Phase 1 — Foundation (do first):
16 → 17 → 18 → 15

Phase 2 — Core Protection Features:
1 → 2 → 3 → 4 → 5 → 21

Phase 3 — Safety Checker + Bug Fixes:
6 → 7 → 19

Phase 4 — Senior Companion Features:
8 → 9 → 10 → 11 → 12 → 13 → 14

Phase 5 — Polish:
20 (Icon Update)

Phase 6 — Quality & Stability:
REMINDER B (Automated Testing)

Phase 7 — Pre-Launch Hardening:
REMINDER A (Security Hardening)
```

**Master Claude Code instruction:**
```
Read CLAUDE.md. Implement all PENDING features in the recommended build order shown at the bottom of this file (Phase 1 through 5). Work autonomously. Mark each feature DONE in the summary table as you finish it. Run ./gradlew assembleDebug every 3 features and fix all errors before continuing. Do not start Phase 6 or Phase 7 until explicitly instructed.
```

---

### 22. Fix Photo Gallery Routing Bug — Images Going to Chat Instead of Safety Checker (PENDING — prompt ready, not yet run)

**Root Cause:** Gallery picker result is being incorrectly routed to the ChatScreen/ChatViewModel instead of SafetyCheckerScreen/SafetyCheckerViewModel. The chat interface cannot display or analyse images. This is a fundamental navigation/routing bug.

**Symptoms:**
- User selects photo from gallery
- App returns to chat screen instead of Safety Checker
- Chat generates text message "I picked an image from my gallery to check if it is safe"
- No way to actually attach or analyse the photo
- Chat interface has no image attachment capability and never will

**Correct Flow (must be enforced):**
```
"Is This Safe?" FAB
  → SafetyCheckerScreen (6 input options)
    → Gallery picker launched FROM SafetyCheckerScreen
      → URI returned TO SafetyCheckerScreen
        → Large image preview shown + "Check This Photo 🔍" button
          → Claude Vision API call with base64 image
            → SafetyResultScreen shows verdict
```
Chat screen must NEVER be involved in image checking flow.

**Fixes Required:**
1. Fix navigation graph — gallery result routes to `SafetyCheckerViewModel`, not `ChatViewModel`
2. Remove all image URI handling from `ChatViewModel` and `ChatScreen`
3. Remove chat message template "I picked an image from my gallery..." — incorrect behaviour
4. Update chat system prompt: text-only, if user asks about photos direct them to "Is This Safe?" button, never suggest attachment controls exist in chat
5. Add `_selectedImageUri` StateFlow to `SafetyCheckerViewModel` with `onImageSelected(uri)` method
6. `SafetyCheckerScreen` shows 6 option cards when no image selected, switches to large preview + "Check This Photo" button when URI is set
7. Add `takePersistableUriPermission()` on returned URI
8. Wire `analyseImage()` in `SafetyCheckerRepository` to send base64 image to Claude Vision API
9. Update all navigation entry points — audit every gallery/camera launcher in app, zero should route to `ChatScreen`
10. Add small camera/shield icon button in chat toolbar as escape hatch → navigates to `SafetyCheckerScreen`

**API Call Structure:** Claude Vision API with base64 encoded image + text prompt, model `claude-haiku-4-5-20251001`

---

## Updated Full Pending Feature List Summary

| # | Feature | Status |
|---|---------|--------|
| 1 | Listening Shield | PENDING |
| 2 | Stop Silent Listening Button | PENDING |
| 3 | Living Knowledge Base (Remediation Updates) | PENDING |
| 4 | Per-App Remediation Drop-Down | PENDING |
| 5 | Emulator Crash Investigation | PENDING |
| 6 | Is This Safe? Universal Safety Checker | PENDING |
| 7 | Stop Silent Listening Automation Upgrade | PENDING |
| 8 | I Think I Was Scammed Panic Button | PENDING |
| 9 | Daily Safe Check-In | PENDING |
| 10 | Real-Time Payment Warning | PENDING |
| 11 | Gift Card Alarm | PENDING |
| 12 | Safe Contacts Number Lookup | PENDING |
| 13 | Weekly Security Report Card | PENDING |
| 14 | Scam of the Week Notification | PENDING |
| 15 | App Rename to Safe Harbor Security | PENDING |
| 16 | App PIN / Biometric Lock + API Key Encryption | PENDING |
| 17 | Family-Friendly Onboarding | PENDING |
| 18 | Privacy Promise Screen | PENDING |
| 19 | Camera Permission Guidance + Gallery Fix + Chat Agent Fix | PENDING |
| 20 | Icon Update — Shield with Palm Tree / Peace of Mind Theme | PENDING |
| 21 | SMS Shield Fix — NotificationListenerService | PENDING |
| 22 | Fix Photo Gallery Routing Bug — Images to Chat Instead of Safety Checker | PENDING |

---

## Complete Build Order (Recommended Sequence for Claude Code)

```
Phase 1 — Foundation (do first):
16 → 17 → 18 → 15

Phase 2 — Core Protection Features:
1 → 2 → 3 → 4 → 5 → 21

Phase 3 — Safety Checker + Bug Fixes (do in this order):
22 → 6 → 7 → 19

Phase 4 — Senior Companion Features:
8 → 9 → 10 → 11 → 12 → 13 → 14

Phase 5 — Polish:
20 (Icon Update)

Phase 6 — Quality & Stability:
REMINDER B (Automated Testing)

Phase 7 — Pre-Launch Hardening:
REMINDER A (Security Hardening)
```

Note: Feature 22 moved to the START of Phase 3 — the routing bug must be fixed before building more Safety Checker features on top of a broken foundation.

**Master Claude Code instruction:**
```
Read CLAUDE.md. Implement all PENDING features in the recommended build order shown at the bottom of this file (Phase 1 through 5). Work autonomously. Mark each feature DONE in the summary table as you finish it. Run ./gradlew assembleDebug every 3 features and fix all errors before continuing. Do not start Phase 6 or Phase 7 until explicitly instructed.
```

---

### 23. Fix URL Checker Failures + Keyboard Covering Text Input (PENDING — prompt ready, not yet run)

**Bug 1 — URL Check Always Fails:**
"Is This Safe?" → "Enter a Web Address" returns "I cannot check that right now" for all URLs including valid ones like jw.org.

**Root causes to investigate:**
- URL not being normalised before network call (bare "jw.org" needs "https://" prepended)
- OkHttp exceptions being silently caught and returning generic error
- Network security config may be blocking outbound connections to non-Anthropic domains
- OkHttp timeout too short
- Claude API call may not be executing at all after URL resolution

**URL Normalisation required — must handle:**
- "jw.org" → "https://jw.org"
- "www.jw.org" → "https://www.jw.org"
- "http://jw.org" → keep as-is
- "https://jw.org" → keep as-is
- URLs with paths, spaces, accidental whitespace

**OkHttp fixes:**
- 10 second connect + read timeouts
- followRedirects + followSslRedirects = true
- HEAD request for resolution, separate GET for page title only
- User-Agent header to avoid bot blocking
- Specific exception handling: UnknownHostException, SSLException, SocketTimeoutException, MalformedURLException

**Better error messages (replace all "I cannot check that right now"):**
- No internet: connection check message
- Invalid URL: show example format "www.example.com"
- Site not found: ask user to double-check address
- Timeout: ask user to try again
- Analysis failed: ask user to try again in a moment

**Show resolved URL card before verdict:**
"🔗 We checked: [resolved URL]" + redirect notice if applicable

**Bug 2 — Keyboard Covers Text Input:**
Keyboard appears over URL text field — user cannot see what they are typing.

**Fixes:**
- Wrap screen content in `Column` with `imePadding()` + `verticalScroll(rememberScrollState())`
- Add `WindowCompat.setDecorFitsSystemWindows(window, false)` in Activity
- Auto-scroll to text field on focus with 300ms delay for keyboard animation
- Set `KeyboardType.Uri` (shows .com and / keys) + `ImeAction.Go` (submits URL directly)
- `ImeAction.Go` triggers URL check — no separate button tap needed
- Keyboard auto-hides when analysis starts
- Add 120dp `Spacer` at bottom of screen for scrollable space
- Apply same `imePadding()` fix to ALL other text input screens in app (chat, onboarding, paste text)

---

## Updated Full Pending Feature List Summary

| # | Feature | Status |
|---|---------|--------|
| 1 | Listening Shield | PENDING |
| 2 | Stop Silent Listening Button | PENDING |
| 3 | Living Knowledge Base (Remediation Updates) | PENDING |
| 4 | Per-App Remediation Drop-Down | PENDING |
| 5 | Emulator Crash Investigation | PENDING |
| 6 | Is This Safe? Universal Safety Checker | PENDING |
| 7 | Stop Silent Listening Automation Upgrade | PENDING |
| 8 | I Think I Was Scammed Panic Button | PENDING |
| 9 | Daily Safe Check-In | PENDING |
| 10 | Real-Time Payment Warning | PENDING |
| 11 | Gift Card Alarm | PENDING |
| 12 | Safe Contacts Number Lookup | PENDING |
| 13 | Weekly Security Report Card | PENDING |
| 14 | Scam of the Week Notification | PENDING |
| 15 | App Rename to Safe Harbor Security | PENDING |
| 16 | App PIN / Biometric Lock + API Key Encryption | PENDING |
| 17 | Family-Friendly Onboarding | PENDING |
| 18 | Privacy Promise Screen | PENDING |
| 19 | Camera Permission Guidance + Gallery Fix + Chat Agent Fix | PENDING |
| 20 | Icon Update — Shield with Palm Tree / Peace of Mind Theme | PENDING |
| 21 | SMS Shield Fix — NotificationListenerService | PENDING |
| 22 | Fix Photo Gallery Routing Bug — Images to Chat Instead of Safety Checker | PENDING |
| 23 | Fix URL Checker Failures + Keyboard Covering Text Input | PENDING |

---

## Complete Build Order (Recommended Sequence for Claude Code)

```
Phase 1 — Foundation (do first):
16 → 17 → 18 → 15

Phase 2 — Core Protection Features:
1 → 2 → 3 → 4 → 5 → 21

Phase 3 — Safety Checker + Bug Fixes (do in this order):
22 → 23 → 6 → 7 → 19

Phase 4 — Senior Companion Features:
8 → 9 → 10 → 11 → 12 → 13 → 14

Phase 5 — Polish:
20 (Icon Update)

Phase 6 — Quality & Stability:
REMINDER B (Automated Testing)

Phase 7 — Pre-Launch Hardening:
REMINDER A (Security Hardening)
```

Note: Features 22 and 23 are both bug fixes to the Safety Checker and must be done BEFORE features 6 and 7 which build on top of it.

**Master Claude Code instruction:**
```
Read CLAUDE.md. Implement all PENDING features in the recommended build order shown at the bottom of this file (Phase 1 through 5). Work autonomously. Mark each feature DONE in the summary table as you finish it. Run ./gradlew assembleDebug every 3 features and fix all errors before continuing. Do not start Phase 6 or Phase 7 until explicitly instructed.
```

---

### 24. Camera Permission Fix + AI Image Detection + URL Checker Fix + Keyboard/Button Fix (PENDING — prompt ready, not yet run)

Found during physical device testing. Four related issues in the Safety Checker feature.

**Issue 1 — Camera Permission Not in Android Settings:**
- `CAMERA` permission and `uses-feature android:required="false"` missing or incorrect in AndroidManifest.xml
- Must uninstall and reinstall app completely after manifest fix for permission to appear in Android Settings
- Runtime permission request via `ActivityResultContracts.RequestPermission()` must be triggered on first "Take a Photo" tap

**Issue 2 — AI Image Generation Detection Not Shown:**
- Update image analysis prompt sent to Claude to explicitly require AI detection JSON fields
- New `AIImageAnalysis` data class: `isLikelyAiGenerated`, `confidence`, `indicators`, `explanation`
- Updated `SafetyCheckResponse` includes `aiImageAnalysis`, `imageContent`, `containsText`, `textContent`, `containsQrCode`, `qrDestination`
- Add `AIImageAnalysisCard` composable to `SafetyResultScreen`:
  - ⚠️ "This image may have been created by a computer" (amber) if AI detected
  - ✅ "This looks like a real photograph" (green) if not AI
  - Confidence percentage + bullet list of specific indicators
  - Plain English explanation suitable for elderly users
- AI detection checks: distorted hands/fingers/faces, inconsistent lighting, background incoherence, garbled text in image, watermarks, plastic-looking skin, unnatural eyes, distorted geometry

**Issue 3 — URL Checker Fails Silently:**
- Add debug logging at every pipeline step to identify exact failure point
- Fix network_security_config.xml if blocking outbound connections to non-Anthropic domains
- OkHttp: 15s timeouts, followRedirects + followSslRedirects = true, retryOnConnectionFailure = true
- URL normalisation: always prepend "https://" to bare domains
- Ensure URL check runs on `Dispatchers.IO` not main thread
- Screen must NEVER close on error — show error inline below text field
- Show "Checking [url]... please wait" while running

**Issue 4 — "Check It" Button Hidden Behind Keyboard:**
- Outer `Box` with `imePadding()` — pushes content up when keyboard appears
- Inner `Column` with `verticalScroll()` + `padding(bottom = 200.dp)`
- `Spacer(height = 200.dp)` at end of column content
- Auto-scroll to bottom on text field focus (400ms delay for keyboard animation)
- `KeyboardType.Uri` (shows .com and / keys) + `ImeAction.Go` triggers check directly
- Keyboard hides automatically when Check It tapped or Go pressed
- Apply same pattern to ALL text input screens: URL input, paste text, chat, onboarding fields, settings fields

---

## Updated Full Pending Feature List Summary

| # | Feature | Status |
|---|---------|--------|
| 1 | Listening Shield | PENDING |
| 2 | Stop Silent Listening Button | PENDING |
| 3 | Living Knowledge Base (Remediation Updates) | PENDING |
| 4 | Per-App Remediation Drop-Down | PENDING |
| 5 | Emulator Crash Investigation | PENDING |
| 6 | Is This Safe? Universal Safety Checker | PENDING |
| 7 | Stop Silent Listening Automation Upgrade | PENDING |
| 8 | I Think I Was Scammed Panic Button | PENDING |
| 9 | Daily Safe Check-In | PENDING |
| 10 | Real-Time Payment Warning | PENDING |
| 11 | Gift Card Alarm | PENDING |
| 12 | Safe Contacts Number Lookup | PENDING |
| 13 | Weekly Security Report Card | PENDING |
| 14 | Scam of the Week Notification | PENDING |
| 15 | App Rename to Safe Harbor Security | PENDING |
| 16 | App PIN / Biometric Lock + API Key Encryption | PENDING |
| 17 | Family-Friendly Onboarding | PENDING |
| 18 | Privacy Promise Screen | PENDING |
| 19 | Camera Permission Guidance + Gallery Fix + Chat Agent Fix | PENDING |
| 20 | Icon Update — Shield with Palm Tree / Peace of Mind Theme | PENDING |
| 21 | SMS Shield Fix — NotificationListenerService | PENDING |
| 22 | Fix Photo Gallery Routing Bug — Images to Chat Instead of Safety Checker | PENDING |
| 23 | Fix URL Checker Failures + Keyboard Covering Text Input | PENDING |
| 24 | Camera Permission Fix + AI Image Detection + URL Checker Fix + Keyboard/Button Fix | PENDING |

---

## Complete Build Order (Recommended Sequence for Claude Code)

```
Phase 1 — Foundation (do first):
16 → 17 → 18 → 15

Phase 2 — Core Protection Features:
1 → 2 → 3 → 4 → 5 → 21

Phase 3 — Safety Checker + Bug Fixes (do in this order):
22 → 23 → 24 → 6 → 7 → 19

Phase 4 — Senior Companion Features:
8 → 9 → 10 → 11 → 12 → 13 → 14

Phase 5 — Polish:
20 (Icon Update)

Phase 6 — Quality & Stability:
REMINDER B (Automated Testing)

Phase 7 — Pre-Launch Hardening:
REMINDER A (Security Hardening)
```

Note: Features 22, 23, and 24 are all Safety Checker bug fixes and must be
completed BEFORE features 6 and 7 which build further on top of that feature.
Require full uninstall and reinstall after feature 24 for camera permission fix.

**Master Claude Code instruction:**
```
Read CLAUDE.md. Implement all PENDING features in the recommended build order
shown at the bottom of this file (Phase 1 through 5). Work autonomously. Mark
each feature DONE in the summary table as you finish it. Run ./gradlew
assembleDebug every 3 features and fix all errors before continuing. After
completing feature 24 do a full uninstall and reinstall on device using
./gradlew installDebug to ensure camera permission registers correctly in
Android Settings. Do not start Phase 6 or Phase 7 until explicitly instructed.
```

---

### 25. Email Scanning + Messages/Email Tabs + Status Indicators (PENDING — prompt ready, not yet run)

Email is completely missing from the app despite being one of the most common
scam vectors targeting elderly users.

**Approach (3 methods, notification listener is primary):**
1. NotificationListenerService — automatic, works across all email apps (primary)
2. Share Sheet — user forwards suspicious email manually (universal fallback)
3. Gmail API — direct integration (deferred to v2)

**Part 1 — Messages Screen Tab Bar:**
- Add TabRow with two tabs: 📱 Text Messages (existing) + 📧 Emails (new)
- Large text, high contrast, easy to tap
- Email tab empty state explains how to use Share Sheet to forward emails

**Part 2 — Email via NotificationListenerService:**
Extend `SafeHarborNotificationListener.kt` to detect email app notifications:
- Monitored packages: Gmail, Outlook, Samsung Email, Yahoo Mail, AOSP Email,
  BlueMail, Fastmail, Thunderbird, Zoho Mail, Tutanota
- Also catch any notification with `category == Notification.CATEGORY_EMAIL`
- Extract: sender (EXTRA_TITLE), subject (EXTRA_SUB_TEXT), preview
  (EXTRA_BIG_TEXT or EXTRA_TEXT)
- Only analyse if preview > 10 chars or subject not blank
- Note: notification listener sees preview only, not full body — prompt to
  Claude accounts for this

**Part 3 — EmailScanRepository:**
New `EmailScanRepository.kt` in `data/repository/`. Claude analysis prompt
checks for: fake bank/PayPal/Amazon alerts, IRS/Medicare/SSA impersonation,
fake prizes, urgency tactics, personal info requests, gift card requests,
suspicious sender domains, mismatched sender name vs address.

JSON response fields: `verdict`, `confidence`, `summary`, `explanation`,
`red_flags`, `recommended_action`, `scam_type`, `sender_suspicious`,
`sender_reason`, `subject_suspicious`, `urgency_detected`

**Part 4 — Status Indicators on ALL List Items (email AND messages):**
Each list item shows large status dot (min 24dp) on LEFT side:
- 🟢 ✓ "Looks Safe" — green
- 🟡 ⚠ "Be Careful" — amber
- 🔴 ✗ "Likely Scam" — red
- ⚪ ⟳ "Checking..." — grey + spinner
- ⚫ ? "Not checked" — dark grey

List item layout: colour dot + sender + subject/preview + one-line summary +
timestamp + Details button. Tapping opens detail screen with full verdict card,
red flags, recommended action, "Forward to family", "Mark as safe", "Delete" buttons.

**Part 5 — Share Sheet Manual Check:**
Accept MIME types: `text/plain`, `text/html`, `message/rfc822`
Extract sender/subject/body → `EmailScanRepository.scanEmail()` → show result
immediately. Tip shown on email tab: forward any email via Share → Safe Harbor.

**Part 6 — Home Screen Unread Badges:**
Dashboard cards for Messages and Calls show red badge with count of unread
DANGEROUS/SUSPICIOUS items. Green/no badge = all clear. Updates in real time.

**Part 7 — Notifications:**
- DANGEROUS email → immediate high-priority notification with "View Details" action
- SUSPICIOUS email → normal priority notification
- SAFE emails → no notification (avoid spamming user)

**Part 8 — Room Database:**
New `EmailScanResult` entity + `EmailScanDao`. Increment DB version + migration.
DAO methods: insert, getAllEmailScans (Flow), getUnreadCount (Flow),
getByVerdict, markAsRead, deleteEmailScan.

**New files:**
- `data/local/EmailScanResult.kt`
- `data/local/EmailScanDao.kt`
- `data/repository/EmailScanRepository.kt`
- `ui/messages/EmailScanScreen.kt`
- `ui/messages/EmailDetailScreen.kt`
- `ui/messages/MessageStatusIndicator.kt` (reusable composable)

**Updated files:**
- `service/SafeHarborNotificationListener.kt` — add email detection
- `ui/messages/MessagesScreen.kt` — add TabRow
- `ui/home/HomeScreen.kt` — add unread badges
- `data/local/AppDatabase.kt` — new entity + migration

---

## Updated Full Pending Feature List Summary

| # | Feature | Status |
|---|---------|--------|
| 1 | Listening Shield | PENDING |
| 2 | Stop Silent Listening Button | PENDING |
| 3 | Living Knowledge Base (Remediation Updates) | PENDING |
| 4 | Per-App Remediation Drop-Down | PENDING |
| 5 | Emulator Crash Investigation | PENDING |
| 6 | Is This Safe? Universal Safety Checker | PENDING |
| 7 | Stop Silent Listening Automation Upgrade | PENDING |
| 8 | I Think I Was Scammed Panic Button | PENDING |
| 9 | Daily Safe Check-In | PENDING |
| 10 | Real-Time Payment Warning | PENDING |
| 11 | Gift Card Alarm | PENDING |
| 12 | Safe Contacts Number Lookup | PENDING |
| 13 | Weekly Security Report Card | PENDING |
| 14 | Scam of the Week Notification | PENDING |
| 15 | App Rename to Safe Harbor Security | PENDING |
| 16 | App PIN / Biometric Lock + API Key Encryption | PENDING |
| 17 | Family-Friendly Onboarding | PENDING |
| 18 | Privacy Promise Screen | PENDING |
| 19 | Camera Permission Guidance + Gallery Fix + Chat Agent Fix | PENDING |
| 20 | Icon Update — Shield with Palm Tree / Peace of Mind Theme | PENDING |
| 21 | SMS Shield Fix — NotificationListenerService | PENDING |
| 22 | Fix Photo Gallery Routing Bug — Images to Chat Instead of Safety Checker | PENDING |
| 23 | Fix URL Checker Failures + Keyboard Covering Text Input | PENDING |
| 24 | Camera Permission Fix + AI Image Detection + URL Checker Fix + Keyboard/Button Fix | PENDING |
| 25 | Email Scanning + Messages/Email Tabs + Status Indicators | PENDING |

---

## Complete Build Order (Recommended Sequence for Claude Code)

```
Phase 1 — Foundation (do first):
16 → 17 → 18 → 15

Phase 2 — Core Protection Features:
1 → 2 → 3 → 4 → 5 → 21 → 25

Phase 3 — Safety Checker + Bug Fixes (do in this order):
22 → 23 → 24 → 6 → 7 → 19

Phase 4 — Senior Companion Features:
8 → 9 → 10 → 11 → 12 → 13 → 14

Phase 5 — Polish:
20 (Icon Update)

Phase 6 — Quality & Stability:
REMINDER B (Automated Testing)

Phase 7 — Pre-Launch Hardening:
REMINDER A (Security Hardening)
```

Note: Feature 25 added to Phase 2 alongside feature 21 (NotificationListenerService)
since email scanning extends the same service. Build both together for efficiency.

**Master Claude Code instruction:**
```
Read CLAUDE.md. Implement all PENDING features in the recommended build order
shown at the bottom of this file (Phase 1 through 5). Work autonomously. Mark
each feature DONE in the summary table as you finish it. Run ./gradlew
assembleDebug every 3 features and fix all errors before continuing. After
completing feature 24 do a full uninstall and reinstall on device using
./gradlew installDebug to ensure camera permission registers correctly in
Android Settings. Do not start Phase 6 or Phase 7 until explicitly instructed.
```

---

### 26. Enhanced Privacy Monitor — Camera, Screen & All Silent Access Risks (PENDING — prompt ready, not yet run)

Extend Listening Shield to detect ALL forms of silent device access.

**New detections:**
- **Camera access** — AppOpsManager `OP_CAMERA`, background camera use, Android 12+ SensorPrivacyManager
- **Screen recording** — MediaProjectionManager active projections, `CAPTURE_VIDEO_OUTPUT` permission, `SYSTEM_ALERT_WINDOW` overlay apps
- **Background location** — AppOpsManager `OP_FINE_LOCATION`/`OP_COARSE_LOCATION`, `ACCESS_BACKGROUND_LOCATION`
- **Clipboard monitoring** — educational note on Android 12+ protection vs older versions
- **Contacts & call log** — AppOpsManager `OP_READ_CONTACTS`, `OP_READ_CALL_LOG` background access
- **Overlay apps** — all apps with `SYSTEM_ALERT_WINDOW` permission, flag unknown ones
- **Device admin apps** — DevicePolicyManager active admins, flag non-MDM/non-security tools with plain English warning
- **VPN apps** — ConnectivityManager active VPN check, flag unknown VPNs

**Updated Privacy Monitor screen — grouped sections:**
- 🎤 Microphone Access
- 📷 Camera Access
- 🖥️ Screen Access
- 📍 Location Access
- 📋 Clipboard & Data Access
- 🔐 Device Control
- 🌐 Network & VPN

Each section: green "All clear" or list of flagged apps with risk cards, plain English descriptions, risk level (HIGH/MEDIUM/INFO), remediation deep-links.

---

### 27. Home Screen Widget (PENDING — prompt ready, not yet run)

Android App Widget (2x1 or 2x2) for device home screen.

**Widget layout:**
```
┌─────────────────────────────┐
│  🛡️ Safe Harbor             │
│  ● Protected               │
│  [  On / Off  ] [ 🎤 Talk ] │
└─────────────────────────────┘
```

**Components:**
- Shield status toggle — turns all shields on/off simultaneously
- Status text: "Protected ✅" / "⚠️ 2 alerts" / "Paused ⏸️"
- Push to Talk 🎤 button — opens app to voice input screen
- Updates in real time via RemoteViews

**New files:**
- `app/src/main/res/layout/widget_safe_harbor.xml` — RemoteViews layout
- `app/src/main/res/xml/widget_info.xml` — AppWidgetProviderInfo (minWidth 180dp, minHeight 110dp, updatePeriodMillis 1800000)
- `widget/SafeHarborWidget.kt` — extends AppWidgetProvider, handles ACTION_TOGGLE_SHIELDS and ACTION_PUSH_TO_TALK

**Manifest:** `<receiver>` declaration with `android.appwidget.action.APPWIDGET_UPDATE` intent filter.
`updateAllWidgets()` called from PrivacyMonitorService, SmsAnalysisRepository, anywhere shield state changes.

---

### 28. Voice Activation & Voice-Controlled Safety Checks (PENDING — prompt ready, not yet run)

Hands-free voice control for elderly users who struggle with typing.

**Approach (battery-friendly, no always-on wake word):**
1. Widget 🎤 Push to Talk button (primary — one physical tap)
2. Large 🎤 "Talk to Safe Harbor" button on home screen (secondary)
3. Android App Actions placeholder for future Google Assistant integration (v2)

**Voice Recognition:** Android built-in `SpeechRecognizer` + `RecognizerIntent.ACTION_RECOGNIZE_SPEECH` (uses existing RECORD_AUDIO permission)

**Voice Intent Recognition via Claude:**
Spoken text sent to Claude to identify intent:
- `check_url` — "Is jw.org safe"
- `check_browser` — "Does this website look safe" (reads URL from active browser)
- `check_text` — "Check this message"
- `check_email` — "I got a strange email"
- `toggle_shields` — "Turn on Safe Harbor"
- `open_chat` — open chat screen
- `unknown` — ask for clarification

**"check_browser" intent — Read URL from Chrome/Firefox:**
Uses AccessibilityService to find URL bar node:
- `com.android.chrome:id/url_bar`
- `com.google.android.apps.chrome:id/url_bar`
- `org.mozilla.firefox:id/mozac_browser_toolbar_url_view`
- `com.microsoft.emmx:id/url_bar`
→ Passes found URL to `SafetyCheckerRepository.checkUrl()`

**Voice UI overlay:**
- Large animated waveform while listening
- "Safe Harbor is listening... 👂"
- "I heard: '[text]'" confirmation
- Claude's `response_to_user` friendly confirmation text
- "Try again" button
- Calm, friendly, never clinical

**New class:** `VoiceInputManager.kt` in `util/`
**New composable:** `VoiceListeningOverlay.kt` in `ui/voice/`
**Home screen addition:** Large 🎤 "Talk to Safe Harbor" button below "Is This Safe?" FAB

---

### 29. URL Checker Complete Rewrite + Persistent Bug Fix (PENDING — prompt ready, not yet run)

URL checker has been reported broken three times. Complete rewrite from scratch — do NOT patch existing code, delete and replace.

**Root cause investigation:**
- Verify `INTERNET` + `ACCESS_NETWORK_STATE` permissions in manifest
- Verify entire pipeline runs on `Dispatchers.IO` (NetworkOnMainThreadException causes silent failures)
- Check network_security_config.xml not blocking non-Anthropic domains

**Clean rewrite pipeline:**
1. `normaliseUrl()` — prepend `https://` to bare domains, trim whitespace
2. `resolveUrl()` — OkHttp HEAD request, 10s timeouts, followRedirects=true, User-Agent header, runs on `Dispatchers.IO`. Resolution failure does NOT block analysis — falls back to original URL
3. Claude analysis — always runs even if resolution failed
4. Parse response — log full Claude response for debugging

**Claude URL analysis prompt includes:**
- Note that jw.org is the official Jehovah's Witnesses website and is completely safe
- Domain legitimacy check, known safe site flag, category classification
- JSON fields: `verdict`, `confidence`, `summary`, `explanation`, `red_flags`, `recommended_action`, `domain_looks_legitimate`, `is_known_safe_site`, `category`

**Test URLs to verify fix (log full response for each):**
- "jw.org", "https://jw.org", "google.com", "anthropic.com", "a-suspicious-looking-bank-login.tk"

**UI rules:**
- Screen NEVER closes or navigates away on error — show error inline
- User can edit URL and retry without retyping
- Show "Checking [url]..." while running

---

## Updated Full Pending Feature List Summary

| # | Feature | Status |
|---|---------|--------|
| 1 | Listening Shield | PENDING |
| 2 | Stop Silent Listening Button | PENDING |
| 3 | Living Knowledge Base (Remediation Updates) | PENDING |
| 4 | Per-App Remediation Drop-Down | PENDING |
| 5 | Emulator Crash Investigation | PENDING |
| 6 | Is This Safe? Universal Safety Checker | PENDING |
| 7 | Stop Silent Listening Automation Upgrade | PENDING |
| 8 | I Think I Was Scammed Panic Button | PENDING |
| 9 | Daily Safe Check-In | PENDING |
| 10 | Real-Time Payment Warning | PENDING |
| 11 | Gift Card Alarm | PENDING |
| 12 | Safe Contacts Number Lookup | PENDING |
| 13 | Weekly Security Report Card | PENDING |
| 14 | Scam of the Week Notification | PENDING |
| 15 | App Rename to Safe Harbor Security | PENDING |
| 16 | App PIN / Biometric Lock + API Key Encryption | PENDING |
| 17 | Family-Friendly Onboarding | PENDING |
| 18 | Privacy Promise Screen | PENDING |
| 19 | Camera Permission Guidance + Gallery Fix + Chat Agent Fix | PENDING |
| 20 | Icon Update — Shield with Palm Tree / Peace of Mind Theme | PENDING |
| 21 | SMS Shield Fix — NotificationListenerService | PENDING |
| 22 | Fix Photo Gallery Routing Bug — Images to Chat Instead of Safety Checker | PENDING |
| 23 | Fix URL Checker Failures + Keyboard Covering Text Input | PENDING |
| 24 | Camera Permission Fix + AI Image Detection + URL Checker Fix + Keyboard/Button Fix | PENDING |
| 25 | Email Scanning + Messages/Email Tabs + Status Indicators | PENDING |
| 26 | Enhanced Privacy Monitor — Camera, Screen & All Silent Access Risks | PENDING |
| 27 | Home Screen Widget | PENDING |
| 28 | Voice Activation & Voice-Controlled Safety Checks | PENDING |
| 29 | URL Checker Complete Rewrite + Persistent Bug Fix | PENDING |

---

## Complete Build Order (Recommended Sequence for Claude Code)

```
Phase 1 — Foundation (do first):
16 → 17 → 18 → 15

Phase 2 — Core Protection Features:
1 → 26 → 2 → 3 → 4 → 5 → 21 → 25

Phase 3 — Safety Checker + Bug Fixes (do in this order):
29 → 22 → 23 → 24 → 6 → 7 → 19

Phase 4 — Senior Companion Features:
8 → 9 → 10 → 11 → 12 → 13 → 14

Phase 5 — Voice & Widget:
27 → 28

Phase 6 — Polish:
20 (Icon Update)

Phase 7 — Quality & Stability:
REMINDER B (Automated Testing)

Phase 8 — Pre-Launch Hardening:
REMINDER A (Security Hardening)
```

Notes:
- Feature 26 moved to start of Phase 2 — extends existing Listening Shield
- Feature 29 moved to START of Phase 3 — URL bug must be fixed before 
  building more Safety Checker features on top of broken foundation
- Features 27 and 28 in their own Phase 5 — widget and voice depend on 
  stable shields and safety checker being working first

**Master Claude Code instruction:**
```
Read CLAUDE.md. Implement all PENDING features in the recommended build 
order shown at the bottom of this file (Phase 1 through 6). Work 
autonomously. Mark each feature DONE in the summary table as you finish 
it. Run ./gradlew assembleDebug every 3 features and fix all errors before 
continuing. After completing feature 24 do a full uninstall and reinstall 
on device using ./gradlew installDebug to ensure camera permission registers 
correctly in Android Settings. Do not start Phase 7 or Phase 8 until 
explicitly instructed.
```

---

### Fix 1 — Text Messages Not Flowing Into App (PENDING — prompt ready, not yet run)

**Root causes to investigate:**
- NotificationListenerService not enabled or not correctly detecting SMS notifications
- No visible feedback to user when notification access not granted

**Fixes:**
- On app launch and SMS Shield toggle: check `NotificationManagerCompat.getEnabledListenerPackages()` — show guidance screen immediately if not granted, never silently fail
- Add service ping mechanism: write timestamp to DataStore when service starts, show "✅ Message scanning active" or "⚠️ Message scanning not running — tap to fix" on Messages screen
- Comprehensive SMS package list: Google Messages, Samsung Messages, AOSP, OnePlus, Sony, Huawei, Verizon, AT&T, carrier messages + catch by `CATEGORY_MESSAGE` / `CATEGORY_SMS`
- Add "Scanning Status" section to Settings: notification access status, last SMS/email scanned timestamp, total scanned today, "Send test message" button
- Persistent amber banner on Messages screen if access not granted: "Safe Harbor cannot scan your messages yet. Tap here to enable scanning."

---

### Fix 2 — Rename "SMS" to "Messages" on Home Screen (PENDING — prompt ready, not yet run)

Simple label change throughout app.
- Replace all user-facing "SMS Shield", "SMS Protection", "SMS" labels with "Messages Shield", "Messages Protection", "Messages"
- Files: `ui/home/HomeScreen.kt`, `res/values/strings.xml`, shield toggle card composables
- Keep "SMS" only in internal/technical code — zero user-facing "SMS" text

---

### 30. Email Account Setup & Inbox Scanning (PENDING — prompt ready, not yet run)

Notification listener only sees new email previews as they arrive. OAuth integration required for inbox scanning.

**Supported providers:**
- Gmail — Google Sign-In OAuth + Gmail API (primary)
- Outlook/Hotmail — MSAL OAuth
- Yahoo Mail — Yahoo OAuth
- Other — manual IMAP (encrypted credentials via Keystore)

**Gmail API scanning:**
- Dependencies: `google-api-client-android`, `google-api-services-gmail`, `play-services-auth`
- On first connect: scan last 50 emails (`is:unread newer_than:7d`)
- Subsequently: scan new emails as they arrive via notification listener

**Multiple accounts:**
- `EmailAccount` Room entity: id (email address), displayName, provider, isActive, lastSyncTime, authToken (Keystore encrypted)
- Up to 3 email accounts
- Messages screen: tab or dropdown to switch between accounts
- Each email shows 🟢🟡🔴 status indicator from scan result

**Email tab:** shows all scanned emails grouped by account, unscanned show ⚪ "Checking..." spinner

---

### 31. Social Media Scam Detection (PENDING — prompt ready, not yet run)

Facebook Marketplace, Craigslist, WhatsApp, NextDoor scam detection.

**Detection methods:**

**Method 1 — Share Sheet (primary):**
Extend existing share target to handle social app content. Add social context to Claude prompt based on sharing app package (Facebook, Craigslist, WhatsApp, NextDoor, etc.)

**Method 2 — Notification monitoring (secondary):**
Extend `SafeHarborNotificationListener` for social packages: Facebook, Facebook Lite, Messenger, WhatsApp, WhatsApp Business, NextDoor, Instagram, Twitter/X, Telegram. Use lower alert threshold — only alert HIGH confidence DANGEROUS verdicts.

**Method 3 — Social scam knowledge base in Claude prompt:**
- Facebook Marketplace: overpayment, shipping scams, fake escrow, too-good pricing, off-platform requests, fake payment screenshots
- Craigslist: advance fee, rental scams, job scams, fake tickets, off-platform requests
- WhatsApp: "Hi Mum/Dad" family impersonation, crypto groups, fake prizes, chain messages, fake jobs
- General: romance scams, fake charities, political donation scams, fake giveaways

**New UI tab:** 📱 Texts | 📧 Emails | 👥 Social on Messages screen
- Platform icon + scam type + 🟢🟡🔴 status indicators
- Tip: "Press and hold any suspicious post → Share → Safe Harbor"

---

### 32. WiFi Security Monitor (PENDING — prompt ready, not yet run)

Alert seniors when connected to dangerous or unsecured WiFi.

**Detections:**
- Open/unsecured network (no WPA encryption)
- WEP encryption (easily hacked)
- Suspicious/evil twin network names (free wifi, public wifi, default router names, look-alike names)
- Network change detection via `WifiManager.NETWORK_STATE_CHANGED_ACTION` BroadcastReceiver — auto-check on each new connection

**Home screen WiFi status indicator:**
- 🟢 "WiFi Secure" — encrypted or trusted network
- 🟡 "WiFi — Be Careful" — public/open network
- 🔴 "WiFi Unsafe" — no encryption or suspicious name
- ⚫ "Not on WiFi" — mobile data (safer)

**WiFi detail screen:** network name, security type, recommendation, "Mark as trusted" button, public WiFi tips. Trusted networks stored in DataStore — no warnings shown.

**On insecure network:** suggest avoiding banking/passwords — no specific VPN product recommended.

**New permissions:** `ACCESS_WIFI_STATE`, `ACCESS_NETWORK_STATE`, `CHANGE_NETWORK_STATE`

**New files:**
- `data/repository/WifiSecurityRepository.kt`
- `receiver/WifiStateReceiver.kt`
- `ui/home/WifiStatusCard.kt`
- `ui/wifi/WifiDetailScreen.kt`

---

### 33. "Connect Additional Security" — Third-Party Services (PENDING — prompt ready, not yet run)

"Connect Additional Security" section in Settings screen.

**Tier 1 — Deep-link integrations (build now):**
- **HaveIBeenPwned** — FULLY INTEGRATED (free API): check user email(s) against known data breaches, `GET /api/v3/breachedaccount/{email}`, weekly WorkManager check, plain English breach explanations, store results in Room. Optional API key field in Settings with link to get free key.
- **LifeLock** — deep-link + description card ("Identity theft protection")
- **Experian** — deep-link + description ("Credit monitoring & dark web alerts")
- **Credit Karma** — deep-link, note free tier ("Free credit monitoring — highly recommended")
- **Google Dark Web Report** — deep-link to myaccount.google.com/security ("Free with your Google account")

**Tier 2 — Future API placeholders (architecture only):**
`ConnectedService` Room entity stub (serviceId, serviceName, isConnected, lastSyncTime, authToken encrypted). Comments: `// TODO: Implement [ServiceName] API when commercial agreement in place`

**Screen layout:** list of service cards showing connected ✅ or not connected ○, last check time, action button (Check Again / Learn More / Open in Google)

**HaveIBeenPwned breach plain English:**
"⚠️ Your email was found in a data leak from [Company] in [Year]. We recommend changing your password for that service."

---

## Updated Full Pending Feature List Summary

| # | Feature | Status |
|---|---------|--------|
| 1 | Listening Shield | PENDING |
| 2 | Stop Silent Listening Button | PENDING |
| 3 | Living Knowledge Base (Remediation Updates) | PENDING |
| 4 | Per-App Remediation Drop-Down | PENDING |
| 5 | Emulator Crash Investigation | PENDING |
| 6 | Is This Safe? Universal Safety Checker | PENDING |
| 7 | Stop Silent Listening Automation Upgrade | PENDING |
| 8 | I Think I Was Scammed Panic Button | PENDING |
| 9 | Daily Safe Check-In | PENDING |
| 10 | Real-Time Payment Warning | PENDING |
| 11 | Gift Card Alarm | PENDING |
| 12 | Safe Contacts Number Lookup | PENDING |
| 13 | Weekly Security Report Card | PENDING |
| 14 | Scam of the Week Notification | PENDING |
| 15 | App Rename to Safe Harbor Security | PENDING |
| 16 | App PIN / Biometric Lock + API Key Encryption | PENDING |
| 17 | Family-Friendly Onboarding | PENDING |
| 18 | Privacy Promise Screen | PENDING |
| 19 | Camera Permission Guidance + Gallery Fix + Chat Agent Fix | PENDING |
| 20 | Icon Update — Shield with Palm Tree / Peace of Mind Theme | PENDING |
| 21 | SMS Shield Fix — NotificationListenerService | PENDING |
| 22 | Fix Photo Gallery Routing Bug — Images to Chat Instead of Safety Checker | PENDING |
| 23 | Fix URL Checker Failures + Keyboard Covering Text Input | PENDING |
| 24 | Camera Permission Fix + AI Image Detection + URL Checker Fix + Keyboard/Button Fix | PENDING |
| 25 | Email Scanning + Messages/Email Tabs + Status Indicators | PENDING |
| 26 | Enhanced Privacy Monitor — Camera, Screen & All Silent Access Risks | PENDING |
| 27 | Home Screen Widget | PENDING |
| 28 | Voice Activation & Voice-Controlled Safety Checks | PENDING |
| 29 | URL Checker Complete Rewrite + Persistent Bug Fix | PENDING |
| Fix 1 | Text Messages Not Flowing Into App | PENDING |
| Fix 2 | Rename SMS to Messages on Home Screen | PENDING |
| 30 | Email Account Setup & Inbox Scanning | PENDING |
| 31 | Social Media Scam Detection | PENDING |
| 32 | WiFi Security Monitor | PENDING |
| 33 | Connect Additional Security — Third-Party Services | PENDING |

---

## Complete Build Order (Recommended Sequence for Claude Code)

```
Phase 1 — Foundation (do first):
16 → 17 → 18 → 15

Phase 2 — Core Protection Features:
Fix 2 → 1 → 26 → 2 → 3 → 4 → 5 → Fix 1 → 21 → 25 → 30 → 31 → 32

Phase 3 — Safety Checker + Bug Fixes (do in this order):
29 → 22 → 23 → 24 → 6 → 7 → 19

Phase 4 — Senior Companion Features:
8 → 9 → 10 → 11 → 12 → 13 → 14

Phase 5 — Voice, Widget & Additional Security:
27 → 28 → 33

Phase 6 — Polish:
20 (Icon Update)

Phase 7 — Quality & Stability:
REMINDER B (Automated Testing)

Phase 8 — Pre-Launch Hardening:
REMINDER A (Security Hardening)
```

Notes:
- Fix 2 (rename SMS→Messages) at very start of Phase 2 — simple change,
  sets correct terminology for everything built after it
- Fix 1 (messages not flowing) placed after Feature 21 — must fix the
  NotificationListenerService foundation first
- Features 30, 31, 32 extend the messaging/scanning pipeline built in 21/25
- Feature 33 (additional security) in Phase 5 — standalone, no dependencies

**Master Claude Code instruction:**
```
Read CLAUDE.md. Implement all PENDING features in the recommended build
order shown at the bottom of this file (Phase 1 through 6). Work
autonomously. Mark each feature DONE in the summary table as you finish
it. Run ./gradlew assembleDebug every 3 features and fix all errors before
continuing. After completing feature 24 do a full uninstall and reinstall
on device using ./gradlew installDebug to ensure camera permission registers
correctly in Android Settings. Do not start Phase 7 or Phase 8 until
explicitly instructed.
```

---

### 34. Comprehensive Messaging App Scanning — WhatsApp, KakaoTalk, Telegram & More (PENDING — prompt ready, not yet run)

Full scanning support for all major messaging apps via three complementary methods.

**Supported Apps (32 total):**
SMS: Google Messages, Samsung Messages, AOSP, OnePlus, Huawei, Verizon, AT&T
Instant Messaging: WhatsApp, WhatsApp Business, Telegram (x2), KakaoTalk,
KakaoStory, LINE, Viber, Skype, Microsoft Teams
Social: Messenger, Messenger Lite, Instagram, Twitter/X, Snapchat, Discord
Asian: WeChat, QQ, Naver, BIGO Live, Zalo
Email: Gmail, Outlook, Yahoo Mail

**Three Detection Methods:**

**Method 1 — Notification Listener (automatic, preview only):**
Expanded package list with smart extraction handling MessagingStyle group chats.
Per-app scam context added to Claude prompt for each platform:
- WhatsApp: family impersonation, crypto groups, fake prizes, romance openers
- KakaoTalk: voice phishing, arrest warrant scam, investment fraud, grandchild impersonation
- LINE: fake official accounts, fake LINE Pay, investment groups
- WeChat: pig butchering, fake Red Packet QR codes, government impersonation
- Messenger: hacked friend impersonation, "I'm abroad" scam, fake marketplace
- Telegram: crypto channels, celebrity impersonation, pump and dump, fake jobs

**Method 2 — Accessibility Service (full on-screen text):**
New `SafeHarborAccessibilityService.kt`:
- Monitors `TYPE_WINDOW_CONTENT_CHANGED` + `TYPE_VIEW_SCROLLED` events
- App-specific node finding by resource ID per app (WhatsApp, KakaoTalk, Telegram, Messenger)
- Generic TextView fallback for unknown apps
- LruCache(100) deduplication — never scans same content twice
- Runs scan on `Dispatchers.IO` — never blocks UI thread
- Only triggers floating warning for DANGEROUS verdicts (not suspicious — avoids annoying user)
- `accessibility_service_config.xml` limits to messaging app packages only

**Accessibility Service permission guidance screen:**
Cannot use runtime permission flow. Step-by-step guidance:
Settings → Accessibility → Safe Harbor Security → Turn on
Reassurance: "Never records or stores conversations — only checks for scams"

**Method 3 — Share Sheet (manual, full content):**
Already built — any message from any app can be forwarded to Safe Harbor

**Floating Warning Banner (overlay):**
New `FloatingWarningManager.kt` using `TYPE_ACCESSIBILITY_OVERLAY`:
- Small amber banner at TOP of screen (non-intrusive, doesn't block content)
- Shield icon + warning summary + X dismiss button
- Auto-dismisses after 8 seconds
- Tap opens Safe Harbor with full scan result
- Only shown for DANGEROUS verdicts while user is inside messaging app
- Layout: `res/layout/floating_warning_banner.xml`

**Unified MessageScanRepository:**
Single repository handles all sources (notification, accessibility, share_sheet).
Per-app Claude prompt context injected based on source package.
JSON response fields: verdict, confidence, summary, explanation, red_flags,
recommended_action, scam_type, urgency_detected, contains_suspicious_link,
suspicious_link, requests_money, requests_personal_info.

**Messages Screen Updates:**
- Source filter chips: [All] [WhatsApp] [KakaoTalk] [Telegram] [Messages] [Messenger]
- Each item: app icon + 🟢🟡🔴 status + sender + preview + summary + timestamp + source
- Statistics card at top: "47 safe ⚠️ 3 suspicious 🔴 1 danger — Scanning: WhatsApp ● KakaoTalk ●"

**Settings — Scanned Apps section:**
List of detected installed messaging apps, toggle per app, detection method
indicator (🔔 notification / 👁️ full scan / 📤 manual), FAQ expandable.

**Updated MessageScanResult Room entity:**
Fields: sender, content (truncated 500 chars), sourceApp, sourcePackage,
scanMethod, verdict, confidence, summary, explanation, redFlags (JSON),
recommendedAction, scamType, urgencyDetected, containsSuspiciousLink,
suspiciousLink, requestsMoney, requestsPersonalInfo, timestamp, isRead,
isMarkedSafe (false positive flag). DB version increment + migration required.

**⚠️ Play Store Compliance Note:**
Accessibility Service must ONLY be used for stated accessibility purpose.
Description string must be honest. Data collected via accessibility must NOT
be transmitted beyond Claude API for scam analysis. Play Store listing must
declare accessibility service usage. These requirements are non-negotiable.

**New files:**
- `service/SafeHarborAccessibilityService.kt`
- `res/xml/accessibility_service_config.xml`
- `res/layout/floating_warning_banner.xml`
- `util/FloatingWarningManager.kt`
- `data/repository/MessageScanRepository.kt` (unified, replaces fragmented repos)

**Updated files:**
- `service/SafeHarborNotificationListener.kt` — expanded package list
- `ui/messages/MessagesScreen.kt` — filter chips + stats card
- `data/local/MessageScanResult.kt` — new unified entity
- `AndroidManifest.xml` — accessibility service declaration
- `res/values/strings.xml` — accessibility service description

---

## Updated Full Pending Feature List Summary

| # | Feature | Status |
|---|---------|--------|
| 1 | Listening Shield | PENDING |
| 2 | Stop Silent Listening Button | PENDING |
| 3 | Living Knowledge Base (Remediation Updates) | PENDING |
| 4 | Per-App Remediation Drop-Down | PENDING |
| 5 | Emulator Crash Investigation | PENDING |
| 6 | Is This Safe? Universal Safety Checker | PENDING |
| 7 | Stop Silent Listening Automation Upgrade | PENDING |
| 8 | I Think I Was Scammed Panic Button | PENDING |
| 9 | Daily Safe Check-In | PENDING |
| 10 | Real-Time Payment Warning | PENDING |
| 11 | Gift Card Alarm | PENDING |
| 12 | Safe Contacts Number Lookup | PENDING |
| 13 | Weekly Security Report Card | PENDING |
| 14 | Scam of the Week Notification | PENDING |
| 15 | App Rename to Safe Harbor Security | PENDING |
| 16 | App PIN / Biometric Lock + API Key Encryption | PENDING |
| 17 | Family-Friendly Onboarding | PENDING |
| 18 | Privacy Promise Screen | PENDING |
| 19 | Camera Permission Guidance + Gallery Fix + Chat Agent Fix | PENDING |
| 20 | Icon Update — Shield with Palm Tree / Peace of Mind Theme | PENDING |
| 21 | SMS Shield Fix — NotificationListenerService | PENDING |
| 22 | Fix Photo Gallery Routing Bug — Images to Chat Instead of Safety Checker | PENDING |
| 23 | Fix URL Checker Failures + Keyboard Covering Text Input | PENDING |
| 24 | Camera Permission Fix + AI Image Detection + URL Checker Fix + Keyboard/Button Fix | PENDING |
| 25 | Email Scanning + Messages/Email Tabs + Status Indicators | PENDING |
| 26 | Enhanced Privacy Monitor — Camera, Screen & All Silent Access Risks | PENDING |
| 27 | Home Screen Widget | PENDING |
| 28 | Voice Activation & Voice-Controlled Safety Checks | PENDING |
| 29 | URL Checker Complete Rewrite + Persistent Bug Fix | PENDING |
| Fix 1 | Text Messages Not Flowing Into App | PENDING |
| Fix 2 | Rename SMS to Messages on Home Screen | PENDING |
| 30 | Email Account Setup & Inbox Scanning | PENDING |
| 31 | Social Media Scam Detection | PENDING |
| 32 | WiFi Security Monitor | PENDING |
| 33 | Connect Additional Security — Third-Party Services | PENDING |
| 34 | Comprehensive Messaging App Scanning — WhatsApp KakaoTalk Telegram & More | PENDING |

---

## Complete Build Order (Recommended Sequence for Claude Code)

```
Phase 1 — Foundation (do first):
16 → 17 → 18 → 15

Phase 2 — Core Protection Features:
Fix 2 → 1 → 26 → 2 → 3 → 4 → 5 → Fix 1 → 21 → 34 → 25 → 30 → 31 → 32

Phase 3 — Safety Checker + Bug Fixes (do in this order):
29 → 22 → 23 → 24 → 6 → 7 → 19

Phase 4 — Senior Companion Features:
8 → 9 → 10 → 11 → 12 → 13 → 14

Phase 5 — Voice, Widget & Additional Security:
27 → 28 → 33

Phase 6 — Polish:
20 (Icon Update)

Phase 7 — Quality & Stability:
REMINDER B (Automated Testing)

Phase 8 — Pre-Launch Hardening:
REMINDER A (Security Hardening)

Phase 9 — Overnight Audit (run when all features complete):
REMINDER C (MASA / BBB / Privacy Compliance Audit)
```

Notes:
- Feature 34 placed in Phase 2 after Feature 21 — extends the same
  NotificationListenerService and adds AccessibilityService alongside it.
  Building both together is more efficient than touching the same files twice.
- Feature 34 should be built BEFORE Feature 25 (email) so the unified
  MessageScanRepository is in place before email scanning is added to it.
- Phase 9 added for the overnight MASA/BBB audit — run only when all
  other phases are complete.

**Master Claude Code instruction:**
```
Read CLAUDE.md. Implement all PENDING features in the recommended build
order shown at the bottom of this file (Phase 1 through 6). Work
autonomously. Mark each feature DONE in the summary table as you finish
it. Run ./gradlew assembleDebug every 3 features and fix all errors before
continuing. After completing feature 24 do a full uninstall and reinstall
on device using ./gradlew installDebug to ensure camera permission registers
correctly in Android Settings. Do not start Phase 7, 8, or 9 until
explicitly instructed.
```

---

### Fix 3 — Rename Chat Button to "Chat with Safe Harbor" (PENDING — prompt ready, not yet run)

Two buttons currently say "Talk to Safe Harbor" which confuses users.
- Round microphone FAB on home screen → rename to "Chat with Safe Harbor 💬"
- Voice activation button → keep as "Talk to Safe Harbor 🎤"
- Files: `ui/home/HomeScreen.kt`, `res/values/strings.xml`, chat button composable
- Clear distinction for elderly users: Chat = typed, Talk = voice

---

### 35. Interactive Voice & Chat Agent with Personas and Voices (PENDING — prompt ready, not yet run)

Fully interactive conversational AI companion experience. Replaces/enhances existing chat screen.

**Agent Knowledge Base (system prompt includes):**
- All scam types detected by app (SMS, email, social, marketplace, romance, crypto, grandparent, tech support, gift card, government impersonation)
- User's recent scan history from Room (last 5 scans)
- Current privacy monitor status
- WiFi security status
- All app features and how to use them
- Communication rules: simple language, warm/patient, never condescending, max 2-4 sentence responses, always end with question or next step

**4 Personas:**

| Persona | Gender | Personality | Voice | Default |
|---------|--------|-------------|-------|---------|
| Grace | Female | Warm, grandmotherly, patient | American, slow | ✅ Yes |
| James | Male | Calm, reassuring, authoritative but kind | American, standard | |
| Sophie | Female | Friendly, energetic, modern but patient | British, standard | |
| George | Male | Steady, wise, unhurried | British, slow | |

**5 Voice Styles (Android TTS):**
- American Standard (rate 0.9, pitch 1.0)
- British English (rate 0.9, pitch 1.0)
- Slow & Clear (rate 0.65, pitch 0.95)
- Gentle Pace / Elderly-Friendly (rate 0.75, pitch 0.95) ← default
- Energetic (rate 1.1, pitch 1.05)

**Chat UI Layout:**
- Animated persona avatar at top (idle animation + speaking animation)
- Left-aligned agent bubbles (soft blue/grey, persona name above)
- Right-aligned user bubbles (Safe Harbor blue, white text)
- Typing indicator (three animated dots) while API call pending
- Large font minimum 18sp throughout
- Quick action chips when chat empty: [Check a message] [Check a website] [Check a photo] [What is this scam?] [Am I protected?] [My scan history]
- Input bar: text field + 🎤 voice input + 📎 attach (opens Safety Checker) + Send
- imePadding() on input bar

**Persona Selection Screen (⚙️ from chat):**
- Card per persona: avatar + name + personality tagline + accent/speed
- "Preview Voice" button plays sample greeting via TTS
- Voice speed selector with labelled stops
- "Preview" button for current settings
- Stored in DataStore, default Grace

**Conversation Memory:**
- Full history sent with each API call (capped at last 20 messages)
- When cap reached: summarize older messages, keep summary in context
- MessageType enum: TEXT, SCAN_RESULT, QUICK_ACTION, SYSTEM_UPDATE

**Smart Actions from Chat:**
Agent can trigger app actions via ACTION JSON at end of response:
- `open_safety_checker` — with pre-filled text
- `open_panic_flow`
- `call_family_contact`
- `open_privacy_monitor`
- `check_url`
- `show_scan_result`
- `open_settings`

**TTS Controls per message bubble:**
▶️ Play / ⏹️ Stop buttons below each agent message.
Auto-play toggle in Settings (default ON). Soft chime before each response. Avatar animates while speaking.

**Conversation History (Room):**
`ChatConversation` entity: id, personaId, startTime, lastMessageTime, messageCount, Claude-generated summary.
`ChatMessage` entity: conversationId, role, content, messageType, timestamp.
"Continue talking to Grace" card shown if last conversation within 24 hours.

**Onboarding Integration:**
New step after PIN setup: "Meet your Safe Harbor companion"
Preview each persona, select favourite, hear greeting animation. Defaults to Grace if skipped.

**New files:**
- `ui/chat/NewChatScreen.kt`
- `ui/chat/PersonaSelectionScreen.kt`
- `ui/chat/ChatViewModel.kt` (updated)
- `ui/chat/components/MessageBubble.kt`
- `ui/chat/components/AgentAvatar.kt`
- `ui/chat/components/TypingIndicator.kt`
- `ui/chat/components/QuickActionChips.kt`
- `ui/chat/components/TTSControls.kt`
- `util/SafeHarborTTS.kt`
- `util/VoiceInputManager.kt`
- `data/model/AgentPersona.kt`
- `data/model/VoiceStyle.kt`
- `data/local/ChatConversation.kt`
- `data/local/ChatMessage.kt`
- `data/local/ChatDao.kt`
- `res/drawable/avatar_grace.xml`
- `res/drawable/avatar_james.xml`
- `res/drawable/avatar_sophie.xml`
- `res/drawable/avatar_george.xml`

**Updated files:**
- `ui/home/HomeScreen.kt` — rename button
- `res/values/strings.xml` — updated strings
- `data/local/AppDatabase.kt` — new entities + migration

---

## Updated Full Pending Feature List Summary

| # | Feature | Status |
|---|---------|--------|
| 1 | Listening Shield | PENDING |
| 2 | Stop Silent Listening Button | PENDING |
| 3 | Living Knowledge Base (Remediation Updates) | PENDING |
| 4 | Per-App Remediation Drop-Down | PENDING |
| 5 | Emulator Crash Investigation | PENDING |
| 6 | Is This Safe? Universal Safety Checker | PENDING |
| 7 | Stop Silent Listening Automation Upgrade | PENDING |
| 8 | I Think I Was Scammed Panic Button | PENDING |
| 9 | Daily Safe Check-In | PENDING |
| 10 | Real-Time Payment Warning | PENDING |
| 11 | Gift Card Alarm | PENDING |
| 12 | Safe Contacts Number Lookup | PENDING |
| 13 | Weekly Security Report Card | PENDING |
| 14 | Scam of the Week Notification | PENDING |
| 15 | App Rename to Safe Harbor Security | PENDING |
| 16 | App PIN / Biometric Lock + API Key Encryption | PENDING |
| 17 | Family-Friendly Onboarding | PENDING |
| 18 | Privacy Promise Screen | PENDING |
| 19 | Camera Permission Guidance + Gallery Fix + Chat Agent Fix | PENDING |
| 20 | Icon Update — Shield with Palm Tree / Peace of Mind Theme | PENDING |
| 21 | SMS Shield Fix — NotificationListenerService | PENDING |
| 22 | Fix Photo Gallery Routing Bug — Images to Chat Instead of Safety Checker | PENDING |
| 23 | Fix URL Checker Failures + Keyboard Covering Text Input | PENDING |
| 24 | Camera Permission Fix + AI Image Detection + URL Checker Fix + Keyboard/Button Fix | PENDING |
| 25 | Email Scanning + Messages/Email Tabs + Status Indicators | PENDING |
| 26 | Enhanced Privacy Monitor — Camera, Screen & All Silent Access Risks | PENDING |
| 27 | Home Screen Widget | PENDING |
| 28 | Voice Activation & Voice-Controlled Safety Checks | PENDING |
| 29 | URL Checker Complete Rewrite + Persistent Bug Fix | PENDING |
| Fix 1 | Text Messages Not Flowing Into App | PENDING |
| Fix 2 | Rename SMS to Messages on Home Screen | PENDING |
| Fix 3 | Rename Chat Button to "Chat with Safe Harbor" | PENDING |
| 30 | Email Account Setup & Inbox Scanning | PENDING |
| 31 | Social Media Scam Detection | PENDING |
| 32 | WiFi Security Monitor | PENDING |
| 33 | Connect Additional Security — Third-Party Services | PENDING |
| 34 | Comprehensive Messaging App Scanning — WhatsApp KakaoTalk Telegram & More | PENDING |
| 35 | Interactive Voice & Chat Agent with Personas and Voices | PENDING |

---

## Complete Build Order (Recommended Sequence for Claude Code)

```
Phase 1 — Foundation (do first):
16 → 17 → 18 → 15

Phase 2 — Core Protection Features:
Fix 2 → Fix 3 → 1 → 26 → 2 → 3 → 4 → 5 → Fix 1 → 21 → 34 → 25 → 30 → 31 → 32

Phase 3 — Safety Checker + Bug Fixes (do in this order):
29 → 22 → 23 → 24 → 6 → 7 → 19

Phase 4 — Senior Companion Features:
8 → 9 → 10 → 11 → 12 → 13 → 14

Phase 5 — Voice, Widget, Chat & Additional Security:
27 → 28 → 35 → 33

Phase 6 — Polish:
20 (Icon Update)

Phase 7 — Quality & Stability:
REMINDER B (Automated Testing)

Phase 8 — Pre-Launch Hardening:
REMINDER A (Security Hardening)

Phase 9 — Overnight Audit:
REMINDER C (MASA / BBB / Privacy Compliance Audit)
```

Notes:
- Fix 3 added to Phase 2 alongside Fix 2 — both are simple label changes,
  do them together at the very start
- Feature 35 placed in Phase 5 after widget (27) and voice (28) are built —
  the interactive chat agent builds on the voice infrastructure from Feature 28
- Feature 35 is the most complex single feature in the app — allocate a full
  Claude Code session to it alone if possible

**Master Claude Code instruction:**
```
Read CLAUDE.md. Implement all PENDING features in the recommended build
order shown at the bottom of this file (Phase 1 through 6). Work
autonomously. Mark each feature DONE in the summary table as you finish
it. Run ./gradlew assembleDebug every 3 features and fix all errors before
continuing. After completing feature 24 do a full uninstall and reinstall
on device using ./gradlew installDebug to ensure camera permission registers
correctly in Android Settings. Do not start Phase 7, 8, or 9 until
explicitly instructed.
```

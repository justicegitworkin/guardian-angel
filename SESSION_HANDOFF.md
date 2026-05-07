# Safe Companion — Session Handoff Document

**Generated:** 2026-05-06
**Purpose:** Allow a new Claude session (potentially in a different project / different model) to pick up exactly where the previous session ended, without needing to re-trace context.

**Read this AFTER reading `CLAUDE.md` (which has the canonical project instructions and feature status table).** This document captures what's happened since the latest session, current open issues, strategic decisions made, and proposed work not yet started.

---

## 1. The user — context that affects how to help

- **Steve** — solo developer, building Safe Companion as a side project aimed at protecting elderly users from scams.
- **Currently in beta** with targeted real testers (~5–10 people, mostly adult children installing on parents' phones).
- **Test phone is a TINNO U656AC running Android 15** — a budget phone, sometimes shows OEM-specific behavior different from Pixel/Samsung.
- **Build environment is Windows + PowerShell + Android Studio**, JDK 17 from Android Studio's bundled JBR.
- **The user does NOT have a phone connected via USB / adb.** They build APKs locally and transfer them to the test phone manually (Phone Link, Drive, file transfer). **Always end build instructions with `assembleStandardDebug`, NEVER `installStandardDebug`.**
- They are not a deeply experienced Android dev — explanations should be friendly, but they understand most concepts. Treat them like a smart product person who codes.

---

## 2. What was accomplished in the most recent session

The session started with the user asking strategic questions and ended with a long string of bug fixes. In rough chronological order:

### Strategic discussions
- iOS port feasibility — answer: not a port, a different product. Lose SMS Shield, Screen Monitor, live call screening, Background Guardian, Listening Shield, app monitoring, WiFi enumeration. Recommended path: skip iOS, build web family dashboard + Phase 10 cloud API.
- Web / desktop port — same answer: web yes (especially family dashboard for adult children), desktop probably not worth the Electron friction.
- "Should I pivot to a wishlist app?" — strong NO. The product idea is sound, AARP is 24–36 month horizon (unrealistic short-term), direct-to-consumer through adult children is faster. Recommended freezing scope, finishing privacy/ToS, doing 50–100 user private beta, launching in 90 days at $9.99/month paid by adult child.
- Banking app verification (raised from a friend's news article) — proposed feature design, not yet built. See section 6.

### Phase 7 — Testing & CI (DONE this session)
- Added test infrastructure to `app/build.gradle.kts`: MockK 1.13.13, Robolectric 4.13, kotlinx-coroutines-test 1.8.1, Turbine 1.1.0, androidx.arch.core:core-testing 2.2.0, Truth 1.4.4, MockK-Android.
- `testOptions { unitTests.isIncludeAndroidResources = true; isReturnDefaultValues = true }` — required for Robolectric + DataStore tests.
- 87 JVM unit tests across 5 test files:
  - `app/src/test/java/com/safeharborsecurity/app/util/ScamArticleFilterTest.kt` — 20 tests guarding the news feed filter (Robolectric, `@Config(sdk = [33])`)
  - `app/src/test/java/com/safeharborsecurity/app/ml/OnDeviceScamClassifierTest.kt` — 24 tests (pure JVM, no Robolectric)
  - `app/src/test/java/com/safeharborsecurity/app/ml/LocalChatResponderTest.kt` — 19 tests (pure JVM)
  - `app/src/test/java/com/safeharborsecurity/app/util/KeystoreManagerPinTest.kt` — 12 tests (Robolectric, `@Config(sdk = [33])`)
  - `app/src/test/java/com/safeharborsecurity/app/util/RssFeedParserTest.kt` — 10 tests (Robolectric, `@Config(sdk = [33])`)
- GitHub Actions CI at `.github/workflows/android-tests.yml` — runs `./gradlew :app:testStandardDebugUnitTest` on every push/PR + builds debug APK as artifact.
- Tests are pinned to Robolectric SDK 33 (not 35) to avoid network fetches on every CI run.
- All 87 tests passing locally.

### Bug fixes shipped this session

**Voice / chat (`SafeHarborChatViewModel.kt`)**
- Voice never timing out → switched from coroutine-job-based mid-speech timer to a timestamp-based silence ticker. New field `lastSpeechActivityMs`, new method `markSpeechActivity()` called from non-blank `onPartialResults`, new `startSilenceTicker()` runs every 1s polling against `silenceBudgetMs`. Survives recognizer cycles and onBeginningOfSpeech firing on background noise. **Current `silenceBudgetMs = 7_000L`**.
- Watchdog reduced 35s → 22s as a safety net under the new ticker.
- Voice beeping on/off → extended `muteBeepTone()` to also silence `STREAM_NOTIFICATION` and `STREAM_SYSTEM` (not just MUSIC, which OEM "blip" tones don't use). Added mute/restore around recognizer destroy+recreate cycles in `onResults`, `startVoiceTurn`, and `continueListening`.
- ElevenLabs falling back to Android TTS on text-Send → added `forceRestoreStreams()` called at start of `speakText()` to guarantee streams aren't left muted from a leaked `muteBeepTone()`.
- Suppressed `Unreachable code` warning at top of `startInterruptListener()` by moving `@Suppress("UNREACHABLE_CODE", "FunctionName")` to function level.

**ElevenLabs voice race (`ElevenLabsTTSManager.kt`)**
- Tester reported "tap James, hear Sophie." Root cause: every persona's audio was written to the SAME shared file `elevenlabs_audio.mp3`. Fast persona switches caused the second response to overwrite the first while MediaPlayer was still reading it. **Fix:** per-persona file `elevenlabs_audio_${safeName}.mp3` so files never collide. `playAudioFile` already calls `stop()` so the prior MediaPlayer is killed cleanly before each playback.

**Listening Shield notification noise (`SafeHarborApp.kt`, `PrivacyMonitorService.kt`)**
- Tester reported a useless heads-up notification every time Listening Shield was toggled on. Foreground services REQUIRE a visible notification (Android won't let us hide), but we made it as quiet as legally possible:
  - Channel `CHANNEL_PRIVACY` dropped from `IMPORTANCE_LOW` to `IMPORTANCE_MIN`, plus `setShowBadge(false)`, `setSound(null, null)`, `enableLights(false)`, `enableVibration(false)`.
  - Notification builder uses `PRIORITY_MIN`, `setSilent(true)`, `setVisibility(VISIBILITY_SECRET)`.
  - **Important:** `createNotificationChannels()` calls `manager.deleteNotificationChannel(CHANNEL_PRIVACY)` BEFORE creating, because Android caches user-set channel importance and ignores subsequent code-side updates. The delete forces a fresh creation with new MIN settings.

**Caller ID & spam picker not showing Safe Companion (`AndroidManifest.xml`)**
- Single typo: `android:permission="android.permission.BIND_SCREENING_APP_ROLE"` (NOT a real Android permission) → corrected to `android:permission="android.permission.BIND_SCREENING_SERVICE"`. Android was treating the service as malformed and silently excluding Safe Companion from the picker.

**"Tell Me More" alert button (`GrabAttentionActivity.kt`, `SafeHarborChatScreen.kt`)**
- Added `@AndroidEntryPoint` and `@Inject UserPreferences` to GrabAttentionActivity. Reads persona via `runBlocking { userPreferences.chatPersona.first() }` once at activity start. Replaced `Icons.Filled.Info` with `Text(personaEmoji, fontSize = 28.sp)` so users see their own assistant's face on the button.
- Chat screen previously stuffed the alert context into the input box but never submitted. Now wraps it: "I just received this and I'm worried it might be a scam. Can you walk me through whether this is dangerous, what kind of scam this looks like, and what I should do? Here is exactly what it said: '...'" — and calls `viewModel.sendMessage(text = framed, speakResponse = true)`. `contextSubmitted` boolean state guards against re-submission on recomposition.

**OnDeviceScamClassifier regex bug (uncovered by tests)**
- `^\+?(232|234|...|876)\d+` didn't handle NANP +1 prefix (Jamaica `+18765551234` wouldn't match). Changed to `^\+?1?(...)\d{7,}` — added optional `1` AND required 7+ digits after the area code (NANP subscriber portion). Without the `\d{7,}` constraint, "12345" parsed as "1 + 234 (Bahamas) + 5" and false-positived as DANGEROUS. **Known limitation that wasn't fixed:** US area code 234 (Ohio overlay for 330) shares prefix with Bahamas — would still false-positive. Real fix needs a true area-code lookup table separating NANP-Caribbean from NANP-US.

**"Add Safe Companion to my Home screen" button (`OnboardingScreen.kt`)**
- Was thin OutlinedButton, easy to miss. Converted to filled `Button` with `containerColor = SafeGreen` (`#2E7D32`) and white text/icon. Same green used for SAFE verdict shield elsewhere — semantic consistency.

### Git ordeal — IMPORTANT lesson learned
The user had been editing files for MONTHS without committing. The local repo only had 2 commits (initial + README from March). When they pulled origin/master, it had drifted to a different branch (`com.guardianangel.app` namespace, the OLD codebase). The pull-rebase tried to apply our 2 local commits on top, conflicts in build.gradle.kts, we did `git add -A` to unblock, the WIP commit captured the entire safeharbor codebase, then a `git rebase --skip` (advised) DROPPED that commit. Codebase wiped from working tree.

**Recovery**: `git reset --hard 0762726` (the WIP commit hash, recovered from `git reflog`) restored everything. Force-pushed to origin to overwrite the bad master.

**Lesson:** the user must commit daily going forward. A simple `git add -A && git commit -m "End of day" && git push` would have made today's mess impossible.

---

## 3. Current state of the app — what's working / what's not

### Working as expected
- News feed (Phase 7 widening shipped: AARP, FTC consumer blog, FTC press releases, BBB, FBI IC3, FBI press, Snopes, Krebs, Hacker News). ScamArticleFilter passes meaningful articles, rejects political/celebrity/procedural noise.
- Phase 7 unit tests (87 passing locally + on GitHub Actions CI).
- Listening Shield (the actual feature, just the notification was annoying — now silent).
- Hidden Device Scanner (WiFi list, IR camera, magnetic, mirror check, ultrasonic, Bluetooth).
- Onboarding flow.
- "Tell Me More" alert button now shows persona emoji and auto-submits framed context.
- Caller ID picker now lists Safe Companion (after manifest permission fix).
- Voice silence timeout: 7 seconds, robust against recognizer cycles.

### Possibly fixed but unverified at session end
- **ElevenLabs intermittent failures + male-voice-sounding-female** — the per-persona file fix should resolve both, but user hadn't rebuilt at session end. Needs testing with the persona picker preview buttons rapidly tapped.
- **Voice timeout responsiveness** — set to 7s in code, not yet rebuilt and tested.

### Known issues / suspicions
- ElevenLabs MIGHT still be flaky on free-tier rate limits; we didn't add retry logic for 429/5xx. If the per-persona file fix doesn't resolve, add a single retry with backoff.
- The "first chat agent tap → notification appears" issue earlier in session was diagnosed as a stale `lastActiveTime = 0L` triggering the lock screen. That's fixed (MainActivity stamps lastActive on resume + during use).
- Robolectric SDK 33 jar download will be slow on the very first CI run, then cached.

### Open product questions
- Should we add Banking App Verifier? — designed but not built, see section 6.
- Phase 9 (privacy/ToS/OWASP) is mandatory before any public launch.

---

## 4. Project / codebase context

### Tech stack
Kotlin · Jetpack Compose · MVVM+Repository · Hilt · Room · DataStore · Retrofit+OkHttp · Min SDK 26 · Target SDK 35 · Claude Haiku (`claude-haiku-4-5-20251001`) · ElevenLabs TTS · Google Neural TTS fallback · Android system TTS last-resort fallback · ML Kit (Latin text recognition + barcode scanning) · MediaProjection + ImageReader for screen scanning.

### Namespacing
- Namespace: `com.safeharborsecurity.app`
- Application ID: `com.safeharborsecurity.app`
- Beta flavor adds `.beta` suffix
- Kotlin source files all under `com/safeharborsecurity/app/...`

### Build flavors
- `standard` — production
- `beta` — `applicationIdSuffix = ".beta"`, `versionNameSuffix = "-beta"`, `DEMO_MODE_DEFAULT = true`, `SHOW_DEBUG_INFO = true`

### Personas (in `data/model/ChatPersona.kt`)
- GRACE — 👵 — warm, grandmotherly, ElevenLabs voice "Sarah" (`EXAVITQu4vr4xnSDxMaL`)
- JAMES — 👨‍💼 — calm, reassuring, ElevenLabs voice "Arnold" (`VR6AewLTigWG4xSOukaG`) — **DEFAULT persona**
- SOPHIE — 👩 — friendly, modern, ElevenLabs voice "Gigi" (`jBpfuIE2acCO8z3wKNLl`)
- GEORGE — 👴🏾 — steady, wise, ElevenLabs voice "Daniel" (`onwK4e9ZLuTAKqWW03F9`)

### Key files to know about
- `app/src/main/java/com/safeharborsecurity/app/SafeHarborApp.kt` — Application class, schedules WorkManager workers, creates notification channels, seeds baked API keys from BuildConfig to DataStore.
- `app/src/main/java/com/safeharborsecurity/app/MainActivity.kt` — Single Activity, hosts Compose nav graph. Handles deep links (`safeharbor://chat?context=...`). Lock screen logic (PIN/biometric, lastActiveTime stamping for auto-lock).
- `app/src/main/java/com/safeharborsecurity/app/ui/chat/SafeHarborChatViewModel.kt` — Voice state machine (IDLE/LISTENING/PROCESSING/SPEAKING/SPEAK_COOLDOWN), recognizer lifecycle, silence ticker, TTS coordination. Most complex file in the app (~1300 lines).
- `app/src/main/java/com/safeharborsecurity/app/util/SafeHarborVoiceManager.kt` — Voice tier selection (ElevenLabs → Google → Android), audio focus.
- `app/src/main/java/com/safeharborsecurity/app/util/ElevenLabsTTSManager.kt` — Direct ElevenLabs API calls + MediaPlayer playback.
- `app/src/main/java/com/safeharborsecurity/app/data/repository/NewsRepository.kt` — RSS sources + sync.
- `app/src/main/java/com/safeharborsecurity/app/util/ScamArticleFilter.kt` — Two-tier news filter (STRONG keywords + WEAK+CONTEXT keywords + EXCLUDES).
- `app/src/main/java/com/safeharborsecurity/app/ml/OnDeviceScamClassifier.kt` — Rule-based classifier for SMS / screen-OCR text, returns SAFE/SUSPICIOUS/DANGEROUS/UNKNOWN.
- `app/src/main/java/com/safeharborsecurity/app/ml/LocalChatResponder.kt` — Offline scripted answers (11 intents) when no Anthropic key.
- `app/src/main/java/com/safeharborsecurity/app/service/ScreenScanService.kt` — MediaProjection + OCR for SMS/payment app text.
- `app/src/main/java/com/safeharborsecurity/app/service/SafeHarborCallScreeningService.kt` — Call screening (Silent Guardian auto-decline).
- `app/src/main/java/com/safeharborsecurity/app/service/PrivacyMonitorService.kt` — Listening Shield foreground service.
- `app/src/main/java/com/safeharborsecurity/app/ui/alert/GrabAttentionActivity.kt` — Full-screen scam alerts with "Tell Me More" button.

### Build / install workflow specific to this user

```powershell
# Set Java if not on PATH
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"

# Build only — user does NOT use installStandardDebug
.\gradlew.bat assembleStandardDebug
```

APK lands at: `C:\Users\steve\guardian-angel\app\build\outputs\apk\standard\debug\app-standard-debug.apk`

User transfers to phone manually. **Always frame instructions around `assembleStandardDebug`.**

### local.properties keys (gitignored)

```
safe.companion.anthropic.api.key=sk-ant-...
safe.companion.elevenlabs.api.key=sk_...
safe.companion.feedback.form.url=https://docs.google.com/forms/d/e/.../viewform
```

These get baked into BuildConfig at build time and synced into DataStore on every app launch via `SafeHarborApp.seedBakedKeysFromBuildConfig()`. **If a user reports any AI feature broken, FIRST check that local.properties has both keys.**

### Git remote
- `https://github.com/justicegitworkin/guardian-angel`
- Default branch: `master` (not `main`)
- GitHub Actions CI runs on push/PR — green check = unit tests passing.
- The user's PAT needs both `repo` AND `workflow` scopes to push `.github/workflows/*` files. If you ever add or modify workflow files and a push fails with "refusing to allow a Personal Access Token to create or update workflow," tell the user to either run `gh auth refresh -h github.com -s workflow`, OR add the workflow file via GitHub's web UI which bypasses PAT scope checks.

---

## 5. Strategic guidance given (for continuity of advice)

### On the right path?
Yes. Don't pivot to a wishlist app — that's burnout reasoning. The product idea addresses a $10B+ scam loss problem with no dominant elder-specific competitor. Voice companion + manual checker + news + family alerts is a defensible product. The features that fight Google Play (passive monitoring, screen OCR, accessibility/notification listener) are also the features that risk delisting — keep them but de-emphasize in store listing language.

### AARP partnership
24–36 month horizon, NOT a launch strategy. AARP requires LLC + insurance + SOC 2 + 10K–50K users + small team. Realistic path: launch, scale to 1K paying families, then approach AARP. Don't gate other progress on AARP.

### iOS port
Not a port — a different product. Lose SMS Shield, Screen Monitor, Call Screening (live AI), Background Guardian, Listening Shield, Payment Monitor, App Checker, WiFi enumeration. What's left is voice agent + Is This Safe? + Gmail scanning + news + family alerts + magnetic/IR/mirror/ultrasonic scans. Roughly 6 months for one engineer. Recommended: skip iOS, build web family dashboard fed by Phase 10 cloud API.

### Web / desktop
Web = yes (especially family dashboard for adult children — that's the killer app for monetization). Desktop = probably not unless specific need. Build web on Next.js + Phase 10 Azure Function API + Cosmos DB. ~2–3 months one engineer.

### Pricing
$9.99/month, paid by adult child (not the elderly user — they won't put a credit card in). 14-day free trial. Same model Bark uses.

### Feature scope
The user has 100+ features in CLAUDE.md status table. Many are padding. The six that matter for an elder are: voice companion, Is This Safe?, news feed/education, weekly check-in, family alerts, ONE passive-monitoring feature done well (probably SMS via NotificationListener since it has the highest catch rate). Rest can ship later or never.

### Pre-launch must-haves
- Phase 9 (privacy policy, ToS, GDPR/CCPA compliance, data deletion endpoint, OWASP MASVS L1 self-audit, incident response plan)
- Crashlytics or equivalent telemetry
- Google Play store listing copy that leans heavily into "voice companion + education" and softens passive features
- Support email + response SLA
- 50–100 user private beta from senior centers / churches / community groups (not user's network)

### Recommended 90-day plan (not yet executed)
- Month 1: Privacy/ToS, telemetry, security pass, store listing
- Month 2: Private beta, observe what testers actually use, cut padding features
- Month 3: Public launch with smaller/sharper product

---

## 6. Pending / proposed feature: Banking App Verifier

**Triggered by:** user's friend reading a news article about fake AI banking websites + the suggestion that banking phone apps are "safer" but only if you can confirm authenticity.

**Status:** designed in conversation, NOT yet implemented. User had not yet greenlit before session ended.

**Threat model:** scam SMS → "your bank account compromised, install secure app" → fake APK download link → user installs → fake login captures credentials → account drained. Top vectors: sideload from phishing link, fake "update your app" website, occasional Play Store impersonator.

**Proposed approach — signature pinning database**

Every legitimate Android app is signed by a specific developer cert. The SHA-256 fingerprint of that cert is essentially impossible to forge without the bank's private key. Compare what's installed (or what's in a downloaded APK) against a known-good fingerprint. Match = real, mismatch = fake.

**Three entry points**
1. **"Check if my banking app is real"** in the app — user searches for their bank by name → look up official package + cert fingerprint in curated DB → check matching app on phone → green check or red shield with "uninstall and download from Play Store."
2. **APK file inspection before install** — register `application/vnd.android.package-archive` intent filter so Safe Companion appears in "Open with..." dialog when user taps a downloaded APK. Examine APK signature against DB before install. If user picks our app and "Always," we become the safer default.
3. **"Is this banking link safe?"** in Is This Safe? — paste URL claiming to be banking site, we check known-bad distribution domains AND offer the official Play Store link instead.

**Data file (ship in `assets/banking_apps.json`)**

Top 50 US banks + top 20 financial apps (Venmo, Cash App, Zelle, Robinhood, etc.). Each entry: official package name, signing cert SHA-256 fingerprint, Play Store URL, plain-language brand name.

Refresh weekly via existing WorkManager (same pattern as news sync). Keep the database fresh without app updates.

**Files to create (when we build it)**
- `app/src/main/assets/banking_apps.json` — curated DB
- `app/src/main/java/com/safeharborsecurity/app/util/BankingAppVerifier.kt` — signature extraction + comparison logic
- `app/src/main/java/com/safeharborsecurity/app/ui/safety/BankingAppCheckScreen.kt` — search UI
- Manifest update: intent filter on a new `ApkVerifyActivity`

**Effort estimate**
- Curated DB (research + extraction): 4 hours
- Verifier engine (PackageInfo.signatures + SHA-256): 4 hours
- Banking check UI: half day
- APK intent-filter handler + verification UI: half day
- URL → Play Store redirect logic in Is This Safe?: 2 hours
- Testing across 5–10 real banking apps: half day
- **Total: ~2 days**

**Honest limit to communicate to user:** we cannot prevent install from outside our app. If a user picks "Package Installer" instead of "Safe Companion" from the Open With dialog, we never see it. Same as Norton, Bitdefender, etc. — Android security model prevents it.

**Why it's worth building**
- Direct answer to a specific, real, escalating threat
- Easy to demo ("watch me catch this fake Chase app")
- AARP-grade specificity ("we cryptographically verify your banking apps")
- Differentiates Safe Companion from generic mobile security apps

---

## 7. Pending phases (for forward planning)

### Phase 7 — Testing (PARTIALLY DONE)
- [x] JVM unit tests (87 tests, 5 files)
- [x] GitHub Actions CI
- [ ] Compose UI smoke tests (HomeScreen, OnboardingScreen, SafeHarborChatScreen)
- [ ] Repository-layer tests with MockK
- [ ] ViewModel tests with Turbine
- [ ] End-to-end Hilt test runner integration

### Phase 8 — Security Hardening (PARTIALLY DONE per status table, needs review)
- [x] Root/emulator/tamper detection (`IntegrityChecker`)
- [x] KeystoreManager hardening (Fix 43)
- [ ] Full R8 obfuscation rules audit
- [ ] Certificate pinning for Anthropic + ElevenLabs API
- [ ] TLS 1.2+ enforcement
- [ ] FLAG_SECURE on sensitive screens (already partially in MainActivity)
- [ ] Prompt injection prevention review for chat
- [ ] `android:allowBackup="false"` confirmation

### Phase 9 — Privacy / Compliance (BLOCKING for launch)
- [ ] Privacy policy (`assets/privacy_policy.html` exists per WIP commit, needs review)
- [ ] Terms of Service
- [ ] GDPR/CCPA/COPPA compliance review
- [ ] Data deletion endpoint (when Phase 10 cloud API ships)
- [ ] OWASP MASVS L1+L2 self-audit
- [ ] BBB readiness checklist
- [ ] Incident response plan

### Phase 10 — Cloud Fraud API (architectural priority)
- Azure Functions + Cosmos DB
- 5-step pipeline: deterministic checks → external reputation (Google Web Risk, VirusTotal) → ML text classifier → cross-user intelligence → Claude fallback
- Enables iOS/web ports to share a backend
- Estimated cost ~$15–35/month for first 1000 users

### Phase 11+ (proposed, not in CLAUDE.md yet)
- Banking App Verifier (section 6)
- Web family dashboard
- iOS thin companion app

---

## 8. Things to do FIRST in the next session

1. **Confirm the user rebuilt and tested** the session's last fixes — particularly the ElevenLabs per-persona file fix and the 7-second silence timeout. If still flaky, add HTTP retry on 429/5xx in ElevenLabsTTSManager.

2. **Commit and push everything** if the user hasn't:
   ```powershell
   git add -A
   git commit -m "End-of-session bug fixes: voice timeout, ElevenLabs race, Listening Shield silence, Tell Me More context"
   git push
   ```

3. **Reinforce the daily-commit habit** — they lost months of work this session because they hadn't committed.

4. **Decide on Banking App Verifier** — it's the highest-impact next feature for a tester / AARP demo and 2 days of work. Ask the user if they want to build it.

5. **Phase 9 prep** — if launch is the goal, this is the next blocking item. Privacy policy + ToS + OWASP self-audit.

---

## 9. Conventions and preferences picked up over many sessions

- **All UI copy plain English for elderly users** — no jargon. "Suspicious" not "anomalous." "Block this number" not "deny inbound calls from this MSISDN."
- **Format conventions:** Material 3, NavyBlue + WarmGold + SafeGreen + ScamRed primary palette. Dark theme.
- **Personas** affect ONLY the response opening / system prompt, not the actual analysis output.
- **No emojis in code or docs** unless it's a deliberate UI element (persona avatars, scam warnings).
- **Voice tier ordering preserved**: ElevenLabs → Google Neural → Android TTS. Never reorder without explicit user request.
- **Don't add features without user request** — the status table is already 100+ entries long. Cut, don't add, when in doubt.
- **Comments are appreciated** — the user reads code and benefits from why-not-just-what comments. Especially around regex, state machines, and anything tricky.

---

## 10. Quick reference: things that broke this session and how

| Symptom | Cause | Fix file |
|---|---|---|
| Voice keeps listening forever | Coroutine-job timer reset by recognizer cycle / noise-triggered onBeginningOfSpeech | `SafeHarborChatViewModel.kt` — silence ticker with timestamp |
| Beep beep on/off during voice | `muteBeepTone()` only muted MUSIC stream, OEM blips on NOTIFICATION/SYSTEM | `SafeHarborChatViewModel.kt` — extended mute helper |
| ElevenLabs falls back to Android TTS on Send | Leaked muteBeepTone left STREAM_MUSIC at 0 → MediaPlayer silent | `SafeHarborChatViewModel.kt` — `forceRestoreStreams()` at top of `speakText` |
| Tap James, hear Sophie | All personas wrote to same `elevenlabs_audio.mp3`, race overwrote mid-playback | `ElevenLabsTTSManager.kt` — per-persona filename |
| Listening Shield notification pops up | Foreground service notification on IMPORTANCE_LOW channel showed heads-up on OEM | `SafeHarborApp.kt` + `PrivacyMonitorService.kt` — IMPORTANCE_MIN + setSilent + delete-and-recreate |
| Safe Companion missing from Caller ID picker | Manifest typo `BIND_SCREENING_APP_ROLE` (not real) → service treated as malformed | `AndroidManifest.xml` — corrected to `BIND_SCREENING_SERVICE` |
| "Tell Me More" agent says "tell me what the message said" | Context only stuffed into input box, never submitted | `SafeHarborChatScreen.kt` — `sendMessage(framed, speakResponse=true)` |
| Caribbean scam phone numbers not detected | Regex didn't allow optional `+1` prefix | `OnDeviceScamClassifier.kt` — `^\+?1?(...)\d{7,}` |
| Build wiped entire codebase | Local edits never committed before pull-rebase, WIP commit was skipped | `git reset --hard 0762726` from reflog |

---

**End of handoff document.** New session should now have full context to continue work. Read `CLAUDE.md` for the canonical project spec; this document covers what's happened since.

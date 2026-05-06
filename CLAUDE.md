# Safe Companion — Project Context

## App
AI-powered Android app protecting elderly users from scam calls, SMS fraud, suspicious emails, and cyber threats. Voice agent with personas (Grace/James/Sophie/George). Named "Safe Companion" — package `com.safecompanion.app`.

## Tech Stack
Kotlin · Jetpack Compose · MVVM+Repository · Claude Haiku (`claude-haiku-4-5-20251001`) · Hilt · Room · DataStore · Retrofit+OkHttp · Min SDK 26 · Target SDK 35

## Project Structure
```
app/src/main/java/com/safecompanion/app/
├── data/        # datastore/ local/ remote/ repository/
├── di/          # Hilt modules
├── receiver/    # SMS broadcast receiver
├── service/     # Call screening + overlay services
├── ui/          # chat/ calls/ home/ messages/ onboarding/ settings/ safetychecker/ privacy/ news/
└── util/        # VoiceInputManager ElevenLabsTTSManager NotificationManager etc.
```

## Conventions
- All Claude API calls via Retrofit client in `data/remote/`
- User prefs in DataStore · Historical data in Room · Hilt everywhere · Compose only, no XML
- All UI copy plain English for elderly users — no jargon
- ElevenLabs TTS (primary) → Google Neural TTS (fallback) → Android TTS (last resort)
- Gmail OAuth requires both Android client AND Web Application client in google-services.json

---

## Master Instruction
```
Read CLAUDE.md. Implement all PENDING items in build order below (Phase 2 onward —
Phase 1 is complete). Work autonomously. Mark each DONE in the status table as
you finish. Run ./gradlew assembleDebug every 3 features; fix all errors before
continuing. Do not start Phase 7, 8, or 9 until explicitly instructed.
```

---

## Status Table

| ID | Feature / Fix | Status |
|----|--------------|--------|
| 1 | Listening Shield — AppOpsManager mic detection, Privacy Monitor screen | PENDING |
| 2 | Stop Silent Listening Button — wizard to disable ad services/mic | PENDING |
| 3 | Living Knowledge Base — WorkManager weekly Claude sync for remediation tips | PENDING |
| 4 | Per-App Remediation Drop-Down — toggle or how-to per flagged app | PENDING |
| 5 | Emulator Crash Investigation | PENDING |
| 6 | Is This Safe? — universal safety checker (text/image/URL/QR/voicemail) | PENDING |
| 7 | Stop Silent Listening Automation Upgrade | PENDING |
| 8 | I Think I Was Scammed — panic button with step-by-step recovery | PENDING |
| 9 | Daily Safe Check-In — morning prompt, no-response alert to family | DONE — replaced by Feature 50 (Smart Family Alerts) |
| 10 | Real-Time Payment Warning — detect payment app launches | PENDING |
| 11 | Gift Card Alarm — detect gift card purchases | PENDING |
| 12 | Safe Contacts Number Lookup | PENDING |
| 13 | Weekly Security Report Card | PENDING |
| 14 | Scam of the Week Notification | PENDING |
| 15 | App Rename | DONE — superseded by Fix 24 |
| 16 | App PIN / Biometric Lock + API Key Encryption | PENDING |
| 17 | Family-Friendly Onboarding | PENDING |
| 18 | Privacy Promise Screen | PENDING |
| 19 | Camera Permission Guidance + Gallery Fix + Chat Agent Fix | PENDING |
| 20 | Old Icon — Palm Tree Theme | DONE — superseded by Fix 25 |
| 21 | SMS Shield Fix — NotificationListenerService | PENDING |
| 22 | Fix Photo Gallery Routing Bug | PENDING |
| 23 | Fix URL Checker Failures + Keyboard Covering Input | PENDING |
| 24 | Camera Permission Fix + AI Image Detection + URL Checker Fix | PENDING |
| 25 | Email Scanning + Messages/Email Tabs + Status Indicators | PENDING |
| 26 | Enhanced Privacy Monitor | PENDING |
| 27 | Home Screen Widget | PENDING |
| 28 | Voice Activation & Voice-Controlled Safety Checks | PENDING |
| 29 | URL Checker Complete Rewrite | PENDING |
| 30 | Email Account Setup & Inbox Scanning | PENDING |
| 31 | Social Media Scam Detection | PENDING |
| 32 | WiFi Security Monitor | PENDING |
| 33 | Connect Additional Security — Third-Party Services | PENDING |
| 34 | Comprehensive Messaging App Scanning | PENDING |
| 35 | Interactive Voice & Chat Agent with Personas and Voices | PENDING |
| 36 | Gmail & Outlook Email Integration | PENDING |
| 37 | Three-Tier Voice System — ElevenLabs → Google Neural → Android TTS | PENDING |
| 38 | Continuous Conversation Mode Button | PENDING |
| 39 | Interrupt Agent Speech With Voice | PENDING |
| 40 | Guided First-Run Permission Walkthrough | DONE |
| 41 | Bluetooth Security Monitor | PENDING |
| 42 | QR Code Scanner in Is This Safe | PENDING |
| 43 | NFC Security Monitor | PENDING |
| 44 | Simple Mode Toggle (Focused / Show All pill) | PENDING |
| 45 | Family Safety Alerts | PENDING |
| 46 | Voicemail Scam Scanner in Is This Safe | PENDING |
| 47 | Hidden Device Scanner — WiFi/IR/Magnetic/Bluetooth room sweep | DONE |
| 48 | Security News Feed — "What Scammers Are Up To" | DONE |
| Fix 1 | Text Messages Not Flowing Into App | PENDING |
| Fix 2 | Rename SMS → Messages on Home Screen | PENDING |
| Fix 3 | Rename Chat button → "Chat with Safe Companion" | PENDING |
| Fix 4 | Natural Engaging Voices + Intentionally Slow Persona | PENDING |
| Fix 5 | Voice Input Timeout Too Fast | DONE — superseded by Fix 23 |
| Fix 6 | Gmail OAuth google-services.json Not Configured | PENDING |
| Fix 7 | Remove Duplicate Stop Silent Listening Button | PENDING |
| Fix 8 | Remove Duplicate Yellow Talk Button | PENDING |
| Fix 9 | Intermittent Cannot Analyse Picture Gallery Error | PENDING |
| Fix 10 | Gmail Shows Name/Email Form Instead of Google OAuth | PENDING |
| Fix 11 | Voice Agent Not Working Like ChatGPT | PENDING |
| Fix 12 | ElevenLabs Falling Back to Android TTS — Diagnose | PENDING |
| Fix 13 | ElevenLabs Regex Crash + Gmail google-services.json | PENDING |
| Fix 14 | Gmail Auth Error After Correct google-services.json | PENDING |
| Fix 15 | Remove Debug Logging from Chat Interface | PENDING |
| Fix 16 | Replace Warning Text with Yellow Triangle VerdictIcon composable | PENDING |
| Fix 17 | Voice Interrupt False Triggers | DONE — superseded by Fix 30 |
| Fix 18 | Gallery Image Picker Completely Broken | PENDING |
| Fix 19 | Voice Input Too Impatient | DONE — superseded by Fix 23 |
| Fix 20 | Hello Greeting Crashes App When Tapped | PENDING |
| Fix 21 | Simple Mode Icon Toggle → Clear Pill Toggle (Focused/Show All) | PENDING |
| Fix 22 | Audio Blips and Bleeps in Voice Chat | PENDING |
| Fix 23 | Voice Cuts Off Mid-Sentence — Extended Timeouts + Done Speaking button | DONE |
| Fix 24 | Rename entire codebase to Safe Companion / com.safecompanion.app | DONE |
| Fix 25 | Replace App Icon with new split blue/green shield icon | DONE |
| Fix 26 | Icon Too Large — add 10% padding so shield outline visible at small sizes | DONE |
| Fix 27 | Onboarding: Show Continue button after returning from system Settings | DONE |
| Fix 28 | Onboarding: Route to App Settings → Permissions → Show All (fewer taps) | DONE |
| Fix 29 | Onboarding: Full SMS Notification Listener walkthrough with manufacturer tips | DONE |
| Fix 30 | Voice Self-Interruption — Definitive Fix (hardware state machine) | DONE |
| Fix 31 | Voice Interrupt Not Working During Agent Speech — two-recognizer fix | DONE |
| Fix 32 | QR Scanner: Show Open button on SAFE verdict only | PENDING |
| Fix 33 | Onboarding Screen Truncation — scroll, padding, Arrangement.Top | DONE |
| Fix 34 | Theme Fix — enforce single dark theme, remove all hardcoded colors | DONE |
| Fix 35 | Settings Screen — white background / light grey unreadable text | DONE |
| Fix 36 | Room Scanner — WiFi/BT/Audio scans stuck on "Waiting", no permission prompts | DONE |
| Fix 37 | "Tell Me More" notification → Message Detail Screen with Block/Delete/Share actions | DONE |
| Fix 38 | Messages/Emails — remove munged verdict label text under red/yellow/green icons | DONE |
| Fix 39 | Theme Overhaul — switch to light theme, black text, audit all screens | DONE |
| Fix 40 | Remove "What's This App?" from home screen (keep in Is This Safe only) | DONE |
| Fix 41 | Change "Disable This App" → "Uninstall or Modify This App" | DONE |
| 49 | Three-Tier Alert Levels (Off / Subtle / Grab Your Attention) | DONE |
| 50 | Smart Family Alerts — risk-interaction-based family notifications | DONE |
| 51 | Safety Points Gamification System | DONE |
| 52 | Trusted Caller Announcements | DONE |
| 53 | Scam Coaching After Close Calls | DONE |
| 54 | Quick Call Family Button | DONE |
| 55 | Daily Safety Tip | DONE |
| 56 | App Safety Checker — "What's This App?" in Is This Safe | DONE |
| 57 | Auto-Start on Boot — start guardian service when phone turns on | DONE |
| 58 | Background Guardian Service — persistent foreground service for monitoring | DONE |
| 59 | WiFi Connection Safety Monitor — alert on unsafe WiFi + VPN tips | DONE |
| 60 | QR Code Safety via Share Sheet — share QR URLs to Safe Companion | DONE |
| 61 | New App Install Auto-Check — auto-scan newly installed apps | DONE |
| 62 | On-Device SLM Scam Classifier | DONE |
| 63 | Hybrid Analysis (local-first, cloud fallback) | DONE |
| 64 | Security Audit Fixes (logging, keystore, cert pinning) | DONE |
| 65 | Anti-Reverse-Engineering (root/emulator/tamper detection) | DONE |
| 66 | Complete ProGuard/R8 Rules | DONE |
| 67 | Beta Test Distribution Mode (zero-config) | DONE |
| 68 | Demo Mode with Sample Data | DONE |
| Fix 42 | Remove debug logging / API key exposure in logs | DONE |
| Fix 43 | KeystoreManager plaintext fallback vulnerability | DONE |
| Fix 44 | OkHttp BODY logging in release builds | DONE |
| TaskA | Best practices & dependency review (deps, R8, allowBackup=false, ProGuard rules) | DONE |
| TaskB | Voice assistant tuning (cooldowns, lower TTS volume, AEC log, hint) | DONE |
| TaskC | WiFi Network Detail Screen (tap a network for full risk breakdown + VPN button) | DONE |
| TaskD | Network Device Discovery (ARP/OUI camera scan added to Room Scanner) | DONE |
| TaskE | Camera Notification Consolidation (NotificationListener + Security Hub screen) | DONE |
| TaskF | VPN Auto-Suggestion + Home VPN status pill | DONE |
| TaskG | Call Duration Tracking + Weekly Email Summary Worker | DONE |
| 62 | On-Device SLM Scam Classifier (local-first, no API key needed) | PENDING |
| 63 | Hybrid Analysis Repository (local SLM → Claude fallback) | PENDING |
| 64 | Security Audit Fixes (logging, keystore, cert pinning) | PENDING |
| 65 | Anti-Reverse-Engineering (root/emulator/tamper/signature checks) | PENDING |
| 66 | Complete ProGuard/R8 Rules + String Stripping | PENDING |
| 67 | Beta Test Distribution Mode (zero-config APK) | PENDING |
| 68 | Demo Mode with Sample Data (no API keys required) | PENDING |
| Fix 42 | Remove debug logging / API key prefix exposure in logs | PENDING |
| Fix 43 | KeystoreManager plaintext fallback vulnerability | PENDING |
| Fix 44 | OkHttp BODY logging → HEADERS only, wrapped in DEBUG check | PENDING |

---

## Key Feature Specs

### Feature 56 — App Safety Checker ("What's This App?")
Two entry points: (1) "Check an App on My Phone" card in Is This Safe, (2) "What's This App?" shortcut on HomeScreen using UsageStatsManager. AppCheckerScreen lists all installed apps with search + filter chips (All/Recently Installed/Not from Play Store). AppDetailScreen shows app identity card, permissions in plain English with emoji+color risk coding, and "Check This App with Safe Companion" button that sends to Claude Haiku. Verdict uses existing SAFE/SUSPICIOUS/DANGEROUS system. Action buttons: Uninstall (ACTION_DELETE intent), Disable (for system apps), Open App Settings, Tell My Family, Ask Safe Companion. Install source detection via getInstallSourceInfo (Play Store/Galaxy Store/Amazon/Pre-installed/Sideloaded/Unknown). Permissions: QUERY_ALL_PACKAGES, PACKAGE_USAGE_STATS, REQUEST_DELETE_PACKAGES. Results saved to SafetyCheckResultEntity with contentType="APP". Integrates with Family Alerts (Feature 50), Safety Points (Feature 51), Scam Coaching (Feature 53). Full spec in task.md.

### Fix 21 — View Mode Pill Toggle
Two-option pill top-right of HomeScreen AND SimpleModeScreen. "Focused" (simple mode) | "Show All" (full). Active = teal `#00897B` + bold white. Inactive = transparent + 45% white. `ViewModePill` composable in `ui/components/`. No ripple. Persisted in DataStore.

### Fix 22 — Audio Blips
(A) SpeechRecognizer tones — mute STREAM_MUSIC 400ms around startListening. (B) MediaPlayer click — 80ms fade-in, 200ms fade-out on ElevenLabs audio. (C) Audio focus — request AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE once per conversation not per utterance. (D) Error tones — mute STREAM_NOTIFICATION in onError. (E) Remove any ToneGenerator/beep calls. (F) Suppress notification sounds during active conversation. (G) Non-streaming endpoint for responses <500 chars. (H) 150ms delay before Android TTS fallback.

### Fix 31 — Voice Interrupt Two-Recognizer Architecture
**Problem:** Fix 30 state machine stops mic during SPEAKING — blocks user voice too.
**Solution:** Two independent SpeechRecognizer instances. `mainRecognizer` — existing, stopped during SPEAKING. `interruptRecognizer` — NEW, runs ONLY during SPEAKING, keyword-only.
Interrupt keywords: stop, wait, hold on, pause, quiet, enough, thanks, thank you, ok, okay, got it, i understand, yes, no + grace, james, sophie, george.
Start interrupt listener 300ms after entering SPEAKING state (lets main recognizer release mic). Short timeouts: COMPLETE_SILENCE=1500ms, POSSIBLY_COMPLETE=1000ms. PARTIAL_RESULTS=true.
Auto-restart on onResults (no keyword) and onError (300ms delay) while still SPEAKING.
Self-interrupt protection: if detected keyword exists in `currentAgentSpeech` → echo, ignore.
State wiring: SPEAKING entry → stopListeningImmediately() then startInterruptListener(). SPEAK_COOLDOWN entry → stopInterruptListener() always. onUserInterrupt() → stop interrupt listener → stop ElevenLabs → transitionTo(SPEAK_COOLDOWN).
`currentAgentSpeech`: set to full text BEFORE audio plays, cleared AFTER cooldown starts.
Visual hint during agent speech: "Say 'stop' or 'hold on' to interrupt" at 40% opacity.
Manual stop button still calls onUserInterrupt() directly.
Test: 3 consecutive 5-exchange conversations, zero self-responses, all 8 keyword tests pass.

### Fix 32 — QR Scanner Open Button on Safe Results
Show action button ONLY when verdict == SAFE. Never show for SUSPICIOUS or DANGEROUS — remove entirely from composable tree, not just hidden/disabled.
Button label + icon varies by QR type: URL→"Open Website" (OpenInBrowser), EMAIL→"Open in Email App", PHONE→"Call This Number" (ACTION_DIAL not ACTION_CALL — pre-fill only, no auto-dial), SMS→"Open in Messages", WIFI→"Connect to WiFi", CONTACT→"Save Contact", LOCATION→"Open in Maps", TEXT→"Copy Text" (clipboard, no intent).
For URL type: show decoded URL in small text above button (13sp, 55% white, 2 lines max).
WiFi (Android 10+): WifiNetworkSuggestion API. Parse WIFI:S:<ssid>;T:<type>;P:<password>;; format.
ActivityNotFoundException → snackbar "No app found" + show decoded content in selectable text field.
Test: safe URL opens browser ✓, suspicious shows NO button ✓, dangerous shows NO button ✓, phone opens dialer (no auto-call) ✓, text copies to clipboard ✓.

### Feature 40 — Permission Walkthrough (DONE)
8 steps: mic→notifications→notification_listener→accessibility→camera→contacts→phone→battery. Progress bar, large icon, plain English WHY. onResume auto-checks + auto-advances (500ms). Completion screen shows active protection count. Settings entry: "Setup & Permissions" card at top.

### Feature 44 — Simple Mode
Pill toggle (Fix 21). Layout: inert greeting + 170dp circular Chat button center + 72dp Is This Safe FAB bottom right + up to 5 Recent Alerts or "All clear."

### Feature 45 — Family Safety Alerts
Opt-in, consent dialog. FamilyContact: name/phone/email/notifyViaSms/notifyViaEmail/alertLevel (ALL/HIGH/CRITICAL). 11 triggers incl. SUSPICIOUS_CALL_1MIN, SUSPICIOUS_CALL_5MIN, GIFT_CARD, WIRE_TRANSFER, CRYPTO, PANIC_BUTTON. Rate limit 1/10min. SMS via SmsManager. Family alert SMS NOT suppressed by Fix 22 notification suppression.

### Feature 46 — Voicemail Scanner
In "Is This Safe?" Method 1: play voicemail on speaker, app listens via mic, live transcript. Method 2: manual text entry. Timeouts: 8s silence, 5s mid-speech. 13 scam patterns in Claude prompt. Result shows "Share with Family" → Android share sheet.

### Feature 47 — Hidden Device Scanner (6 scan types)

**General architecture — dynamic capability detection**
Build scan list at runtime. If hardware unavailable → hide card entirely (no "Skipped" message). If permission missing → show inline grant button, not an error.

```kotlin
data class ScanCapability(val type: ScanType, val isAvailable: Boolean,
    val requiresPermission: Boolean, val permissionGranted: Boolean)
// Filter to only isAvailable=true entries before rendering
```

"Scan Room" button runs all available+permitted scans in sequence with progress label "Scanning WiFi... (1/5)". After all complete, show summary verdict card at top: green shield "Room appears safe" or yellow/red "X potential issues found."

Honest limitations card always shown at bottom — not dismissible.

---

**Scan 1 — WiFi**
DO NOT use CHANGE_WIFI_STATE — remove from manifest. Use passive `wifiManager.scanResults` (cached, no trigger). Permissions: ACCESS_WIFI_STATE + ACCESS_FINE_LOCATION only.

Per-network risk labelling:
- SAFE (green): WPA2 or WPA3 encryption
- CAUTION (yellow): WPA/WEP only, hidden SSID (null/empty), or very strong signal >-40dBm from unknown device
- SUSPICIOUS (red): open network (no WPA/WEP in capabilities), SSID matches camera patterns (IPCam, HiCamera, vstarcam, IPCAM, HiIPC, GoCamera, iCamera, "camera", "cam_", "spy", "hidden"), or MAC OUI matches Hikvision (3C:A3:08, BC:AD:28, C0:56:E3), Dahua (90:02:A9, 4C:11:AE), Reolink (EC:71:DB, 48:02:2A), Axis (00:40:8C, AC:CC:8E)

Display: scrollable list — each row shows lock icon, SSID (or "Hidden Network"), risk badge, signal bars (4 levels from dBm), encryption pill. Summary line: "X networks found. Y look suspicious." If no results: "No WiFi networks detected. Make sure WiFi is turned on."

---

**Scan 2 — Infrared Camera**
Tapping IR card launches full-screen camera preview. Never skip silently.
Use front camera (less likely to have IR cut filter). Add "Switch Camera" button top-right.

Frame analysis (ImageAnalysis.Analyzer, sample every 4px):
- Flag pixels where R>200, G<100, B<100
- Cluster analysis: flag clusters of 20+ adjacent IR pixels
- Draw yellow circle overlay on each detected cluster

Baseline on launch: if mean red channel <30 in dark scene → phone likely has strong IR filter → show banner "Your camera may filter IR light. Results may be limited."

Status bar at bottom: "Scanning..." (pulsing) → "⚠️ Possible IR source detected" if clusters found → "✓ No IR sources detected" after 10s clean.

Small permanent text: "Results vary by phone model. Works best in a darkened room."

---

**Scan 3 — Magnetic**
Check `sensorManager.getDefaultSensor(TYPE_MAGNETIC_FIELD) != null` first.
If sensor absent → hide card entirely, show nothing.
If present → keep metal-detector UX: 3s baseline calibration, flag spikes >25µT above baseline, needle/gauge visual, haptic pulse on spike.

---

**Scan 4 — Bluetooth**
If BLUETOOTH_SCAN or BLUETOOTH_CONNECT missing → show inline inside card:
- "To scan for suspicious Bluetooth devices, Safe Companion needs permission to see nearby devices."
- Large teal button "Grant Bluetooth Permission" → RequestMultiplePermissions launcher
- On grant → run scan immediately
- On deny → "Bluetooth scan skipped. You can enable this in Settings → Apps → Safe Companion → Permissions." + "Open Settings" link
Never show raw error state.

Device labelling: SUSPICIOUS = generic/unnamed devices, camera-related names, networking class. SAFE = audio/phone/health/wearable class. REVIEW = uncategorized.

---

**Scan 5 — Mirror Check (guided, no sensors)**
Guided step-through card. Works on all devices. Uses flashlight API only.

Steps (one at a time, Next advances):
1. "Turn off the lights or find the darkest corner in the room."
2. "Hold your phone flashlight directly against the mirror surface." + "Turn on Flashlight" button (CameraManager torch)
3. "Look through the mirror toward the light. A real mirror appears dark behind the glass. A one-way mirror lets you see through."
4. Two result buttons: "I can see through it" → SUSPICIOUS | "It looks dark/solid" → LOOKS OK

Results: SUSPICIOUS → "⚠️ This mirror may be one-way glass. One-way mirrors are sometimes used to hide cameras. Consider requesting a different room or contacting the property manager." LOOKS OK → "✓ This mirror appears to be a standard mirror."

Always show: "This is a visual guide only, not a definitive test."
CRITICAL: turn flashlight OFF automatically on back/exit. Never leave torch on accidentally.

---

**Scan 6 — Ultrasonic & Audio Sweep**
Requires RECORD_AUDIO. If not granted → same inline permission pattern as Bluetooth.
Record 5-second audio sample at highest available sample rate (target 44100Hz).
Use Goertzel algorithm (no new library) to check 8 target frequencies: 18000, 18500, 19000, 19500, 20000, 20500, 21000, 21200 Hz.

```kotlin
fun goertzel(samples: DoubleArray, targetFreq: Double, sampleRate: Int): Double {
    val n = samples.size
    val k = (0.5 + n * targetFreq / sampleRate).toInt()
    val omega = 2.0 * Math.PI * k / n
    val coeff = 2.0 * cos(omega)
    var q1 = 0.0; var q2 = 0.0
    for (sample in samples) { val q0 = coeff * q1 - q2 + sample; q2 = q1; q1 = q0 }
    return q1 * q1 + q2 * q2 - q1 * q2 * coeff
}
```

Flag if any target frequency energy > 8× baseline (average energy at 1000Hz). UI: animated waveform bar during scan + frequency histogram (low/mid/high bands).

DETECTED: "⚠️ Unusual high-frequency signal detected around [X] Hz. Some surveillance devices emit signals in this range. Air conditioning and electronics can also cause this." CLEAN: "✓ No unusual audio frequencies detected."

---

**Manifest permissions for Feature 47**
ADD: ACCESS_WIFI_STATE, ACCESS_FINE_LOCATION, CAMERA, BLUETOOTH_SCAN, BLUETOOTH_CONNECT, RECORD_AUDIO
REMOVE: CHANGE_WIFI_STATE

**Files**
RoomScannerScreen.kt · RoomScannerViewModel.kt · WifiScanner.kt · IrScanner.kt · MagneticScanner.kt · BluetoothScanner.kt · MirrorCheckScreen.kt (NEW) · UltrasonicScanner.kt (NEW)

### Feature 48 — Security News Feed
RSS feed from 5 sources, no API keys. WorkManager sync every 6 hours. OkHttp + XmlPullParser only — no new libraries.
Sources: AARP (`aarp.org/money/scams-fraud/rss.html` red #E31837) · FTC (`ftc.gov/rss/alerts` blue #1A3A6B) · FBI (`fbi.gov/feeds/fbi-news-elder-fraud/rss.xml` navy #003366) · BBB (`bbb.org/rss/scam-alerts` blue #0066CC) · Snopes (`snopes.com/category/facts/scams/feed/` grey #555).
Room: NewsArticleEntity, upsert, keep 100 scam-relevant articles only, delete >30 days. ID = MD5 of link.
Home screen: "What Scammers Are Up To" section at bottom, 5 articles, "See all" link.
Article card: colored source pill + relative timestamp + 2-line title (17sp) + 2-line summary (14sp grey) + unread left border accent. Tap → mark read + Chrome Custom Tab.
Full NewsScreen: all articles grouped by date, pull-to-refresh, filter chips (All|AARP|FTC|FBI|BBB|Snopes), "Mark all read," add to bottom nav.
Simple Mode: single most recent unread article as banner only.
Files: RssFeedParser.kt · ScamArticleFilter.kt (NEW) · NewsRepository.kt · NewsArticleEntity/Dao.kt · NewsSyncWorker.kt · NewsScreen.kt · NewsViewModel.kt · ArticleCard.kt · NewsSection.kt

**Scam relevance filtering — ScamArticleFilter.kt (NEW)**
RSS feeds mix general news with scam content. Filter every parsed article BEFORE saving to Room. Discard anything not scam-related.

Article PASSES filter if: (title OR description contains at least one REQUIRED keyword) AND (title does NOT match any EXCLUDE pattern).

REQUIRED keywords (case-insensitive, check title+description combined):
scam, fraud, phishing, smishing, vishing, impersonat, fake, con , swindle, deceptive, mislead, trick, deceiv, exploit, victim, identity theft, stolen, hack, breach, malware, ransomware, spyware, robocall, spoofing, grandparent, romance scam, lottery, sweepstake, prize, gift card, wire transfer, cryptocurrency, bitcoin, investment fraud, ponzi, pyramid, tech support, irs scam, social security scam, medicare scam, charity fraud, disaster relief fraud, money mule, advance fee, overpayment, counterfeit, fake check, forged, suspicious, warning, alert, caution, beware, do not click, do not call, consumer alert, data breach, account takeover, credential, password stolen, unauthorized access, elder, senior, retiree, pension

EXCLUDE patterns (regex on title only — catches general policy/org news):
- "^(FTC|FBI|BBB|AARP)\s+(announces|releases|publishes|updates|seeks|proposes|finalizes|issues rule)" 
- "annual report"
- "press release"
- "job opening|hiring|career"
- "budget|appropriation|congress|legislation|bill passed"
- Month + year + "complaint" pattern (monthly complaint stats, not actionable)

Apply filter in RssFeedParser before returning: `articles.filter { ScamArticleFilter.isScamRelevant(it.title, it.description) }`

If entire feed returns 0 articles after filtering (source changed structure) → log warning, show cached articles only, do NOT show error to user.
If fewer than 3 articles pass across all sources on first-ever sync → show "Checking for scam alerts... Check back soon." instead of empty list.

### VerdictIcon Composable (Fix 16)
DANGEROUS: red circle + white ✕. SUSPICIOUS: yellow triangle (Canvas Path) + black !. SAFE: green circle + white ✓. CHECKING: grey progress. Replace ALL "Warning" text throughout app.

### Feature 41 — Bluetooth Monitor
Scan bondedDevices. SUSPICIOUS: generic names, networking/misc class. SAFE: audio/phone/health/wearable. REVIEW: uncategorized. "Ask Safe Companion" → Claude. Warning banner if SUSPICIOUS found.

### Feature 42 — QR Scanner
ML Kit (`com.google.mlkit:barcode-scanning:17.3.0`) + CameraX. Animated scan line. 9 QR types. Instant pre-analysis warnings for crypto/IP URL/shorteners/HTTP. Claude prompt includes 8 quishing patterns. On SAFE verdict show Fix 32 open button.

### Feature 43 — NFC Monitor
`NfcAdapter.enableForegroundDispatch()`. HIGH: IP URLs/crypto/wallet connect. MEDIUM: shorteners/HTTP/AAR. LOW: no NDEF/payment cards. `android:required="false"` critical. HIGH → full screen warning.

---

## Reminders (Do Not Start Until Instructed)

**REMINDER A — Phase 8 Security Hardening:** R8 obfuscation, root/emulator/tamper detection, Play Integrity API, certificate pinning for Anthropic API, TLS 1.2+, SQLCipher, FLAG_SECURE, prompt injection prevention, android:allowBackup="false".

**REMINDER B — Phase 7 Automated Testing:** JUnit+MockK unit tests (20+ scam samples, 95% security coverage), AndroidX integration tests, Compose UI tests, GitHub Actions CI, 70% overall coverage minimum.

**REMINDER C — Phase 9 Overnight Audit:** Full OWASP MASVS L1+L2, Google Play policy compliance, GDPR/CCPA/COPPA, privacy policy (`assets/privacy_policy.html`), terms of service, BBB readiness. Output: `SafeCompanion_AuditReport.md`.

**REMINDER D — Instructional Videos (Post-Launch):** 7 videos: (1) Scanning physical mail — highest priority. (2) Scanning computer screen. (3) QR code pre-check. (4) Voicemail scam check. (5) Chat agent / Grace. (6) Family alerts setup (for adult children). (7) Simple Mode. Slow/warm narration, large captions, real hands on real phone.

**REMINDER E — Phase 10: Cloud Fraud Detection API (Azure)**

Build a cloud-hosted fraud scoring service on Azure that the Android app calls for
real-time scam verdicts. This replaces/augments the current approach of sending
everything to Claude Haiku by adding fast, cheap, deterministic checks first —
Claude remains the final arbiter for ambiguous cases only.

**Why this matters:**
- Claude Haiku costs per-call and adds latency; deterministic URL/phone lookups are free or near-free and instant
- A central API accumulates a shared threat database across ALL Safe Companion users
- Enables pattern detection across users (e.g., 50 users all got the same scam SMS today)
- External reputation APIs (Google Safe Browsing, VirusTotal) can't be called directly from the Android app without exposing API keys

**Architecture:**

```
┌─────────────────┐       HTTPS/JSON        ┌──────────────────────────┐
│  Safe Companion  │  ───────────────────►   │   Azure Function App     │
│  Android App     │  ◄───────────────────   │   (Python / C#)          │
│                  │    { riskScore: 0.87,   │                          │
│  Sends:          │      verdict: "DANGER", │   1. URL Reputation      │
│  - URL           │      reasons: [...] }   │   2. Phone # Lookup      │
│  - phone number  │                         │   3. Text Classifier      │
│  - message text  │                         │   4. Pattern Database     │
│  - sender info   │                         │   5. Cross-User Intel     │
│  - metadata      │                         │   6. Claude (fallback)    │
└─────────────────┘                          └──────┬───────────────────┘
                                                    │
                              ┌──────────────────────┼──────────────────────┐
                              │                      │                      │
                    ┌─────────▼────────┐  ┌─────────▼────────┐  ┌─────────▼────────┐
                    │ Google Web Risk   │  │ Azure Cosmos DB   │  │ Azure ML          │
                    │ API (URL check)   │  │ (threat database  │  │ (text classifier  │
                    │                   │  │  + cross-user     │  │  trained on scam   │
                    │ VirusTotal API    │  │  intelligence)    │  │  corpus)           │
                    │ (URL + file scan) │  │                   │  │                   │
                    └──────────────────┘  └──────────────────┘  └──────────────────┘
```

**Scoring Pipeline (executed in order, short-circuits on high confidence):**

```
Step 1 — Deterministic Checks (< 50ms, free)
  ├── Known scam phone numbers (local DB of reported numbers)
  ├── Known scam URL domains (local blocklist, updated daily)
  ├── Regex pattern matches (gift card, wire transfer, urgency phrases)
  └── If confidence > 0.95 → return immediately, skip remaining steps

Step 2 — External Reputation APIs (< 200ms, metered)
  ├── Google Web Risk API (replaces deprecated Safe Browsing v4)
  │   - URL reputation: SAFE / SOCIAL_ENGINEERING / MALWARE / UNWANTED_SOFTWARE
  │   - Commercial use allowed (unlike Safe Browsing v4 which is non-commercial only)
  ├── VirusTotal API (URL + domain scan)
  │   - Free tier: 4 requests/min, 500/day, 15.5K/month
  │   - Premium: higher limits if needed at scale
  └── If external APIs agree on verdict with high confidence → return

Step 3 — ML Text Classifier (< 100ms, Azure compute cost only)
  ├── Trained on corpus of known scam messages vs legitimate messages
  ├── Features: urgency score, financial keywords, impersonation patterns,
  │   grammar anomalies, sender reputation, time-of-day, message length
  ├── Model: Azure ML deployed as managed endpoint (or embedded in Function)
  └── XGBoost or similar lightweight model — not a large LLM

Step 4 — Cross-User Intelligence (< 50ms, Cosmos DB lookup)
  ├── Hash the message content (minus personal details)
  ├── Check: have other Safe Companion users reported this exact message?
  ├── Check: is this phone number/URL trending across users in the last 24h?
  └── Crowd-sourced threat intelligence — gets smarter with more users

Step 5 — Claude AI Analysis (< 2s, per-call cost — ONLY if steps 1-4 are inconclusive)
  ├── Send to Claude Haiku with scam detection prompt
  ├── Include context from steps 1-4 as evidence
  └── Claude makes final judgment on ambiguous cases
```

**API Contract:**

```
POST https://safecompanion-api.azurewebsites.net/api/v1/analyze

Request:
{
  "type": "sms" | "email" | "call" | "url" | "qr",
  "content": {
    "text": "Your bank account has been...",
    "sender": "+18005550199",
    "urls": ["https://suspicious-bank.com/verify"],
    "subject": null,
    "metadata": {
      "timestamp": "2026-03-14T10:30:00Z",
      "deviceLocale": "en-US"
    }
  },
  "clientVersion": "1.2.0",
  "anonymousUserId": "sha256-of-device-id"  // for cross-user intel, not PII
}

Response:
{
  "riskScore": 0.87,           // 0.0 (safe) to 1.0 (definite scam)
  "verdict": "DANGEROUS",      // SAFE | SUSPICIOUS | DANGEROUS
  "confidence": 0.92,          // how sure the system is
  "reasons": [
    "URL domain registered 2 days ago",
    "Message uses urgency tactics: 'act now or lose access'",
    "Phone number reported by 23 other users this week",
    "Google Web Risk: SOCIAL_ENGINEERING"
  ],
  "recommendations": [
    "Do not click any links in this message",
    "Do not call this number back",
    "Block this sender"
  ],
  "scamType": "BANK_IMPERSONATION",
  "pipeline": {                // transparency: which steps fired
    "deterministicMatch": true,
    "googleWebRisk": "SOCIAL_ENGINEERING",
    "virusTotal": { "positives": 12, "total": 70 },
    "crossUserReports": 23,
    "claudeUsed": false
  }
}
```

**Azure Resources Needed:**
- Azure Function App (Consumption plan — pay-per-execution, ~$0.20/million executions)
- Azure Cosmos DB (Serverless — for threat database + cross-user intel)
- Azure Key Vault (API keys for Google Web Risk, VirusTotal, Anthropic)
- Azure ML Managed Endpoint (optional — for text classifier, or embed in Function)
- Azure Application Insights (monitoring + alerting)
- Google Web Risk API key (commercial Safe Browsing replacement)
- VirusTotal API key (free tier to start, upgrade as user base grows)

**Android App Integration:**
- New Retrofit service: `FraudDetectionApiService.kt`
- New repository: `CloudFraudRepository.kt`
- Call cloud API FIRST, fall back to local Claude analysis if API unreachable
- Cache verdicts locally (Room) — same URL/phone checked twice = instant response
- Offline mode: if no network, use local heuristics only (existing Claude-based flow)
- API key stored in BuildConfig (not hardcoded), rotated via Azure Key Vault

**Privacy Considerations:**
- NEVER send contact names, user's real name, or device identifiers
- anonymousUserId = SHA-256 hash of Android ID (not reversible)
- Message text is sent for analysis but NOT stored beyond 24 hours
- Cross-user intel uses content hashes, not raw messages
- Document all data handling in privacy policy update (Phase 9)
- GDPR: provide data deletion endpoint

**Cost Estimate (1,000 active users):**
- Azure Functions: ~$1-5/month (Consumption plan)
- Cosmos DB Serverless: ~$5-10/month
- Google Web Risk: free tier covers 10K lookups/day
- VirusTotal: free tier covers 500/day (sufficient for 1K users with caching)
- Azure ML endpoint: ~$10-20/month if used, $0 if classifier embedded in Function
- Total: ~$15-35/month to start, scales linearly

**Separate Repository:**
This is a separate project from the Android app:
`safe-companion-api/` — Python (Azure Functions) or C# (.NET isolated worker)
Claude Code can scaffold and deploy this. Use `azd` (Azure Developer CLI) for infra-as-code.

**Build Order:**
1. Scaffold Azure Function App with single `/analyze` endpoint
2. Implement Step 1 (deterministic checks) with hardcoded blocklists
3. Add Google Web Risk API integration (Step 2a)
4. Add VirusTotal API integration (Step 2b)
5. Add Cosmos DB for threat database + cross-user reporting (Step 4)
6. Add Claude fallback for ambiguous cases (Step 5)
7. Android app integration: FraudDetectionApiService + CloudFraudRepository
8. ML text classifier (Step 3) — train on collected data after launch
9. Monitoring, alerting, cost optimization

---

## Build Order

```
Phase 1 — COMPLETE ✓
Fix 24 ✓ → Fix 25 ✓ → Fix 26 ✓ → Fix 27 ✓ → Fix 28 ✓ → Fix 29 ✓ → Fix 30 ✓ → Feature 40 ✓

Current Sprint (complete before Phase 2):
Feature 47 (in progress) → Fix 31 → Fix 32 → Feature 48

Phase 2 — Core Protection:
Fix 2 → Fix 3 → Fix 7 → Fix 8 → 1 → 26 → 41 → 43 → 2 → 3 → 4 → 5
→ Fix 1 → 21 → 34 → 25 → 30 → 36 → Fix 6 → Fix 10 → Fix 14 → 31 → 32

Phase 3 — Safety Checker:
29 → 22 → 23 → Fix 9 → Fix 18 → 24 → 42 → Fix 32 → 47 → 46 → 6 → 7 → 19

Phase 4 — Senior Companion:
8 → 9 → 10 → 11 → 12 → 13 → 14 → 45

Phase 5 — Voice + Widget + Chat:
27
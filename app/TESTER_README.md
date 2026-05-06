# Safe Companion — Beta Testing Guide

## Installation
1. Install the APK on your Android phone (Android 8.0+)
2. Open Safe Companion
3. Walk through the permission setup (the app guides you)
4. **That's it!** No API keys or accounts needed.

The home screen seeds three sample alerts (red / yellow / green) on first launch
so you can see what each verdict looks like. Each one is tagged `[SAMPLE]` in
its reason text.

## What works without any API keys
- ✅ Scam text/SMS detection (on-device classifier)
- ✅ Suspicious URL checking
- ✅ QR code safety scanning
- ✅ App safety checker ("What's This App?")
- ✅ Room scanner (WiFi, Bluetooth, IR, magnetic, ultrasonic)
- ✅ Local network camera discovery
- ✅ Security news feed
- ✅ Family safety alerts (SMS-based)
- ✅ Daily safety tips
- ✅ Home screen widget

## Optional — unlock voice chat
To enable the AI voice assistant (Grace, James, Sophie, George):
1. Open **Settings** (gear icon)
2. Tap **Add Claude API Key**
3. Get a key from <https://console.anthropic.com> (free trial available)
4. Paste it in

## Optional — premium voice quality
For natural-sounding voices:
1. Open **Settings**
2. Tap **Add ElevenLabs Key**
3. Get a key from <https://elevenlabs.io> (free tier available)

## Testing checklist
- [ ] App opens and shows home screen
- [ ] Sample alerts visible (marked `[SAMPLE]`)
- [ ] **Is This Safe?** → type a suspicious message → see verdict
- [ ] **Is This Safe?** → scan QR code → see verdict
- [ ] **What's This App?** → shows installed apps → check one
- [ ] **Scan This Room** → all available scans complete
- [ ] Settings → all options accessible
- [ ] Notification appears for sample suspicious message

## Reporting issues
Take a screenshot and share the device model + Android version. Include the
relevant lines from `adb logcat -s SafeHarbor` if the bug involves a crash.

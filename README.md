# Guardian Angel

An AI-powered Android app that protects elderly users from scam calls, fraudulent SMS messages, and suspicious emails. Guardian Angel uses Claude AI to analyse incoming communications in real time and alert family members when threats are detected.

## Features

- **SMS Shield** — Automatically scans incoming text messages for scam patterns and flags suspicious content
- **Call Shield** — Screens calls in real time using on-device transcription and AI analysis; overlays a warning banner during live calls
- **Chat with Guardian** — A friendly AI companion the user can ask anything, with voice input and text-to-speech output
- **Family Alerts** — Notifies designated family contacts when a threat is detected
- **Trusted Numbers** — Whitelist contacts that are never screened
- **Accessible UI** — Large text option, clear visual design with high-contrast colours

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Kotlin |
| UI | Jetpack Compose |
| Architecture | MVVM + Repository |
| AI | Claude Haiku (`claude-haiku-4-5-20251001`) via Anthropic API |
| DI | Hilt |
| Local DB | Room |
| Preferences | DataStore |
| Networking | Retrofit + OkHttp |
| Min SDK | 26 (Android 8.0) |
| Target SDK | 35 (Android 15) |

## Setup

### Prerequisites

- Android Studio Hedgehog or later
- An [Anthropic API key](https://console.anthropic.com)

### Build

1. Clone the repo:
   ```bash
   git clone https://github.com/justicegitworkin/guardian-angel.git
   cd guardian-angel
   ```

2. Open in Android Studio and let Gradle sync.

3. Run on a device or emulator (API 26+).

### First Launch

On first launch you'll be guided through onboarding:

1. Enter your name
2. Paste your Anthropic API key
3. Test the connection
4. Grant the required permissions (SMS, Phone, Microphone, Notifications)

## Permissions

| Permission | Purpose |
|---|---|
| `RECEIVE_SMS` / `READ_SMS` | Read incoming messages for scam detection |
| `READ_CALL_LOG` | Log screened calls |
| `RECORD_AUDIO` | Voice input in chat and call transcription |
| `FOREGROUND_SERVICE` | Keep call screening active during calls |
| `POST_NOTIFICATIONS` | Alert the user and family contacts |
| `SEND_SMS` | Send family alert messages |

## Project Structure

```
app/src/main/java/com/guardianangel/app/
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

## License

MIT

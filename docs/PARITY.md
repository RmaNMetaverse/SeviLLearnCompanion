# Platform parity

Every product change must preserve equivalent user-visible behavior across Android, iOS and web. Shared server improvements apply to all clients; client changes require corresponding changes in the other two. Platform APIs and layouts may differ. Update this table and relevant API examples when adding features.

| Capability | Web | Android | iOS |
|---|---|---|---|
| Saved course profile and language preferences | Implemented | Implemented | Source implemented |
| Sessions and permission acknowledgment | Implemented | Implemented | Source implemented |
| Audio capture and local recovery | IndexedDB checkpoints | Foreground service/private files | AVAudioRecorder/private files |
| Board camera during recording | Separate video-only stream | In-app CameraX | In-app AVFoundation/video-only session |
| Capture offsets and attachment uploads | Implemented | Implemented | Source implemented |
| Transcript editing and evidence display | Implemented | Implemented | Source implemented |
| Live AI notes and public-source discovery | Shared API | Shared API | Shared API |
| PDF download/export | Download | Document picker | Quick Look/share |
| Session and asset deletion | Implemented | Implemented | Source implemented |
| Eight-language interface | Shared catalog | Shared catalog | Shared catalog |
| Server token | Browser session | Private app preferences | Keychain |

## Verification

- Shared API: local automated workflow/security/PDF tests.
- Web: TypeScript and production build; local browser verification.
- Android: Windows debug APK build with the installed SDK. No physical device was connected during initial development; recording/camera runtime checks remain required.
- iOS: Windows cannot run Xcode. A macOS build job is included in CI; do not claim a successful Apple build unless that job or a Mac build has actually passed. Physical iPhone/iPad checks remain required even after a successful simulator build.

## Release rule

No feature is production-verified merely because source compiles. Shared language catalogs must pass `scripts/sync-locales.py --check`. CI must check the API contract and compile the three clients. Record platform-specific limitations rather than silently removing a feature from one client.

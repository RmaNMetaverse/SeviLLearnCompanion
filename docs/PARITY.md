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
| Automatic GitHub prerelease downloads | Standalone server archive | Debug-signed APK | Simulator app + unsigned device IPA |
| AI provider dropdown | OpenAI/Gemini/Claude | OpenAI/Gemini/Claude | OpenAI/Gemini/Claude |
| Claude audio-service selection | Explicit Gemini/OpenAI choice | Explicit Gemini/OpenAI choice | Explicit Gemini/OpenAI choice |
| Generated-note provider provenance | Displayed | Displayed | Displayed |

## Verification

- Shared API: 16 local automated workflow/security/PDF tests, including upload retry deduplication and browser-origin rejection.
- Web: TypeScript and production build; local browser verification.
- Android: Windows debug APK build with the installed SDK. No physical device was connected during initial development; recording/camera runtime checks remain required.
- iOS: the source compiled successfully with Xcode on GitHub's macOS runner in [run 41223414740](https://github.com/RmaNMetaverse/SeviLLearnCompanion/actions/runs/41223414740). Physical iPhone/iPad checks remain required. Windows itself did not run Xcode.
- Web browser checks: generated sample handout, Turkish interface switch, phone-width layout without horizontal overflow, and a successful PDF download. Local screenshots are retained under ignored `artifacts/`.
- A live Gemini-only test passed with synthetic audio, a board image, a reading resource, structured notes and PDF export. The key remains in ignored local configuration. OpenAI and Claude calls have routing/format tests; live calls were not made for those providers in this change. Real classroom accuracy and long-session behavior remain unverified.
- Docker deployment is configured but has not been run locally. Containers and public deployment need a separate environment check before use.

## Release rule

No feature is production-verified merely because source compiles. Shared language catalogs must pass `scripts/sync-locales.py --check`. CI must check the API contract and compile the three clients. Record platform-specific limitations rather than silently removing a feature from one client.

Each successful pushed `main` revision publishes the corresponding web, Android and iOS assets together. Release publication waits for the shared API checks and all three platform builds. See [RELEASES.md](RELEASES.md) for the difference between simulator, unsigned device and signed installable packages.

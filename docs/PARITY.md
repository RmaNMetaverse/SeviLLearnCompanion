# Platform parity

All product changes must apply to native Android, native iOS and web. Device libraries are intentionally separate; cloud sync will be added later.

| Behavior | Android | iOS/iPadOS | Web |
|---|---|---|---|
| Independent library, no laptop server | Private SQLite + files | Private SQLite + Application Support files | IndexedDB metadata + blobs |
| OpenAI/Gemini/Claude selection | Native dropdown | Native picker | Dropdown |
| Personal credentials | Keystore-encrypted | Keychain | Tab memory; reload clears |
| Direct transcription/notes/search | OkHttp HTTPS | URLSession HTTPS | Browser fetch HTTPS |
| Courses, sessions, attached PDFs/text | Local | Local | Local |
| Recording and pending recovery | Foreground recording service, private files | AVAudioRecorder, private files | MediaRecorder, IndexedDB checkpoints |
| Board capture during recording | CameraX without audio | Camera capture without microphone session | Separate camera stream |
| Photo capture time and original | Local asset offset | Local asset offset | Local asset offset |
| Evidence and citation checks | Shared schema + native validation | Shared schema + Codable validation | Shared schema + validation |
| PDF including photos | Android PdfDocument | UIGraphicsPDFRenderer/CoreText | Browser Print/Save as PDF |
| Offline sample | Synthetic contract sample | Same sample | Same sample |
| Eight interface languages | Shared catalog | Shared catalog | Shared catalog |
| Green responsive workspace | Compose bottom nav / rail / sidebar | SwiftUI bottom nav / rail / sidebar | Responsive bottom nav / rail / sidebar |
| Session tabs | Notes, Chat, Transcript, Materials | Same four | Same four |
| Saved session tutor | Native local chat + direct provider | Native local chat + direct provider | IndexedDB chat + direct provider |
| Link references and excerpts | Session-scoped, HTTPS validation | Same | Same |
| Chat citations and verified links | Source-ID/URL checks, explicit search | Same | Same |
| Retry and clear conversation | Stable request IDs, local history | Same | Same |
| Cloud sync/account | Future | Future | Future |

Native PDFs are generated as bytes; web opens a print dialog to save a PDF. Native clients check PDF page count; browser sends chapters without page count prevalidation. Other limits and interruption behavior are documented in README.

## Verification

The new device-library change includes browser tests for offline CRUD, original bytes/timestamps, shared course resources, key preflight, direct Gemini requests and citation rejection. Android instrumentation and iOS simulator tests cover local persistence, file retention, PDF generation and secure credential storage. GitHub releases are gated on these tests and all builds.

For the 0.2 workspace/chat change, local Windows Android app and instrumentation-test APK compilation passed, as did web type checking, production export and all 10 browser-engine tests. New tests cover all three vendor request formats, session isolation, saved follow-ups, idempotent retry, link/citation rejection and bounded evidence. Native tests add session link/history cleanup and context validation; Android also exercises navigation/sample chat and exports UI screenshots.

The 0.2 app passed all builds and tests in [GitHub run 15](https://github.com/RmaNMetaverse/SeviLLearnCompanion/actions/runs/41297296732): 31 Python tests, 10 web engine tests, 5 Android instrumentation tests and the iOS simulator suite. macOS built both the simulator app and unsigned device IPA. Android phone navigation and opening the session tutor passed. Screenshot retention is being corrected because connected tests uninstall the app and remove its private output directory.

Web visual checks cover 320, 390, 768 and 1280 pixel widths, bottom navigation/rail/sidebar and long German labels. A real Gemini test with synthetic data verified transcript grounding, follow-up context, recognition that a topic was deferred to the next lecture, an attached excerpt and its checked link, and admitting missing lecture evidence. Saved conversation survived reload. OpenAI/Claude requests use mocked vendor responses in automated tests; no live paid-provider test is claimed. iOS layout has source/build coverage but has not been visually checked on a physical Apple device.

Simulator/emulator results do not establish physical-device microphone, camera, calls, background reliability, soft-keyboard behavior or classroom accuracy. No physical device is connected to the development laptop during this change. All three libraries remain local and separate; managed subscriptions and cloud sync are not implemented.

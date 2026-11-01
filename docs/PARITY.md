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

At the time of this milestone, the new native instrumentation/simulator tests and iOS compilation are pending CI, and responsive visual/live Gemini checks are still in progress. This statement must be updated with actual results after verification. No unbuilt platform is verified. Simulator/emulator results do not establish physical-device microphone, camera, calls, background reliability or classroom accuracy. No physical device is connected to the development laptop during this change.

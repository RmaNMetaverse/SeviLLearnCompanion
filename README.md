# SeviL Learn Companion

A multilingual lecture companion for international students. Native **Kotlin/Compose Android**, native **Swift/SwiftUI iOS**, and **Next.js web** clients share one Python API and eight interface languages.

## Download builds

Every successful push to `main` automatically publishes a [GitHub development release](https://github.com/RmaNMetaverse/SeviLLearnCompanion/releases) after all platform checks pass. Downloads include an installable Android debug APK, an iOS simulator app, an **unsigned** iOS device IPA, and a compiled web server. The device IPA needs Apple signing/provisioning before it can be installed on an iPhone. See [release and installation details](docs/RELEASES.md).

## Run on Windows

Requirements: Python 3.12+, Node.js 22+, Git. The initial setup has already been performed on the development laptop.

```powershell
.\scripts\setup.ps1
.\scripts\start-local.ps1
```

Open **http://localhost:3000**. API documentation: http://localhost:8000/docs. `Start-SeviL.cmd` also starts the app. Stop with `scripts\stop-local.ps1`. Background processes use hidden windows; logs are under `.tools/run`.

The application starts in **demo mode**. “Try a sample lecture” generates explicitly labeled synthetic content; it does **not** analyze recordings, photos, or attachments. You can exercise course/session creation, uploads, transcript editing, notes, and PDF download without a key. The sample handout is in English regardless of interface language.

The initial implementation passed web, backend, Android and iOS build checks on GitHub. The Android APK was also built locally on Windows. This verifies builds, not microphone/camera behavior on physical devices. Gemini-only audio, board-image, notes and PDF processing has passed a live synthetic test; real-device classroom validation remains required.

## Enable real AI processing

All three clients include an **AI service** dropdown: OpenAI, Gemini or Claude. Gemini supports audio transcription and multimodal notes; PDF generation runs locally. Claude uses an explicitly selected Gemini/OpenAI audio service or a saved transcript. See [provider configuration](docs/AI_PROVIDERS.md).

Copy `.env.example` to `.env` if it does not exist. Edit it locally, never commit it:

```dotenv
SEVIL_MODE=live
SEVIL_DEFAULT_PROVIDER=gemini
GEMINI_API_KEY=your-private-key
GEMINI_MODEL=gemini-3.5-flash-lite
GEMINI_TRANSCRIPTION_MODEL=gemini-3.5-flash-lite
SEVIL_AUTO_DISCOVERY=true
```

Restart the API after changing `.env`. Model names are configurable because provider capabilities and account access vary. Provider keys stay on the server. The app never silently switches providers. No real AI calls or paid resource searches are made in demo mode. Live calls consume provider quota and may incur charges according to your account plan.

Workflow: add a course → create a session and confirm recording permission → record/upload audio → capture board photos and attach course PDFs/text → generate → review evidence and uncertain passages → export PDF. A supplied transcript can be edited and saved before generation; this skips audio transcription and is labeled as student-edited evidence.

In live mode, public-source discovery searches the named course, university and professor using the selected provider's web search. Only returned HTTPS citation URLs are retained. It supplements course uploads with a **search summary**, not a verified copy of a syllabus or textbook. Open and verify the original links. The server never fetches arbitrary supplied URLs or bypasses restricted portals. Scanned PDFs need OCR before upload in this version.

## Native Android

```powershell
.\scripts\setup-android.ps1
.\scripts\build-android.ps1
```

The setup installs the Android command-line SDK, platform 35, build tools 35.0.0 and platform tools under ignored `.tools`. It reuses an available JDK 21 or downloads Temurin 21. Gradle 8.11.1 is pinned through the committed wrapper; AGP is 8.9.2. The APK is copied to **`artifacts/SeviL-debug.apk`**. This is a debug build, not a store-signed release.

Setup uses the current `android sdk install` command when available and verifies the required tools before reporting success. Older SDK installations fall back to `sdkmanager`; its deprecation notice is not a build failure. A successful setup finishes with `Android tools ready` and the next build command.

To connect a USB phone without opening a network port:

```powershell
.\.tools\android-sdk\platform-tools\adb.exe reverse tcp:8000 tcp:8000
```

Set the app's server URL to `http://127.0.0.1:8000`. For an emulator use `http://10.0.2.2:8000`. Other HTTP hosts are intentionally blocked; use HTTPS for remote servers. A physical phone/emulator is needed to verify microphone, camera, calls, and background behavior. Android's foreground recorder stores audio in private app storage; pending recordings remain there until a successful retry upload. Clearing app storage or uninstalling deletes them.

## Native iOS source

On a Mac with Xcode and XcodeGen:

```bash
brew install xcodegen
cd apps/ios
xcodegen generate
open SeviL.xcodeproj
```

Select your signing team and an iPhone/iPad, then build. `project.yml` is the source of the Xcode project and Info.plist. The generated `.xcodeproj` is not committed. iOS 17+ is supported. Simulator defaults to `http://localhost:8000`; a real device should use the HTTPS API URL in Settings. Tokens are stored in Keychain. Recordings are retained in private application storage until uploaded. The in-app camera explicitly avoids owning the microphone's audio session.

## What is implemented

- Reusable course profiles: university, program, professor, course code, lecture language and note language.
- Shared persistent sessions, consent acknowledgment, original files, editable transcripts, sources and notes.
- Audio recording, independent board capture, manual uploads, capture timestamps, and pending-audio recovery in all clients.
- Selectable OpenAI/Gemini/Claude notes, OpenAI/Gemini transcription, contextual terminology hints, public-source search, image input, structured notes and citation validation.
- Bilingual glossary, learning objectives, review questions, uncertainty list and PDF handouts with original board images.
- English, Turkish, Simplified Chinese, Japanese, Korean, German, French, Spanish interfaces from one catalog.
- Local development scripts, deployment containers and CI checks for web, server, Android and iOS.

## Current limits

This is a **single-user pilot**, not a finished multi-tenant university service. One configured server token grants access to the whole workspace; there are no separate student accounts yet. Do not share that token among unrelated students. See [deployment](docs/DEPLOYMENT.md) before hosting.

- No universal university/LMS login integration, licensed textbook retrieval, or professor voice enrollment/separation.
- Audio is transcribed in approximately five-minute segments; references are **coarse segment starts**, not word alignment. Student-edited transcripts have no verified audio alignment.
- Photo timestamps reflect capture time, not when the board was written. Native/browser recording can be interrupted; inspect recovered files after an interruption. Recovery does not guarantee that an unfinalized audio container is playable.
- Browser recovery checkpoints are written every 10 seconds. An abrupt exit can lose the final checkpoint and may leave an incomplete recording. Keep the page open and stop/save normally.
- Maximum audio upload: 250 MiB; other files: 20 MiB; 20 board photos per live handout; 300 pages per PDF. Retrieval selects at most five pages per resource and 30 resource excerpts overall. This is lexical retrieval, not exhaustive textbook interpretation.
- PDF headings are currently English; content follows the configured output language in live mode. CJK PDFs use standard CID fonts and should be checked on intended readers.
- Jobs run in the API process. A restart marks interrupted jobs as failed for a manual retry; there is no distributed queue yet. Use a single API process.
- No live captions, fully offline AI, billing, push notifications, or app-store release signing yet.

## Checks and project policy

```powershell
$env:PYTHONPATH='services/api'
.\.venv\Scripts\python.exe -m pytest services/api/tests -q
python scripts/sync-locales.py --check
python scripts/export-contract.py --check
cd apps/web
npm run typecheck
npm run build
```

[AGENTS.md](AGENTS.md) requires platform parity for future changes. [docs/PARITY.md](docs/PARITY.md) records actual implementation and verification status. Shared translations are edited in `shared/locales.json`, then copied with `python scripts/sync-locales.py`. Generated copies are checked in CI. All clients consume the versioned API described in `shared/openapi.json`.

Source repository: https://github.com/RmaNMetaverse/SeviLLearnCompanion

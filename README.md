# SeviL Learn Companion

A lecture companion for international students with native Kotlin/Compose Android, native Swift/SwiftUI iOS and a static Next.js web application. All three clients keep **separate libraries on their own device**. No laptop backend, hosted account or cloud database is required. Cloud sync is planned later.

## Downloads

Successful pushes to `main` publish [development releases](https://github.com/RmaNMetaverse/SeviLLearnCompanion/releases) after web tests, Android emulator tests, iOS simulator tests and all builds pass. Downloads include an Android debug APK, an iOS simulator app, an **unsigned** iOS device IPA, and a runnable static web package. An unsigned IPA needs Apple signing/provisioning before installation. These are development builds, not app-store releases.

## Study workspace and session chat

All clients use the same green workspace structure: Overview, My courses, Lecture sessions and Settings. Phones show bottom navigation, medium screens a navigation rail, and large screens a sidebar. Each lecture has Notes, Chat, Transcript and Materials tabs. Shared colors and layout breakpoints are generated from `shared/design.json`; see [design rules](docs/DESIGN.md).

Open a session's **Chat** tab to ask questions, request examples or practise the topic with Gemini, OpenAI or Claude. Save a transcript or generate notes first when the session contains only audio. Chat uses that session's transcript and photos, its course's attached resources, attached link excerpts and recent conversation. Messages are saved on this device. Changing provider does not erase the conversation. Replies distinguish lecture evidence from extra explanations, display checked source references, and expose only links that occur in supplied evidence or actual search results. A model's answer can still be mistaken.

Add reference links in **Materials** with an optional pasted excerpt. A URL alone is a reference, not a fetched/read webpage. Enable **Search the web** per question for a separate provider search; this can add charges and require a paid provider account. Gemini's current free API tier does not include Google Search grounding for the configured model. No provider is substituted automatically.

The tutor keeps the latest 12 messages and selects bounded text excerpts (up to 32,000 transcript characters and 12,000 per text resource). PDFs and photos are sent as attachments again per answer, up to 20 photos and 30 MiB total binary input. Large libraries can cost more than the example budget in [AI costs and proposed pricing](docs/AI-COSTS.md). Original recordings/files and full saved conversation remain intact. A failed saved question can be retried without creating duplicate messages. Keep the app/tab open until processing finishes; replies are not streamed or processed as background jobs. Clearing chat removes this session's dialogue without removing its recordings/materials.

The current app uses personal provider keys; no subscription, shared commercial API key, quota billing or cloud sync is implemented. The prices in the cost document are proposals for a later metered service.

## Use Gemini on a phone

Install the new APK, open **Settings**, paste your own Gemini API key into the Gemini field, and save. Select **Gemini** in the AI service dropdown. Courses, sessions, recording, photos and existing PDF exports work without a key or internet. Generating new AI notes and searching for public sources require internet and provider quota. No server URL or `adb reverse` is needed.

Keys are entered separately on each device. Android encrypts saved keys using Android Keystore; iOS uses Keychain. Browser keys stay in the current tab's memory and clear on reload or close. A key in the laptop's ignored `.env` does not configure a phone. No shared secret is embedded in public binaries or source code.

## Where data lives

| Client | Database | Original recordings and attachments | AI connection |
|---|---|---|---|
| Android | Private SQLite `sevil-device.sqlite3` | Private app files | Direct to selected provider over HTTPS |
| iOS/iPadOS | Private SQLite `SeviLLibrary/library.sqlite3` | Private Application Support files | Direct to selected provider over HTTPS |
| Web | Browser IndexedDB `sevil-device-library` | IndexedDB blobs; recording checkpoints in a separate recovery database | Direct to selected provider over HTTPS |

There is no sync or account yet. Device libraries survive ordinary restarts and compatible app updates, but uninstalling, clearing app/site storage or browser eviction can delete them. Export important handouts and keep original source files. A complete library backup/import feature is not implemented. Old laptop-backed libraries remain in `data/`; they are not automatically migrated into device libraries.

The earlier pilot used FastAPI, SQLite `data/sevil.sqlite3`, and media in `data/files` on the laptop. That service is retained under `services/api` for development and future cloud work. Current clients do not connect to it.

## Run the web app on Windows

With Node.js 24 and installed dependencies:

```powershell
npm.cmd ci --prefix apps/web
.\scripts\start-local.ps1
```

Open http://localhost:3000. Stop with `.\scripts\stop-local.ps1`. `Start-SeviL.cmd` also starts it. Development startup runs only the web app. Logs are in `.tools/run`.

For an offline-capable production build:

```powershell
npm.cmd run build --prefix apps/web
npm.cmd start --prefix apps/web
```

The production build caches app assets after the first successful load. AI always requires internet. Keep the tab open during recording and AI processing. Browser camera/microphone require localhost or HTTPS. Host the contents of `apps/web/out` at the root of any static HTTPS host to deploy later; no API server or database service is required. Changing the host/origin creates a separate browser library. See [deployment](docs/DEPLOYMENT.md).

## Build Android on Windows

```powershell
.\scripts\setup-android.ps1
.\scripts\build-android.ps1
```

The SDK is installed under ignored `.tools`; JDK 21, platform 35, build tools 35.0.0 and Gradle 8.11.1 are used. The installable test APK is copied to `artifacts/SeviL-debug.apk`. The setup uses `android sdk install` when available, with an older `sdkmanager` fallback. Its deprecation notice is not an installation failure.

GitHub needs a persistent test signing key to update APKs without uninstalling. See [release details](docs/RELEASES.md). Physical microphone/camera/background behavior still needs testing on target devices.

## Build iOS on a Mac

```bash
brew install xcodegen
cd apps/ios
xcodegen generate
open SeviL.xcodeproj
```

Select your Apple signing team and device in Xcode. iOS/iPadOS 17+ is supported. The source is maintained on Windows; compilation and simulator tests run on GitHub's macOS runner. The in-app camera avoids taking over the microphone audio session.

## Workflow and limits

Add a course → create a session and confirm permission to record → record/upload audio → capture timestamped board photos and attach PDFs/text → generate → check sources and uncertainties → export PDF. A saved student-edited transcript skips transcription and has no verified audio alignment. “Try a sample lecture” creates explicitly synthetic notes offline.

- All clients offer OpenAI, Gemini and Claude. Gemini supports audio and notes. Claude needs an explicitly chosen Gemini/OpenAI transcription provider or a saved transcript. No silent provider fallback.
- Public source search is an explicit action and caches a summary and returned HTTPS links for that course/provider. Verify original links. It does not automatically retrieve university portals, licensed textbooks, or a professor's private materials.
- Original audio and photos are preserved. Transcription references identify whole audio clips and their start times, not word-level alignment. Photo timestamps indicate capture time, not when the board was written.
- Maximum stored audio attachment: 250 MiB. OpenAI transcription accepts files below 24 MiB in this version; choose Gemini or split longer audio manually. Gemini uses temporary provider file uploads for larger recordings and attempts to delete those copies afterward.
- Other attachments: 20 MiB each, combined binary attachments 30 MiB, 20 board photos. Native clients accept PDFs up to 300 pages; browser PDF page counts are not prevalidated, so use short chapters. Whole documents are passed to the model, not a verified page-level retrieval pipeline. Account/model limits can be lower.
- Native clients create PDFs locally; web uses the browser's Print/Save as PDF dialog. PDF section labels currently use English; content follows the course's requested note language. Native fonts support the device's language fonts; inspect exported non-Latin text on intended readers.
- AI processing runs in the foreground app/tab and can be interrupted by OS suspension or closing. A restart marks unfinished jobs failed so they can be retried; originals remain. Browser recording checkpoints are written every 10 seconds. Abrupt termination can lose the last checkpoint or leave an incomplete audio container.
- No cloud sync, hosted authentication, full library backup, live captions, offline AI, production signing, or billing yet. Real classroom accuracy and physical-device recording require further validation.

## Checks and shared contracts

```powershell
$env:PYTHONPATH='services/api'
.\.venv\Scripts\python.exe -m pytest services/api/tests scripts/tests -q
python scripts/sync-locales.py --check
.\.venv\Scripts\python.exe scripts/sync-ai-contract.py --check
.\.venv\Scripts\python.exe scripts/export-contract.py --check
npm.cmd test --prefix apps/web
npm.cmd run typecheck --prefix apps/web
npm.cmd run build --prefix apps/web
```

Edit all eight interface languages in `shared/locales.json`, then run `python scripts/sync-locales.py`. AI schema/prompts/models are generated for all clients with `scripts/sync-ai-contract.py`. The retained server contract is `shared/openapi.json`. [AGENTS.md](AGENTS.md) requires feature parity and [docs/PARITY.md](docs/PARITY.md) records actual verification.

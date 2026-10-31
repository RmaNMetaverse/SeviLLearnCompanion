# Deployment

The current three clients use separate device libraries. They do not require the Python API, a cloud database or cloud storage.

## Web

Run `npm ci` and `npm run build` in `apps/web`. Upload the contents of `out/` to the root of a static HTTPS host. HTTPS is required for microphone/camera access outside localhost. App assets have a production service worker; AI requests are never cached. Serve `sw.js` and HTML with revalidation, and hashed `_next/static` assets with long cache lifetimes. For a container, `docker compose up --build web` serves the static app at localhost:3000. Put a real HTTPS reverse proxy in front for remote hosting.

The release archive contains `out/`, a small static file server and `start-local.cjs`. Extract and run `node start-local.cjs` with Node.js 24. It binds localhost:3000. This server only serves public static app files: no browser courses, recordings, keys or database reach it.

Browser data is isolated by origin, browser and profile. Changing from localhost to a hosted URL will not transfer a library. Incognito storage may disappear when the session closes; normal site storage can also be cleared or evicted. The browser key is held in tab memory only. A full backup/migration UI is future work.

## Native clients

Android and iOS connect directly to the selected provider. Enter personal provider keys in Settings on each device. No configurable SeviL server endpoint exists in the current clients. SQLite metadata and media remain in private app storage. Preserve app signing identity for updates; uninstalling clears the library. Cloud sync is not implemented.

## Retained legacy API

`services/api` still contains the earlier FastAPI pilot. `data/sevil.sqlite3` and `data/files` remain untouched; current clients do not read or migrate those files. It can be started separately for development, or with the `legacy-api` Docker Compose profile. Its `.env` keys configure only that service.

Do not expose the legacy single-workspace pilot as a multi-user cloud service. Any future shared backend requires per-user authentication and authorization, database/storage ownership, quotas, retention, backup and a durable job queue. No cloud resources have been deployed by this change.

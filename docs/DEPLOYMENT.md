# Deployment

Local operation binds both services to loopback. Remote operation is deliberately an explicit configuration step.

## Private pilot with Docker

1. Copy `.env.example` to `.env`.
2. Set `SEVIL_ACCESS_TOKEN` to a random secret of at least 32 characters. Generate one locally with `python -c "import secrets; print(secrets.token_urlsafe(48))"` and keep it private.
3. Set `SEVIL_CORS_ORIGINS` to the exact HTTPS web origin (no trailing slash). The API rejects browser requests from unlisted origins.
4. Choose `SEVIL_MODE=demo` or configure the selected provider's server key and `SEVIL_MODE=live`.
5. Run `docker compose up --build -d`.
6. Put a managed HTTPS reverse proxy in front of the loopback-bound web port 3000 and, for native clients, API port 8000. Configure upload limits >=250 MiB, long request timeouts for uploads and source discovery, and no caching of authenticated responses.
7. Enter the same server token in each client. The web token is retained only for the current browser session. Native clients use private storage (Android) or Keychain (iOS).

The web image is a Next.js standalone server; API proxy rewrites are configured through the build argument `API_INTERNAL_URL`. Build a different image if the internal API hostname changes. Do not bake AI provider credentials into the web image. The Compose API service has an explicit persistent volume at `/data`; the backend container is non-root. Both exposed host ports bind only to `127.0.0.1`.

Back up and encrypt the data volume. It contains original classroom audio, normalized board photos, attachments, SQLite records and generated notes. Deleting a session deletes its audio/photos/notes; shared course resources persist and can be explicitly removed from another session in the same course. These deletes are permanent in the application; retention in backups is an operator responsibility.

## Before a public multi-student launch

- Replace the shared workspace token with individual accounts and server-side owner/organization checks on every record and file. Add recovery and sign-in abuse controls.
- Move files into private object storage and metadata into PostgreSQL with tested migrations; do not place originals in a public bucket.
- Move processing into a durable queue; add retries, deduplication, quotas, per-user cost controls and process isolation for untrusted PDFs/media.
- Define deletion/retention/export policies; obtain the appropriate recording permissions and complete KVKK/cross-border-processing review for the actual deployment.
- Test battery drain, two-hour recordings, screen lock, calls, Bluetooth route changes, low storage, airplane mode, interrupted uploads and whiteboard capture on actual Android and Apple devices.
- Review accessibility and translations with native speakers; validate difficult subjects, accents, handwritten mathematics and all PDF scripts against original evidence.

This repository does not claim these production controls are already implemented. Docker configuration makes private hosting portable; it is not a substitute for multi-user authorization.

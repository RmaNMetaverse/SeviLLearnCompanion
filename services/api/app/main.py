import asyncio
import os
import secrets
import uuid
import hashlib
from contextlib import asynccontextmanager
from pathlib import Path
from typing import Literal
from dotenv import load_dotenv
load_dotenv(Path(__file__).resolve().parents[3] / ".env")
from fastapi import FastAPI, HTTPException, Request, UploadFile, File, Form, Depends
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import FileResponse, Response
from PIL import Image, ImageOps, UnidentifiedImageError
from . import store
from .models import CourseInput, SessionInput, TranscriptInput, GenerationInput, Provider, LANGUAGES
from .providers import catalog, default_provider, require_key, configured
from .processing import generate, assets_for
from .pdf import render

tasks = set()
locks: set[str] = set()

@asynccontextmanager
async def lifespan(app):
    if os.getenv("SEVIL_ENV") == "production" and len(os.getenv("SEVIL_ACCESS_TOKEN", "")) < 32:
        raise RuntimeError("Production requires a private access token of at least 32 characters and HTTPS at the reverse proxy.")
    (store.DATA / "files").mkdir(parents=True, exist_ok=True)
    for session in store.all_records("session"):
        if session["status"] == "processing":
            session.update(status="failed", error="Server restarted during processing. Your files are saved. Please retry.")
            store.save("session", session)
    yield
    if tasks: await asyncio.gather(*tasks, return_exceptions=True)

async def access(request: Request):
    origin = request.headers.get("origin")
    allowed = os.getenv("SEVIL_CORS_ORIGINS", "http://localhost:3000,http://127.0.0.1:3000").split(",")
    if origin and origin not in allowed:
        raise HTTPException(403, "This browser origin is not allowed.")
    token = os.getenv("SEVIL_ACCESS_TOKEN", "")
    if token:
        supplied = request.headers.get("Authorization", "").removeprefix("Bearer ")
        if not secrets.compare_digest(supplied, token): raise HTTPException(401, "Enter the server access token in Settings.")
    else:
        # Unauthenticated development is intentionally restricted to loopback clients.
        host = request.client.host if request.client else ""
        if host not in {"127.0.0.1", "::1", "localhost", "testclient"}:
            raise HTTPException(403, "Configure SEVIL_ACCESS_TOKEN before connecting another device.")

app = FastAPI(title="SeviL Learn Companion", version="0.1.0", lifespan=lifespan)
app.add_middleware(CORSMiddleware, allow_origins=os.getenv("SEVIL_CORS_ORIGINS", "http://localhost:3000,http://127.0.0.1:3000").split(","), allow_methods=["GET", "POST", "DELETE", "PUT"], allow_headers=["Authorization", "Content-Type"])

def require(kind, id):
    item = store.get(kind, id)
    if not item: raise HTTPException(404, f"{kind.title()} not found")
    return item

def editable(session):
    if session["id"] in locks or session["status"] == "processing":
        raise HTTPException(409, "Wait for processing to finish before changing this session.")

def invalidate(session):
    session.update(status="draft", handout=None, sources=[], error=None)
    store.save("session", session)

@app.get("/health")
def health(): return {"status": "ok", "version": "0.1.0", "mode": os.getenv("SEVIL_MODE", "demo"), "ai_configured": any(configured(p) for p in ("openai","gemini","claude"))}

@app.get("/v1/providers", dependencies=[Depends(access)])
def providers(): return catalog()

@app.get("/v1/courses", dependencies=[Depends(access)])
def courses(): return store.all_records("course")

@app.post("/v1/courses", dependencies=[Depends(access)], status_code=201)
def create_course(body: CourseInput):
    if body.lecture_language not in LANGUAGES or body.output_language not in LANGUAGES: raise HTTPException(422, "Unsupported language")
    if not body.title.strip(): raise HTTPException(422, "Course title cannot be blank")
    return store.save("course", body.model_dump())

@app.get("/v1/sessions", dependencies=[Depends(access)])
def sessions(): return store.all_records("session")

@app.get("/v1/courses/{id}/sources", dependencies=[Depends(access)])
def discovered_sources(id: str, provider: Provider | None = None):
    require("course", id)
    return store.get("discovery", id + ":" + (provider or default_provider())) or {"id":id,"course_id":id,"summary":"","links":[]}

@app.post("/v1/courses/{id}/discover", dependencies=[Depends(access)])
async def discover_sources(id: str, body: GenerationInput = GenerationInput()):
    from .discovery import discover
    course = require("course", id)
    provider = body.provider or default_provider()
    try: await asyncio.to_thread(discover, course, provider)
    except ValueError as exc: raise HTTPException(502, str(exc))
    return store.get("discovery", id + ":" + provider) or {"id":id,"course_id":id,"summary":"","links":[]}

@app.post("/v1/sessions", dependencies=[Depends(access)], status_code=201)
def create_session(body: SessionInput):
    require("course", body.course_id)
    if not body.recording_permission: raise HTTPException(422, "Confirm that you have permission to record this class.")
    return store.save("session", {**body.model_dump(), "status": "draft", "transcript": "", "handout": None, "sources": [], "error": None})

@app.get("/v1/sessions/{id}", dependencies=[Depends(access)])
def get_session(id: str):
    session = require("session", id)
    return {**session, "assets": assets_for(session)}

@app.put("/v1/sessions/{id}/transcript", dependencies=[Depends(access)])
def transcript(id: str, body: TranscriptInput):
    session = require("session", id)
    editable(session)
    session.update(transcript=body.text, transcript_provider="manual", transcript_sources=[{"id": "transcript:manual", "label": "Student-edited transcript; audio timing not verified"}])
    invalidate(session)
    return session

@app.post("/v1/sessions/{id}/assets", dependencies=[Depends(access)], status_code=201)
async def upload(id: str, kind: Literal["audio", "photo", "resource"] = Form(...), offset_ms: int = Form(0, ge=0, le=86400000), duration_ms: int = Form(0, ge=0, le=86400000), file: UploadFile = File(...)):
    session = require("session", id)
    editable(session)
    if kind == "resource":
        for related in store.all_records("session"):
            if related["course_id"] == session["course_id"]: editable(related)
    name = Path((file.filename or "attachment").replace("\\", "/")).name[:180]
    ext = Path(name).suffix.lower()
    accepted = {"audio": {".m4a", ".mp3", ".mp4", ".wav", ".webm", ".ogg"}, "photo": {".jpg", ".jpeg", ".png", ".webp"}, "resource": {".pdf", ".txt", ".md"}}
    if ext not in accepted[kind]: raise HTTPException(415, "Unsupported file type")
    limit = 250 * 1024 * 1024 if kind == "audio" else 20 * 1024 * 1024
    asset_id = uuid.uuid4().hex
    path = store.DATA / "files" / asset_id
    path.parent.mkdir(parents=True, exist_ok=True)
    size = 0
    digest = hashlib.sha256()
    try:
        with path.open("wb") as destination:
            while chunk := await file.read(1024 * 1024):
                size += len(chunk)
                if size > limit: raise HTTPException(413, "File exceeds the upload limit")
                digest.update(chunk)
                destination.write(chunk)
        if not size: raise HTTPException(422, "The file is empty")
        mime = {".m4a": "audio/mp4", ".mp4": "audio/mp4", ".mp3": "audio/mpeg", ".wav": "audio/wav", ".webm": "audio/webm", ".ogg": "audio/ogg", ".pdf": "application/pdf", ".txt": "text/plain", ".md": "text/plain"}.get(ext, "image/jpeg")
        if kind == "photo":
            with Image.open(path) as image:
                if image.width * image.height > 40000000: raise HTTPException(413, "Image is too large")
                image = ImageOps.exif_transpose(image).convert("RGB")
                image.thumbnail((2400, 2400))
                image.save(path, format="JPEG", quality=90)
            mime = "image/jpeg"
        if kind == "resource" and ext == ".pdf":
            from pypdf import PdfReader
            try:
                reader = PdfReader(path)
                if reader.is_encrypted or len(reader.pages) > 300: raise ValueError()
            except Exception: raise HTTPException(422, "Use an unlocked PDF with at most 300 pages")
    except (UnidentifiedImageError, Image.DecompressionBombError, OSError):
        path.unlink(missing_ok=True)
        raise HTTPException(422, "Unable to read this file")
    except Exception:
        path.unlink(missing_ok=True)
        raise
    for previous in store.all_records("asset"):
        if previous.get("session_id") == id and previous["kind"] == kind and previous.get("offset_ms") == offset_ms and previous.get("sha256") == digest.hexdigest():
            path.unlink(missing_ok=True)
            return previous
    asset = store.save("asset", {"id": asset_id, "session_id": id, "course_id": session["course_id"], "kind": kind, "name": name, "mime": mime, "size": path.stat().st_size, "offset_ms": offset_ms, "duration_ms": duration_ms, "sha256": digest.hexdigest()})
    # New course resources invalidate all completed sessions that used this course library.
    for target in store.all_records("session") if kind == "resource" else [session]:
        if target["course_id"] == session["course_id"] and target["status"] != "processing": invalidate(target)
    if kind == "audio":
        session = require("session", id)
        session.update(transcript="", transcript_sources=[])
        store.save("session", session)
    return asset

@app.get("/v1/assets/{id}", dependencies=[Depends(access)])
def download_asset(id: str):
    asset = require("asset", id)
    return FileResponse(store.DATA / "files" / id, media_type=asset["mime"], filename=asset["name"])

@app.post("/v1/sessions/{id}/generate", dependencies=[Depends(access)], status_code=202)
async def process(id: str, body: GenerationInput = GenerationInput()):
    session = require("session", id)
    editable(session)
    provider = body.provider or default_provider()
    if os.getenv("SEVIL_MODE", "demo") != "demo":
        try:
            require_key(provider)
            if not session.get("transcript"):
                audio_provider = body.transcription_provider or (provider if provider != "claude" else None)
                if not audio_provider: raise ValueError("Choose Gemini or OpenAI as the audio service for Claude, or save a transcript first.")
                require_key(audio_provider)
        except ValueError as exc: raise HTTPException(422, str(exc))
    locks.add(id)
    session.update(status="processing", error=None, handout=None, requested_provider=provider, requested_transcription_provider=body.transcription_provider)
    store.save("session", session)
    async def work():
        try: await asyncio.to_thread(generate, id, provider, body.transcription_provider)
        finally: locks.discard(id)
    task = asyncio.create_task(work())
    tasks.add(task)
    task.add_done_callback(tasks.discard)
    return session

@app.get("/v1/sessions/{id}/handout.pdf", dependencies=[Depends(access)])
def pdf(id: str):
    session = require("session", id)
    if session["status"] != "ready" or not session.get("handout"): raise HTTPException(409, "Generate a handout first")
    return Response(render(session, require("course", session["course_id"]), assets_for(session)), media_type="application/pdf", headers={"Content-Disposition": 'attachment; filename="sevil-handout.pdf"'})

@app.delete("/v1/sessions/{id}", dependencies=[Depends(access)], status_code=204)
def delete_session(id: str):
    session = require("session", id)
    editable(session)
    for asset in store.all_records("asset"):
        if asset.get("session_id") == id and asset["kind"] != "resource":
            (store.DATA / "files" / asset["id"]).unlink(missing_ok=True)
            store.remove(asset["id"])
    store.remove(id)
    return Response(status_code=204)

@app.delete("/v1/assets/{id}", dependencies=[Depends(access)], status_code=204)
def delete_asset(id: str):
    asset = require("asset", id)
    related = [s for s in store.all_records("session") if s["course_id"] == asset["course_id"] and (asset["kind"] == "resource" or s["id"] == asset["session_id"])]
    for session in related: editable(session)
    (store.DATA / "files" / id).unlink(missing_ok=True)
    store.remove(id)
    for session in related:
        if asset["kind"] == "audio": session.update(transcript="", transcript_sources=[])
        invalidate(session)
    return Response(status_code=204)

import json
import os
import sqlite3
import uuid
from datetime import datetime, timezone
from pathlib import Path

DATA = Path(os.getenv("SEVIL_DATA_DIR", "./data")).resolve()

def connect():
    DATA.mkdir(parents=True, exist_ok=True)
    db = sqlite3.connect(DATA / "sevil.sqlite3", timeout=30)
    db.execute("PRAGMA journal_mode=WAL")
    db.execute("CREATE TABLE IF NOT EXISTS records (id TEXT PRIMARY KEY, kind TEXT NOT NULL, payload TEXT NOT NULL)")
    return db

def save(kind, value):
    value = dict(value)
    value.setdefault("id", uuid.uuid4().hex)
    value.setdefault("created_at", datetime.now(timezone.utc).isoformat())
    with connect() as db:
        db.execute("INSERT OR REPLACE INTO records VALUES (?,?,?)", (value["id"], kind, json.dumps(value, ensure_ascii=False)))
    return value

def get(kind, id):
    with connect() as db:
        row = db.execute("SELECT payload FROM records WHERE kind=? AND id=?", (kind, id)).fetchone()
    return json.loads(row[0]) if row else None

def all_records(kind):
    with connect() as db:
        rows = db.execute("SELECT payload FROM records WHERE kind=? ORDER BY rowid DESC", (kind,)).fetchall()
    return [json.loads(row[0]) for row in rows]

def remove(id):
    with connect() as db:
        db.execute("DELETE FROM records WHERE id=?", (id,))

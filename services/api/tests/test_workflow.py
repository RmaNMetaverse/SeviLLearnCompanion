import io
import time
import json
import pytest
from fastapi.testclient import TestClient
from PIL import Image
from pypdf import PdfReader
from app import store
from app.main import app
from app.processing import generate

@pytest.fixture
def client(tmp_path, monkeypatch):
    monkeypatch.setattr(store, "DATA", tmp_path)
    monkeypatch.setenv("SEVIL_MODE", "demo")
    monkeypatch.setenv("SEVIL_AUTO_DISCOVERY", "false")
    monkeypatch.delenv("SEVIL_ACCESS_TOKEN", raising=False)
    monkeypatch.setenv("SEVIL_ENV", "development")
    monkeypatch.setenv("SEVIL_DEFAULT_PROVIDER", "openai")
    for key in ("OPENAI_API_KEY","GEMINI_API_KEY","ANTHROPIC_API_KEY"): monkeypatch.delenv(key,raising=False)
    with TestClient(app) as client: yield client

def course(client, language="en"):
    response = client.post("/v1/courses", json={"title":"Test course","university":"Test university","professor":"Test professor","output_language":language})
    assert response.status_code == 201
    return response.json()

def session(client, c=None):
    c = c or course(client)
    response = client.post("/v1/sessions", json={"course_id":c["id"],"title":"Lecture 1","recording_permission":True})
    assert response.status_code == 201
    return response.json()

def ready(client, s):
    assert client.post(f"/v1/sessions/{s['id']}/generate").status_code == 202
    for _ in range(100):
        result = client.get(f"/v1/sessions/{s['id']}").json()
        if result["status"] != "processing": break
        time.sleep(.02)
    assert result["status"] == "ready", result
    return result

def test_permission_and_language_validation(client):
    c = course(client)
    assert client.post("/v1/sessions",json={"course_id":c["id"],"title":"Test"}).status_code == 422
    assert client.post("/v1/courses",json={"title":"Test","output_language":"invalid"}).status_code == 422
    assert client.post("/v1/courses",json={"title":"   "}).status_code == 422

@pytest.mark.parametrize("language", ["en","tr","zh-Hans","ja","ko","de","fr","es"])
def test_full_workflow_pdf_and_deletion(client, language):
    c = course(client, language)
    s = session(client, c)
    base = f"/v1/sessions/{s['id']}"
    image = io.BytesIO(); Image.new("RGB",(400,200),"white").save(image,"PNG")
    photo = client.post(base+"/assets",data={"kind":"photo","offset_ms":"12345"},files={"file":("board.png",image.getvalue(),"image/png")})
    assert photo.status_code == 201, photo.text
    assert photo.json()["offset_ms"] == 12345
    resource = client.post(base+"/assets",data={"kind":"resource"},files={"file":("reading.txt",b"Specialized terminology and learning.","text/plain")}).json()
    assert client.put(base+"/transcript",json={"text":"Student-edited transcript."}).status_code == 200
    result = ready(client, s)
    assert result["generated_mode"] == "demo"
    assert "DEMONSTRATION" in result["handout"]["summary"]
    pdf = client.get(base+"/handout.pdf")
    assert pdf.status_code == 200
    reader = PdfReader(io.BytesIO(pdf.content))
    assert len(reader.pages) >= 1
    assert any(p.images for p in reader.pages)
    assert client.put(base+"/transcript",json={"text":"Corrected transcript"}).status_code == 200
    assert client.get(base+"/handout.pdf").status_code == 409
    assert client.delete(base).status_code == 204
    assert client.get(base).status_code == 404
    assert client.get("/v1/assets/"+photo.json()["id"]).status_code == 404
    # Course library persists until explicitly removed.
    assert client.get("/v1/assets/"+resource["id"]).status_code == 200
    assert client.delete("/v1/assets/"+resource["id"]).status_code == 204

def test_upload_validation_and_course_library(client):
    c = course(client); s = session(client,c); s2 = session(client,c)
    base=f"/v1/sessions/{s['id']}"
    assert client.post(base+"/assets",data={"kind":"photo"},files={"file":("bad.jpg",b"not an image")}).status_code == 422
    assert client.post(base+"/assets",data={"kind":"audio","offset_ms":"-1"},files={"file":("bad.m4a",b"audio")}).status_code == 422
    assert client.post(base+"/assets",data={"kind":"resource"},files={"file":("file.exe",b"bad")}).status_code == 415
    assert client.post(base+"/assets",data={"kind":"resource"},files={"file":("empty.txt",b"")}).status_code == 422
    asset = client.post(base+"/assets",data={"kind":"resource"},files={"file":("../../chapter.txt",b"Content")}).json()
    assert asset["name"] == "chapter.txt"
    assert any(a["id"]==asset["id"] for a in client.get(f"/v1/sessions/{s2['id']}").json()["assets"])

def test_access_token_guards_all_private_endpoints(client, monkeypatch):
    c = course(client)
    monkeypatch.setenv("SEVIL_ACCESS_TOKEN","private-test-token")
    assert client.get("/health").status_code == 200
    assert client.get("/v1/courses").status_code == 401
    assert client.post("/v1/sessions",json={"course_id":c["id"],"title":"Test","recording_permission":True}).status_code == 401
    assert client.get("/v1/courses",headers={"Authorization":"Bearer private-test-token"}).status_code == 200

def test_retried_upload_is_idempotent(client):
    s = session(client)
    path = f"/v1/sessions/{s['id']}/assets"
    first = client.post(path,data={"kind":"audio","offset_ms":1200,"duration_ms":4000},files={"file":("lecture.m4a",b"synthetic-test-audio")})
    second = client.post(path,data={"kind":"audio","offset_ms":1200,"duration_ms":4000},files={"file":("lecture.m4a",b"synthetic-test-audio")})
    assert first.status_code == second.status_code == 201
    assert first.json()["id"] == second.json()["id"]
    assert len(client.get(f"/v1/sessions/{s['id']}").json()["assets"]) == 1

def test_cross_origin_requests_are_rejected(client):
    assert client.get("/v1/courses",headers={"Origin":"https://untrusted.example"}).status_code == 403

def test_live_mode_does_not_fall_back_to_fake_notes(client, monkeypatch):
    s = session(client)
    monkeypatch.setenv("SEVIL_MODE","live")
    generate(s["id"])
    stored = store.get("session",s["id"])
    assert stored["status"] == "failed"
    assert stored["handout"] is None

def test_live_rejects_fabricated_citations(client, monkeypatch):
    s = session(client)
    client.put(f"/v1/sessions/{s['id']}/transcript",json={"text":"Gravity attracts masses."})
    monkeypatch.setenv("SEVIL_MODE","live")
    from app import processing
    note = processing.demo_handout(s).model_dump()
    note["sections"][0].update(kind="lecture",citations=["invented-source"])
    monkeypatch.setenv("OPENAI_API_KEY","test-key")
    monkeypatch.setattr(processing,"make_note",lambda *a,**kw:json.dumps(note))
    generate(s["id"])
    stored=store.get("session",s["id"])
    assert stored["status"] == "failed"
    assert "unknown citation" in stored["error"]

def test_production_requires_token(tmp_path,monkeypatch):
    monkeypatch.setattr(store,"DATA",tmp_path)
    monkeypatch.setenv("SEVIL_ENV","production")
    monkeypatch.delenv("SEVIL_ACCESS_TOKEN",raising=False)
    with pytest.raises(RuntimeError):
        with TestClient(app): pass

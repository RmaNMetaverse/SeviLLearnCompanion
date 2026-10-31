import json
import pytest
import httpx
from app import providers, store, processing, discovery
from app.models import Handout
from test_workflow import client, session

def test_catalog_never_exposes_keys(client,monkeypatch):
    monkeypatch.setenv("GEMINI_API_KEY","private-test-key")
    response=client.get("/v1/providers")
    assert response.status_code == 200
    assert "private-test-key" not in response.text
    catalog=response.json()
    assert {p["id"] for p in catalog["providers"]} == {"openai","gemini","claude"}
    assert next(p for p in catalog["providers"] if p["id"] == "gemini")["configured"]

def test_invalid_provider_and_missing_key_are_rejected(client,monkeypatch):
    s=session(client); path=f"/v1/sessions/{s['id']}/generate"
    assert client.post(path,json={"provider":"unknown"}).status_code == 422
    monkeypatch.setenv("SEVIL_MODE","live")
    assert client.post(path,json={"provider":"gemini"}).status_code == 422
    assert store.get("session",s["id"])["status"] == "draft"

def test_claude_requires_explicit_audio_choice(client,monkeypatch):
    s=session(client)
    monkeypatch.setenv("SEVIL_MODE","live"); monkeypatch.setenv("ANTHROPIC_API_KEY","test-key")
    response=client.post(f"/v1/sessions/{s['id']}/generate",json={"provider":"claude"})
    assert response.status_code == 422
    assert "audio service" in response.json()["detail"]

@pytest.mark.parametrize("provider",["openai","gemini","claude"])
def test_selected_provider_drives_note_generation(client,monkeypatch,provider):
    s=session(client)
    client.put(f"/v1/sessions/{s['id']}/transcript",json={"text":"Mitochondria produce ATP."})
    monkeypatch.setenv("SEVIL_MODE","live"); monkeypatch.setenv(providers.KEYS[provider],"test-key")
    captured=[]
    note=processing.demo_handout(s).model_dump()
    note["sections"][0].update(kind="lecture",citations=["transcript:manual"])
    monkeypatch.setattr(processing,"make_note",lambda p,*args:captured.append(p) or json.dumps(note))
    processing.generate(s["id"],provider)
    result=store.get("session",s["id"])
    assert result["status"] == "ready",result
    assert result["generated_provider"] == provider
    assert captured == [provider]

@pytest.mark.parametrize("provider",["openai","gemini","claude"])
def test_provider_wire_formats_support_images_and_structured_notes(monkeypatch,provider):
    requests=[]
    def fake_post(p,path,**kw):
        requests.append((p,path,kw))
        if p == "openai": return {"output":[{"content":[{"type":"output_text","text":"{}"}]}]}
        if p == "gemini": return {"candidates":[{"finishReason":"STOP","content":{"parts":[{"text":"{}"}]}}]}
        return {"stop_reason":"end_turn","content":[{"type":"text","text":"{}"}]}
    monkeypatch.setattr(providers,"post",fake_post)
    content=[{"type":"input_text","text":"Evidence"},{"type":"input_image","image_url":"data:image/jpeg;base64,dGVzdA=="}]
    assert providers.make_note(provider,content,"Instructions",providers.schema_for(Handout)) == "{}"
    p,path,kwargs=requests[0]; body=kwargs["json"]
    assert p == provider
    if p == "gemini":
        assert body["generationConfig"]["responseMimeType"] == "application/json"
        assert body["contents"][0]["parts"][1]["inlineData"]["mimeType"] == "image/jpeg"
    elif p == "claude":
        assert body["output_config"]["format"]["type"] == "json_schema"
        assert body["messages"][0]["content"][1]["source"]["media_type"] == "image/jpeg"
    else: assert body["store"] is False

def test_provider_error_does_not_leak_or_fall_back(monkeypatch):
    monkeypatch.setenv("GEMINI_API_KEY","secret-never-log")
    calls=[]
    class FakeClient:
        def __init__(self,**kw): pass
        def __enter__(self): return self
        def __exit__(self,*args): pass
        def post(self,url,**kwargs):
            calls.append(url)
            return httpx.Response(429,json={"error":{"message":"secret-never-log"}})
    monkeypatch.setattr(providers.httpx,"Client",FakeClient)
    with pytest.raises(ValueError) as error: providers.gemini_generate([{"text":"test"}])
    assert "secret-never-log" not in str(error.value)
    assert "quota" in str(error.value)
    assert len(calls) == 1 and "generativelanguage.googleapis.com" in calls[0]

def test_source_caches_do_not_cross_providers(client,monkeypatch):
    s=session(client)
    store.save("discovery",{"id":s["course_id"]+":gemini","links":[{"url":"https://example.edu","title":"Test"}],"summary":"Gemini evidence"})
    assert client.get(f"/v1/courses/{s['course_id']}/sources?provider=gemini").json()["links"]
    assert client.get(f"/v1/courses/{s['course_id']}/sources?provider=openai").json()["links"] == []

def test_claude_uses_only_explicit_audio_provider(client,monkeypatch):
    s=session(client)
    monkeypatch.setenv("SEVIL_MODE","live")
    monkeypatch.setenv("ANTHROPIC_API_KEY","test-claude-key")
    monkeypatch.setenv("GEMINI_API_KEY","test-gemini-key")
    audio_calls=[]; note_calls=[]
    def audio(session,course,assets,provider):
        audio_calls.append(provider)
        return "Mitochondria produce ATP.",[{"id":"audio:test:0","label":"Synthetic audio"}]
    note=processing.demo_handout(s).model_dump()
    note["sections"][0].update(kind="lecture",citations=["audio:test:0"])
    monkeypatch.setattr(processing,"transcribe",audio)
    monkeypatch.setattr(processing,"make_note",lambda provider,*a:note_calls.append(provider) or json.dumps(note))
    processing.generate(s["id"],"claude","gemini")
    stored=store.get("session",s["id"])
    assert stored["status"] == "ready",stored
    assert audio_calls == ["gemini"] and note_calls == ["claude"]
    assert stored["transcript_provider"] == "gemini"

def test_existing_transcript_is_not_retranscribed(client,monkeypatch):
    s=session(client)
    client.put(f"/v1/sessions/{s['id']}/transcript",json={"text":"A corrected lecture transcript."})
    monkeypatch.setenv("SEVIL_MODE","live"); monkeypatch.setenv("GEMINI_API_KEY","test-key")
    def unexpected(*args): raise AssertionError("Existing transcript must be reused")
    monkeypatch.setattr(processing,"transcribe",unexpected)
    monkeypatch.setattr(processing,"make_note",lambda *a:processing.demo_handout(s).model_dump_json())
    processing.generate(s["id"],"gemini")
    stored=store.get("session",s["id"])
    assert stored["status"] == "ready",stored
    assert stored["transcript_provider"] == "manual"

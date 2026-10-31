"""Server-only provider adapters. Never fall back to another vendor implicitly."""
import base64
import copy
import os
from urllib.parse import quote
import httpx

KEYS = {"openai":"OPENAI_API_KEY", "gemini":"GEMINI_API_KEY", "claude":"ANTHROPIC_API_KEY"}
LABELS = {"openai":"OpenAI", "gemini":"Gemini", "claude":"Claude"}

def default_provider():
    provider = os.getenv("SEVIL_DEFAULT_PROVIDER", "openai")
    return provider if provider in KEYS else "openai"

def model_for(provider):
    return {"openai":os.getenv("OPENAI_NOTES_MODEL", "gpt-4.1"), "gemini":os.getenv("GEMINI_MODEL", "gemini-3.5-flash-lite"), "claude":os.getenv("ANTHROPIC_MODEL", "claude-sonnet-5-5")}[provider]

def configured(provider): return bool(os.getenv(KEYS[provider], ""))

def catalog():
    return {"default_provider":default_provider(), "providers":[{"id":p,"label":LABELS[p],"configured":configured(p),"audio_supported":p != "claude","model":model_for(p)} for p in KEYS]}

def require_key(provider):
    key = os.getenv(KEYS[provider], "")
    if not key: raise ValueError(f"{LABELS[provider]} is not configured. Set {KEYS[provider]} in the server's .env file. No other provider was used.")
    return key

def post(provider, path, **kwargs):
    key = require_key(provider)
    base = {"openai":"https://api.openai.com/v1/", "gemini":"https://generativelanguage.googleapis.com/v1beta/", "claude":"https://api.anthropic.com/v1/"}[provider]
    headers = {"openai":{"Authorization":"Bearer " + key}, "gemini":{"x-goog-api-key":key}, "claude":{"x-api-key":key,"anthropic-version":"2023-06-01"}}[provider]
    try:
        with httpx.Client(timeout=240) as client: response = client.post(base + path, headers=headers, **kwargs)
    except httpx.HTTPError:
        raise ValueError(f"{LABELS[provider]} could not be reached. Your files are saved; retry later. No other provider was used.") from None
    if response.status_code >= 400:
        reason = {400:"request/model configuration was rejected",401:"authentication failed",403:"access was denied",404:"model or API endpoint was unavailable",429:"quota or rate limit was reached"}.get(response.status_code,"provider request failed")
        raise ValueError(f"{LABELS[provider]} HTTP {response.status_code}: {reason}. Check the server key, model access and quota. No other provider was used.")
    return response.json()

def openai_post(path, **kwargs): return post("openai", path, **kwargs)

def schema_for(model):
    schema = copy.deepcopy(model.model_json_schema())
    def strict(node):
        if isinstance(node, dict):
            if node.get("type") == "object":
                node["additionalProperties"] = False
                node["required"] = list(node.get("properties", {}))
            for value in list(node.values()): strict(value)
        elif isinstance(node,list):
            for value in node: strict(value)
    strict(schema)
    return schema

def gemini_text(result):
    candidates = result.get("candidates", [])
    if not candidates: raise ValueError("Gemini returned no content. The request may have been blocked; no handout was published.")
    candidate = candidates[0]
    if candidate.get("finishReason") not in {None,"STOP"}:
        raise ValueError("Gemini did not complete the response. Check input length or content restrictions and retry.")
    return "".join(part.get("text", "") for part in candidate.get("content", {}).get("parts", []) if not part.get("thought"))

def gemini_generate(parts, instructions="", schema=None, search=False, model=None):
    body = {"contents":[{"role":"user","parts":parts}],"generationConfig":{"maxOutputTokens":16384}}
    if instructions: body["systemInstruction"] = {"parts":[{"text":instructions}]}
    if schema: body["generationConfig"].update(responseMimeType="application/json",responseJsonSchema=schema)
    if search: body["tools"] = [{"google_search":{}}]
    selected = model or model_for("gemini")
    return post("gemini", "models/" + quote(selected, safe="-._") + ":generateContent", json=body)

def claude_generate(content, instructions, schema=None, search=False):
    body = {"model":model_for("claude"),"max_tokens":16384,"system":instructions,"messages":[{"role":"user","content":content}]}
    if schema: body["output_config"] = {"format":{"type":"json_schema","schema":schema}}
    if search: body["tools"] = [{"type":"web_search_20250305","name":"web_search","max_uses":3}]
    result = post("claude", "messages", json=body)
    if result.get("stop_reason") not in {None,"end_turn"}:
        raise ValueError("Claude did not finish the response. Retry after checking the input and model limits.")
    return result

def make_note(provider, content, instructions, schema):
    if provider == "openai":
        result = openai_post("responses", json={"model":model_for(provider),"store":False,"instructions":instructions,"input":[{"role":"user","content":content}],"text":{"format":{"type":"json_schema","name":"lecture_handout","strict":True,"schema":schema}}})
        return "".join(c.get("text", "") for item in result.get("output", []) for c in item.get("content", []) if c.get("type") == "output_text")
    converted = []
    for part in content:
        if part["type"] == "input_text": converted.append({"text":part["text"]} if provider == "gemini" else {"type":"text","text":part["text"]})
        elif part["type"] == "input_image":
            header, data = part["image_url"].split(",",1)
            mime = header.removeprefix("data:").split(";")[0]
            converted.append({"inlineData":{"mimeType":mime,"data":data}} if provider == "gemini" else {"type":"image","source":{"type":"base64","media_type":mime,"data":data}})
    if provider == "gemini": return gemini_text(gemini_generate(converted,instructions,schema))
    result = claude_generate(converted,instructions,schema)
    return "".join(p.get("text", "") for p in result.get("content", []) if p.get("type") == "text")

def transcribe_clip(provider, path, context, language):
    if provider == "gemini":
        prompt = f"Transcribe this lecture audio verbatim in its original language ({language}). Output only the transcript, no summary or commentary. Do not invent speech in silence/noise. Mark unclear speech [inaudible]. Terminology hints (not speech to invent): {context}"
        result = gemini_generate([{"text":prompt},{"inlineData":{"mimeType":"audio/m4a","data":base64.b64encode(path.read_bytes()).decode()}}],model=os.getenv("GEMINI_TRANSCRIPTION_MODEL",model_for("gemini")))
        return gemini_text(result)
    if provider != "openai": raise ValueError("Claude does not accept audio in this integration. Select Gemini or OpenAI as the audio service, or supply a transcript.")
    model = os.getenv("OPENAI_TRANSCRIPTION_MODEL", "gpt-transcribe")
    hints = {"languages[]":language} if model == "gpt-transcribe" else {"language":language}
    with path.open("rb") as file:
        result = openai_post("audio/transcriptions",files={"file":(path.name,file,"audio/mp4")},data={"model":model,"prompt":context,**hints})
    return result.get("text", "")

"""Explicit opt-in live integration check. Uses only synthetic fixtures, never classroom files.

Requires a locally created artifacts/gemini-smoke/lecture.wav and GEMINI_API_KEY
in the ignored .env. Makes real Gemini requests and may consume provider quota.
"""
import io
import os
import sys
import time
from pathlib import Path
ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT / "services/api"))
from app.main import app
from app import store
from fastapi.testclient import TestClient
from PIL import Image, ImageDraw, ImageFont
from pypdf import PdfReader

def main():
    folder = ROOT / "artifacts/gemini-smoke"
    audio = folder / "lecture.wav"
    if not audio.is_file(): raise SystemExit("Create the synthetic lecture.wav fixture first; no personal audio is selected automatically.")
    os.environ.update(SEVIL_MODE="live",SEVIL_DEFAULT_PROVIDER="gemini",SEVIL_AUTO_DISCOVERY="false",SEVIL_ENV="development",SEVIL_ACCESS_TOKEN="")
    store.DATA = folder / ("data-" + str(time.time_ns()))
    image=Image.new("RGB",(1000,500),"white")
    draw=ImageDraw.Draw(image)
    font=ImageFont.truetype("C:/Windows/Fonts/arial.ttf",42) if Path("C:/Windows/Fonts/arial.ttf").exists() else ImageFont.load_default(size=36)
    draw.multiline_text((50,60),"CELL BIOLOGY\nMitochondria -> ATP\nPhotosynthesis: light -> glucose",fill="black",font=font,spacing=24)
    board=io.BytesIO(); image.save(board,"PNG")
    with TestClient(app) as client:
        c=client.post("/v1/courses",json={"title":"Synthetic cell biology test","university":"Synthetic test university","professor":"Synthetic speech","lecture_language":"en","output_language":"en"})
        c.raise_for_status()
        s=client.post("/v1/sessions",json={"course_id":c.json()["id"],"title":"Gemini audio, whiteboard and PDF test","recording_permission":True})
        s.raise_for_status(); base="/v1/sessions/"+s.json()["id"]
        with audio.open("rb") as f:
            r=client.post(base+"/assets",data={"kind":"audio"},files={"file":("lecture.wav",f,"audio/wav")}); r.raise_for_status()
        r=client.post(base+"/assets",data={"kind":"photo","offset_ms":5000},files={"file":("board.png",board.getvalue(),"image/png")}); r.raise_for_status()
        r=client.post(base+"/assets",data={"kind":"resource"},files={"file":("reading.txt",b"Mitochondria produce ATP through cellular respiration. Chloroplasts use light to produce glucose through photosynthesis.","text/plain")}); r.raise_for_status()
        r=client.post(base+"/generate",json={"provider":"gemini","transcription_provider":"gemini"}); r.raise_for_status()
        deadline=time.monotonic()+500
        while time.monotonic()<deadline:
            result=client.get(base).json()
            if result["status"] != "processing": break
            time.sleep(1)
        if result["status"] != "ready": raise SystemExit(result.get("error") or "Timed out waiting for Gemini")
        assert result["generated_provider"] == "gemini"
        assert result["transcript_provider"] == "gemini"
        assert "mitochondria" in result["transcript"].lower()
        assert result["handout"]["sections"]
        pdf=client.get(base+"/handout.pdf"); pdf.raise_for_status()
        reader=PdfReader(io.BytesIO(pdf.content))
        assert any(page.images for page in reader.pages)
        output=folder / "Gemini-live-handout.pdf"; output.write_bytes(pdf.content)
        print("PASS: Gemini audio transcription, board-image input, resource-grounded notes and PDF export.")
        print("PDF:",output)
        print("Pages:",len(reader.pages),"Transcript characters:",len(result["transcript"]))

if __name__ == "__main__": main()

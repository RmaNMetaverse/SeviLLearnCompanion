import io
from pathlib import Path
from xml.sax.saxutils import escape
from reportlab.lib import colors
from reportlab.lib.styles import getSampleStyleSheet, ParagraphStyle
from reportlab.lib.enums import TA_LEFT
from reportlab.lib.utils import ImageReader
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from reportlab.pdfbase.cidfonts import UnicodeCIDFont
from reportlab.platypus import SimpleDocTemplate, Paragraph, Spacer, Image, KeepTogether
from . import store

def render(session, course, assets):
    language = course["output_language"]
    font = "Helvetica"
    if language in {"zh-Hans", "ja", "ko"}:
        font = {"zh-Hans": "STSong-Light", "ja": "HeiseiMin-W3", "ko": "HYSMyeongJo-Medium"}[language]
        pdfmetrics.registerFont(UnicodeCIDFont(font))
    else:
        for path in [Path("C:/Windows/Fonts/arial.ttf"), Path("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf")]:
            if path.exists():
                font = "SevilUnicode"
                if font not in pdfmetrics.getRegisteredFontNames():
                    pdfmetrics.registerFont(TTFont(font, str(path)))
                break
    styles = getSampleStyleSheet()
    for style in styles.byName.values():
        style.fontName = font
        style.wordWrap = "CJK" if language in {"zh-Hans", "ja", "ko"} else None
    styles.add(ParagraphStyle("Meta", fontName=font, fontSize=9, leading=14, textColor=colors.HexColor("#526666")))
    styles["BodyText"].leading = 17
    styles["Heading1"].textColor = colors.HexColor("#155e55")
    note = session["handout"]
    out = io.BytesIO()
    doc = SimpleDocTemplate(out, title=note["title"], author="SeviL Learn Companion", leftMargin=48, rightMargin=48, topMargin=48, bottomMargin=48)
    story = []
    def para(text, style="BodyText"):
        story.append(Paragraph(escape(str(text)).replace("\n", "<br/>"), styles[style]))
        story.append(Spacer(1, 8))
    para("SeviL / LEARN COMPANION", "Meta")
    para(note["title"], "Title")
    para(f"{course['title']} · {course['university']} · {course['professor']} · {session['created_at'][:10]}", "Meta")
    if session.get("generated_mode") == "demo": para("DEMONSTRATION — SYNTHETIC CONTENT", "Heading2")
    para(note["summary"])
    para("Learning objectives", "Heading1")
    for item in note["objectives"]: para("• " + item)
    sources = {s["id"]: s["label"] for s in session.get("sources", [])}
    for section in note["sections"]:
        para(section["heading"], "Heading1")
        para(section["kind"].upper(), "Meta")
        para(section["body"])
        for ref in section["citations"]: para(sources.get(ref, ref), "Meta")
    photos = sorted([a for a in assets if a["kind"] == "photo"], key=lambda a: a["offset_ms"])
    if photos: para("Whiteboard evidence", "Heading1")
    for photo in photos:
        path = store.DATA / "files" / photo["id"]
        width, height = ImageReader(str(path)).getSize()
        scale = min(480 / width, 360 / height)
        caption = Paragraph(escape(f"{photo['name']} · captured at {photo['offset_ms'] // 60000:02}:{photo['offset_ms'] // 1000 % 60:02}. Capture time does not establish when the board was written."), styles["Meta"])
        story.append(KeepTogether([Image(str(path), width=width * scale, height=height * scale), Spacer(1, 8), caption, Spacer(1, 16)]))
    para("Bilingual glossary", "Heading1")
    for term in note["glossary"]: para(f"{term['term']} / {term['translation']} — {term['definition']}")
    para("Review questions", "Heading1")
    for i, item in enumerate(note["questions"], 1): para(f"{i}. {item}")
    para("Check before studying", "Heading1")
    for item in note["uncertainties"]: para("• " + item)
    if session.get("sources"):
        para("References", "Heading1")
        for source in session["sources"]:
            para(source["label"], "Meta")
            for link in source.get("links", []):
                para(link["title"] + " — " + link["url"], "Meta")
    def footer(canvas, document):
        canvas.setFont(font, 8)
        canvas.setFillColor(colors.HexColor("#526666"))
        canvas.drawString(48, 25, "SeviL · Personal study handout · Verify unclear passages against original evidence")
        canvas.drawRightString(document.pagesize[0] - 48, 25, str(document.page))
    doc.build(story, onFirstPage=footer, onLaterPages=footer)
    return out.getvalue()

from typing import Literal
from pydantic import BaseModel, Field

LANGUAGES = {"en", "tr", "zh-Hans", "ja", "ko", "de", "fr", "es"}
Provider = Literal["openai", "gemini", "claude"]

class GenerationInput(BaseModel):
    provider: Provider | None = None
    transcription_provider: Literal["openai", "gemini"] | None = None

class CourseInput(BaseModel):
    title: str = Field(min_length=1, max_length=160)
    university: str = Field(default="", max_length=160)
    degree: str = Field(default="", max_length=160)
    professor: str = Field(default="", max_length=160)
    code: str = Field(default="", max_length=40)
    lecture_language: str = "en"
    output_language: str = "en"

class SessionInput(BaseModel):
    course_id: str
    title: str = Field(min_length=1, max_length=160)
    recording_permission: bool = False

class TranscriptInput(BaseModel):
    text: str = Field(min_length=1, max_length=200000)

class Section(BaseModel):
    heading: str
    body: str
    kind: Literal["lecture", "supporting", "explanation"]
    citations: list[str]

class Term(BaseModel):
    term: str
    translation: str
    definition: str

class Handout(BaseModel):
    title: str
    summary: str
    objectives: list[str]
    sections: list[Section]
    glossary: list[Term]
    questions: list[str]
    uncertainties: list[str]

class ChatLink(BaseModel):
    title: str
    url: str

class ChatReply(BaseModel):
    answer: str
    citations: list[str]
    links: list[ChatLink]

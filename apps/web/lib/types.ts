export type Course = {
  id: string;
  title: string;
  university: string;
  degree: string;
  professor: string;
  code: string;
  lecture_language: string;
  output_language: string;
};
export type Asset = {
  id: string;
  name: string;
  kind: "audio" | "photo" | "resource";
  offset_ms: number;
  duration_ms: number;
  mime: string;
};
export type Handout = {
  title: string;
  summary: string;
  objectives: string[];
  sections: {
    heading: string;
    body: string;
    kind: string;
    citations: string[];
  }[];
  glossary: { term: string; translation: string; definition: string }[];
  questions: string[];
  uncertainties: string[];
};
export type Session = {
  id: string;
  course_id: string;
  title: string;
  created_at: string;
  status: "draft" | "processing" | "ready" | "failed";
  transcript: string;
  handout: Handout | null;
  error: string | null;
  assets?: Asset[];
  sources?: { id: string; label: string }[];
  generated_mode?: string;
  generated_provider?: string;
};

import contract from "../shared-ai-contract.json";
import { relevantText, validHTTPS, validateChat } from "./chat-context";

type Item = Record<string, any>;
type Provider = "gemini" | "openai" | "claude";
const labels = { gemini: "Gemini", openai: "OpenAI", claude: "Claude" };
// Individual keys stay in this tab's memory, never in a build, URL or persistent browser database.
const keys: Record<Provider, string> = { gemini: "", openai: "", claude: "" };
export function setDeviceKey(provider: Provider, value: string) {
  keys[provider] = value.trim();
}
export function deviceKey(provider: Provider) {
  return keys[provider];
}

let dbPromise: Promise<IDBDatabase> | undefined;
function database() {
  if (!dbPromise)
    dbPromise = new Promise<IDBDatabase>((resolve, reject) => {
      const request = indexedDB.open("sevil-device-library", 1);
      request.onupgradeneeded = () => {
        request.result.createObjectStore("records", { keyPath: "id" });
        request.result.createObjectStore("files", { keyPath: "id" });
      };
      request.onsuccess = () => resolve(request.result);
      request.onerror = () => reject(request.error);
    });
  return dbPromise;
}
async function transaction(
  store: string,
  mode: IDBTransactionMode,
  action: (s: IDBObjectStore) => IDBRequest,
): Promise<any> {
  const db = await database();
  return new Promise((resolve, reject) => {
    const tx = db.transaction(store, mode);
    const request = action(tx.objectStore(store));
    let value: any;
    request.onsuccess = () => {
      value = request.result;
    };
    tx.oncomplete = () => resolve(value);
    tx.onerror = () => reject(tx.error);
  });
}
async function all(kind: string): Promise<Item[]> {
  return (await transaction("records", "readonly", (s) => s.getAll()))
    .filter((r: Item) => r.kind === kind)
    .map((r: Item) => r.value)
    .sort((a: Item, b: Item) =>
      String(b.created_at).localeCompare(String(a.created_at)),
    );
}
async function get(kind: string, id: string): Promise<Item> {
  const r = await transaction("records", "readonly", (s) => s.get(id));
  if (!r || r.kind !== kind) throw Error(`${kind} not found on this device`);
  return r.value;
}
async function save(kind: string, value: Item): Promise<Item> {
  value = {
    ...value,
    id: value.id || crypto.randomUUID(),
    created_at: value.created_at || new Date().toISOString(),
  };
  await transaction("records", "readwrite", (s) =>
    s.put({ id: value.id, kind, value }),
  );
  return value;
}
async function remove(id: string) {
  await transaction("records", "readwrite", (s) => s.delete(id));
}
async function assets(session: Item) {
  return (await all("asset")).filter(
    (a) =>
      a.session_id === session.id ||
      (a.kind === "resource" && a.course_id === session.course_id),
  );
}
const chatPending = new Set<string>();
function editable(session: Item) {
  if (chatPending.has(session.id))
    throw Error("Wait for the session tutor to finish.");
  if (session.status === "processing")
    throw Error("Wait for processing to finish.");
}
async function invalidate(session: Item) {
  await save("session", {
    ...session,
    status: "draft",
    handout: null,
    sources: [],
    error: null,
  });
}
export async function assetBlob(id: string): Promise<Blob> {
  const record = await transaction("files", "readonly", (s) => s.get(id));
  if (!record) throw Error("Attachment not found");
  return record.blob;
}
let ready: Promise<void> | undefined;
function initialize() {
  return (ready ??= (async () => {
    for (const message of await all("message"))
      if (message.status === "pending")
        await save("message", {
          ...message,
          status: "failed",
          error:
            "Reply interrupted. Your conversation is saved; retry the question.",
        });
    for (const session of await all("session"))
      if (session.status === "processing")
        await save("session", {
          ...session,
          status: "failed",
          error:
            "This tab closed during processing. Your files remain on this device; retry when ready.",
        });
  })());
}

function requireKey(provider: Provider) {
  if (!keys[provider])
    throw Error(
      `Add your ${labels[provider]} API key in Settings in this tab. No laptop backend is needed.`,
    );
  return keys[provider];
}
async function checked(provider: Provider, response: Response) {
  if (!response.ok)
    throw Error(
      `${labels[provider]} returned HTTP ${response.status}. Check your key, internet connection, model access and quota. No other service was used.`,
    );
  return response;
}
async function remote(
  provider: Provider,
  path: string,
  body: Item,
): Promise<Item> {
  const base = {
    gemini: "https://generativelanguage.googleapis.com/v1beta/",
    openai: "https://api.openai.com/v1/",
    claude: "https://api.anthropic.com/v1/",
  }[provider];
  const headers: Record<string, string> = {
    "Content-Type": "application/json",
  };
  const key = requireKey(provider);
  if (provider === "gemini") headers["x-goog-api-key"] = key;
  else if (provider === "openai") headers.Authorization = `Bearer ${key}`;
  else
    Object.assign(headers, {
      "x-api-key": key,
      "anthropic-version": "2023-06-01",
      "anthropic-dangerous-direct-browser-access": "true",
    });
  try {
    return await (
      await checked(
        provider,
        await fetch(base + path, {
          method: "POST",
          headers,
          body: JSON.stringify(body),
        }),
      )
    ).json();
  } catch (error) {
    if (error instanceof TypeError)
      throw Error(
        `${labels[provider]} could not be reached. Check internet access and browser permissions. Your files are saved locally.`,
      );
    throw error;
  }
}
async function base64(blob: Blob): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => resolve(String(reader.result).split(",")[1]);
    reader.onerror = () => reject(reader.error);
    reader.readAsDataURL(blob);
  });
}
async function mediaPart(blob: Blob, mime: string) {
  return { inlineData: { mimeType: mime, data: await base64(blob) } };
}
async function gemini(
  parts: Item[],
  instructions = "",
  schema = false,
  search = false,
  responseSchema: Item = contract.schema,
  maxTokens = 65536,
): Promise<Item> {
  const body: Item = {
    contents: [{ role: "user", parts }],
    generationConfig: { maxOutputTokens: maxTokens },
  };
  if (instructions)
    body.systemInstruction = { parts: [{ text: instructions }] };
  if (schema)
    Object.assign(body.generationConfig, {
      responseMimeType: "application/json",
      responseJsonSchema: responseSchema,
    });
  if (search) body.tools = [{ google_search: {} }];
  return remote(
    "gemini",
    `models/${contract.models.gemini}:generateContent`,
    body,
  );
}
function geminiText(result: Item) {
  const candidate = result.candidates?.[0];
  if (!candidate)
    throw Error(
      "Gemini returned no content. Your files remain on this device.",
    );
  if (candidate.finishReason !== "STOP")
    throw Error(
      "Gemini response incomplete. Split the session or retry; no partial handout was published.",
    );
  return candidate.content.parts
    .filter((p: Item) => !p.thought)
    .map((p: Item) => p.text || "")
    .join("");
}
async function withMedia(
  blob: Blob,
  mime: string,
  operation: (part: Item) => Promise<string>,
): Promise<string> {
  if (blob.size < 8 * 1024 * 1024)
    return operation(await mediaPart(blob, mime));
  const key = requireKey("gemini");
  const start = await checked(
    "gemini",
    await fetch(
      "https://generativelanguage.googleapis.com/upload/v1beta/files",
      {
        method: "POST",
        headers: {
          "x-goog-api-key": key,
          "Content-Type": "application/json",
          "X-Goog-Upload-Protocol": "resumable",
          "X-Goog-Upload-Command": "start",
          "X-Goog-Upload-Header-Content-Length": String(blob.size),
          "X-Goog-Upload-Header-Content-Type": mime,
        },
        body: JSON.stringify({ file: { display_name: "SeviL lecture" } }),
      },
    ),
  );
  const address = start.headers.get("X-Goog-Upload-URL");
  if (!address)
    throw Error(
      "The browser could not start a large-file Gemini upload. Try a shorter recording; your original is saved.",
    );
  const url = new URL(address);
  if (url.protocol !== "https:" || !url.hostname.endsWith(".googleapis.com"))
    throw Error("Unexpected upload address");
  let file = (
    await (
      await checked(
        "gemini",
        await fetch(url, {
          method: "POST",
          headers: {
            "x-goog-api-key": key,
            "X-Goog-Upload-Offset": "0",
            "X-Goog-Upload-Command": "upload, finalize",
            "Content-Type": mime,
          },
          body: blob,
        }),
      )
    ).json()
  ).file;
  const name = file.name;
  if (typeof name !== "string" || !name.startsWith("files/"))
    throw Error("Invalid Gemini file response");
  try {
    for (let i = 0; i < 90 && file.state === "PROCESSING"; i++) {
      await new Promise((r) => setTimeout(r, 1000));
      file = await (
        await checked(
          "gemini",
          await fetch(
            `https://generativelanguage.googleapis.com/v1beta/${name}`,
            { headers: { "x-goog-api-key": key } },
          ),
        )
      ).json();
    }
    if (["FAILED", "PROCESSING"].includes(file.state))
      throw Error(
        "Gemini could not prepare the recording. It remains saved on this device.",
      );
    return await operation({ fileData: { mimeType: mime, fileUri: file.uri } });
  } finally {
    await fetch(`https://generativelanguage.googleapis.com/v1beta/${name}`, {
      method: "DELETE",
      headers: { "x-goog-api-key": key },
    }).catch(() => {});
  }
}
async function transcribe(provider: Provider, asset: Item, language: string) {
  const blob = await assetBlob(asset.id),
    prompt = `${contract.transcript_prompt} Expected language: ${language}.`;
  if (provider === "gemini")
    return withMedia(blob, asset.mime, async (part) =>
      geminiText(await gemini([{ text: prompt }, part])),
    );
  if (provider !== "openai")
    throw Error("Choose Gemini or OpenAI for audio transcription");
  if (blob.size >= 24 * 1024 * 1024)
    throw Error(
      "For OpenAI, split audio into files under 24 MiB or choose Gemini for long recordings.",
    );
  const form = new FormData();
  form.set("file", blob, asset.name);
  form.set("model", "gpt-transcribe");
  form.set("prompt", prompt);
  return (
    await (
      await checked(
        provider,
        await fetch("https://api.openai.com/v1/audio/transcriptions", {
          method: "POST",
          headers: { Authorization: `Bearer ${requireKey(provider)}` },
          body: form,
        }),
      )
    ).json()
  ).text;
}
async function makeNote(
  provider: Provider,
  evidence: Item,
  attachments: Item[],
  chat = false,
) {
  const instructions = chat ? contract.chat_prompt : contract.notes_prompt;
  const schema = chat ? contract.chat_schema : contract.schema;
  const parts: Item[] = [{ text: JSON.stringify(evidence) }];
  for (const a of attachments)
    parts.push(await mediaPart(await assetBlob(a.id), a.mime));
  if (provider === "gemini")
    return JSON.parse(
      geminiText(
        await gemini(
          parts,
          instructions,
          true,
          false,
          schema,
          chat ? 8192 : 65536,
        ),
      ),
    );
  const content = parts.map((p) => {
    if (p.text)
      return {
        type: provider === "openai" ? "input_text" : "text",
        text: p.text,
      };
    const { mimeType: mime, data } = p.inlineData;
    if (provider === "openai")
      return mime === "application/pdf"
        ? {
            type: "input_file",
            filename: "course.pdf",
            file_data: `data:${mime};base64,${data}`,
          }
        : { type: "input_image", image_url: `data:${mime};base64,${data}` };
    return {
      type: mime === "application/pdf" ? "document" : "image",
      source: { type: "base64", media_type: mime, data },
    };
  });
  if (provider === "openai") {
    const r = await remote(provider, "responses", {
      model: contract.models.openai,
      store: false,
      instructions,
      input: [{ role: "user", content }],
      text: {
        format: {
          type: "json_schema",
          name: "lecture_handout",
          strict: true,
          schema,
        },
      },
    });
    return JSON.parse(
      r.output
        .flatMap((i: Item) => i.content || [])
        .filter((p: Item) => p.type === "output_text")
        .map((p: Item) => p.text)
        .join(""),
    );
  }
  const r = await remote(provider, "messages", {
    model: contract.models.claude,
    max_tokens: chat ? 4096 : 16384,
    system: instructions,
    messages: [{ role: "user", content }],
    output_config: { format: { type: "json_schema", schema } },
  });
  if (r.stop_reason !== "end_turn") throw Error("Claude response incomplete");
  return JSON.parse(
    r.content
      .filter((p: Item) => p.type === "text")
      .map((p: Item) => p.text)
      .join(""),
  );
}
export function validateNote(note: Item, sources: Item[]) {
  if (
    typeof note.title !== "string" ||
    typeof note.summary !== "string" ||
    !["objectives", "sections", "glossary", "questions", "uncertainties"].every(
      (k) => Array.isArray(note[k]),
    )
  )
    throw Error("AI returned incomplete notes; retry generation.");
  if (
    !["objectives", "questions", "uncertainties"].every((k) =>
      note[k].every((v: unknown) => typeof v === "string"),
    ) ||
    !note.glossary.every((v: Item) =>
      ["term", "translation", "definition"].every(
        (k) => typeof v[k] === "string",
      ),
    )
  )
    throw Error("AI returned malformed notes; retry generation.");
  const ids = new Set(sources.map((s) => s.id));
  for (const section of note.sections) {
    const refs = section.citations;
    if (
      !["lecture", "supporting", "explanation"].includes(section.kind) ||
      typeof section.heading !== "string" ||
      typeof section.body !== "string" ||
      !Array.isArray(refs) ||
      !refs.every((r) => ids.has(r)) ||
      (section.kind !== "explanation" && !refs.length) ||
      (section.kind === "lecture" &&
        !refs.some(
          (r) => r.startsWith("audio:") || r.startsWith("transcript:"),
        )) ||
      (section.kind === "supporting" &&
        !refs.some(
          (r: string) =>
            r.startsWith("photo:") ||
            r.startsWith("resource:") ||
            r.startsWith("web:") ||
            r.startsWith("link:"),
        ))
    )
      throw Error(
        "AI returned invalid or unsupported citations; no handout was published.",
      );
  }
}
async function generate(
  id: string,
  provider: Provider,
  audioProvider: Provider,
) {
  let s = await get("session", id);
  try {
    const course = await get("course", s.course_id),
      list = await assets(s),
      sources: Item[] = [],
      attachments: Item[] = [];
    if (!s.transcript) {
      const audio = list
        .filter((a) => a.kind === "audio")
        .sort((a, b) => a.offset_ms - b.offset_ms);
      if (!audio.length)
        throw Error("Record audio or save a transcript first.");
      const chunks: string[] = [],
        refs: Item[] = [];
      for (const a of audio) {
        const ref = `audio:${a.id}:${a.offset_ms}`;
        refs.push({
          id: ref,
          label: `Audio clip at ${Math.floor(a.offset_ms / 1000)}s (clip-level timing)`,
        });
        chunks.push(
          `[${ref}] ${await transcribe(audioProvider, a, course.lecture_language)}`,
        );
      }
      s = {
        ...s,
        transcript: chunks.join("\n\n"),
        transcript_sources: refs,
        transcript_provider: audioProvider,
      };
      await save("session", s);
    }
    sources.push(
      ...(s.transcript_sources || [
        { id: "transcript:manual", label: "Student transcript" },
      ]),
    );
    const cached = await get("discovery", `${s.course_id}:${provider}`).catch(
      () => null,
    );
    if (cached?.links?.length)
      sources.push({
        id: `web:${s.course_id}`,
        label: "Public-source search summary; verify originals",
        text: cached.summary,
        links: cached.links,
      });
    if (list.filter((a) => a.kind === "photo").length > 20)
      throw Error("Split sessions with more than 20 board photos.");
    let total = 0;
    for (const a of list.filter((a) => a.kind !== "audio")) {
      const source: Item = {
        id: a.kind === "photo" ? `photo:${a.id}` : `resource:${a.id}:1`,
        label:
          a.name +
          (a.kind === "photo"
            ? ` · captured at ${Math.floor(a.offset_ms / 1000)}s`
            : " · attached resource"),
      };
      if (a.mime === "text/plain") {
        source.text = await (await assetBlob(a.id)).text();
        if (source.text.length > 100000)
          throw Error("Attach a shorter text resource.");
      } else {
        total += a.size;
        if (total > 30 * 1024 * 1024)
          throw Error("Combined images/PDFs must be under 30 MiB.");
        attachments.push(a);
        source.attachment_index = attachments.length - 1;
      }
      sources.push(source);
    }
    sources.push(
      ...(await all("link"))
        .filter((l) => l.session_id === s.id)
        .map((l) => ({
          id: `link:${l.id}`,
          label: l.title + " · " + l.url,
          text: l.text,
          links: [{ url: l.url, title: l.title }],
        })),
    );
    const note = await makeNote(
      provider,
      { course, session_title: s.title, transcript: s.transcript, sources },
      attachments,
    );
    validateNote(note, sources);
    await save("session", {
      ...s,
      status: "ready",
      handout: note,
      sources,
      generated_provider: provider,
      generated_mode: "live",
      error: null,
    });
  } catch (error) {
    await save("session", {
      ...s,
      status: "failed",
      handout: null,
      error:
        error instanceof Error
          ? error.message
          : "Processing failed; original files are saved on this device.",
    });
  }
}
async function discover(course: Item, provider: Provider, question = "") {
  let summary = "",
    links: Item[] = [];
  const add = (url: string, title: string) => {
    try {
      const u = new URL(url);
      if (u.protocol === "https:") links.push({ url, title });
    } catch {}
  };
  if (provider === "gemini") {
    const r = await gemini(
      [{ text: JSON.stringify({ course, question }) }],
      contract.search_prompt,
      false,
      true,
    );
    summary = geminiText(r);
    for (const c of r.candidates || [])
      for (const chunk of c.groundingMetadata?.groundingChunks || [])
        if (chunk.web) add(chunk.web.uri, chunk.web.title || chunk.web.uri);
  } else if (provider === "openai") {
    const r = await remote(provider, "responses", {
      model: contract.models.openai,
      store: false,
      instructions: contract.search_prompt,
      input: JSON.stringify({ course, question }),
      tools: [{ type: "web_search" }],
    });
    for (const c of r.output.flatMap((i: Item) => i.content || [])) {
      summary += c.text || "";
      for (const a of c.annotations || [])
        if (a.type === "url_citation") add(a.url, a.title);
    }
  } else {
    const r = await remote(provider, "messages", {
      model: contract.models.claude,
      max_tokens: 8192,
      system: contract.search_prompt,
      messages: [
        { role: "user", content: JSON.stringify({ course, question }) },
      ],
      tools: [{ type: "web_search_20250305", name: "web_search", max_uses: 3 }],
    });
    for (const c of r.content) {
      summary += c.text || "";
      for (const a of c.citations || []) add(a.url, a.title);
    }
  }
  return save("discovery", { id: `${course.id}:${provider}`, summary, links });
}
const mimeTypes: Record<string, string> = {
  m4a: "audio/m4a",
  mp4: "audio/mp4",
  mp3: "audio/mpeg",
  wav: "audio/wav",
  webm: "audio/webm",
  ogg: "audio/ogg",
  pdf: "application/pdf",
  txt: "text/plain",
  md: "text/plain",
  jpg: "image/jpeg",
  jpeg: "image/jpeg",
  png: "image/png",
  webp: "image/webp",
};
async function upload(id: string, form: FormData) {
  const s = await get("session", id);
  editable(s);
  const blob = form.get("file");
  if (!(blob instanceof File)) throw Error("Select a file.");
  const kind = String(form.get("kind")),
    offset = Number(form.get("offset_ms") || 0),
    duration = Number(form.get("duration_ms") || 0);
  const mime = mimeTypes[blob.name.split(".").pop()!.toLowerCase()];
  if (
    !mime ||
    !(kind === "audio"
      ? mime.startsWith("audio/")
      : kind === "photo"
        ? mime.startsWith("image/")
        : ["application/pdf", "text/plain"].includes(mime))
  )
    throw Error("Unsupported attachment type");
  if (
    blob.size === 0 ||
    blob.size > (kind === "audio" ? 250 : 20) * 1024 * 1024
  )
    throw Error("Empty file or file exceeds the size limit.");
  const hash = Array.from(
    new Uint8Array(
      await crypto.subtle.digest("SHA-256", await blob.arrayBuffer()),
    ),
  )
    .map((b) => b.toString(16).padStart(2, "0"))
    .join("");
  const existing = (await all("asset")).find(
    (a) =>
      a.session_id === id &&
      a.kind === kind &&
      a.offset_ms === offset &&
      a.sha256 === hash,
  );
  if (existing) return existing;
  const related =
    kind === "resource"
      ? (await all("session")).filter((v) => v.course_id === s.course_id)
      : [s];
  related.forEach(editable);
  const asset = {
    id: crypto.randomUUID(),
    created_at: new Date().toISOString(),
    session_id: id,
    course_id: s.course_id,
    kind,
    name: blob.name,
    mime,
    size: blob.size,
    offset_ms: offset,
    duration_ms: duration,
    sha256: hash,
  };
  const db = await database();
  await new Promise<void>((res, rej) => {
    const tx = db.transaction(["records", "files"], "readwrite");
    tx.objectStore("records").put({
      id: asset.id,
      kind: "asset",
      value: asset,
    });
    tx.objectStore("files").put({ id: asset.id, blob });
    tx.oncomplete = () => res();
    tx.onerror = () => rej(tx.error);
  });
  for (const target of related)
    await invalidate(kind === "audio" ? { ...target, transcript: "" } : target);
  return asset;
}
async function sendChat(session: Item, body: Item) {
  editable(session);
  const provider = (body.provider || "gemini") as Provider;
  if (!["gemini", "openai", "claude"].includes(provider))
    throw Error("Choose a supported AI service.");
  requireKey(provider);
  const question = String(body.text || "").trim(),
    limits = contract.chat_limits;
  if (!question || question.length > limits.question_chars)
    throw Error("Enter a question under 4,000 characters.");
  const requestId = String(body.request_id || crypto.randomUUID());
  if (!/^[A-Za-z0-9-]{1,80}$/.test(requestId))
    throw Error("Invalid request ID");
  const prefix = `chat:${session.id}:${requestId}`,
    existing = await get("message", prefix + ":assistant").catch(() => null);
  if (existing) return existing;
  const list = await assets(session);
  if (!session.transcript && list.some((a) => a.kind === "audio"))
    throw Error(
      "Generate notes or save a transcript first to chat about the recording.",
    );
  if (
    !session.transcript &&
    !session.handout &&
    !list.length &&
    !(await all("link")).some((l) => l.session_id === session.id)
  )
    throw Error(
      "Add a transcript or session materials before asking the tutor.",
    );
  const history = (await all("message"))
    .filter((m) => m.session_id === session.id && m.status === "complete")
    .reverse()
    .slice(-limits.history_messages)
    .map((m) => ({
      role: m.role,
      content: relevantText(m.content, question, limits.history_message_chars),
    }));
  chatPending.add(session.id);
  let user: Item = {
    id: prefix + ":user",
    session_id: session.id,
    role: "user",
    content: question,
    provider,
    status: "pending",
    request_id: requestId,
  };
  try {
    user = await save("message", user);
    const course = await get("course", session.course_id),
      sources: Item[] = [],
      attachments: Item[] = [];
    for (const ref of session.transcript_sources || []) sources.push(ref);
    if (session.transcript && !sources.length)
      sources.push({
        id: "transcript:manual",
        label: "Student transcript; timing not verified",
      });
    let total = 0;
    if (list.filter((a) => a.kind === "photo").length > 20)
      throw Error("Split sessions with more than 20 board photos.");
    for (const a of list.filter((a) => a.kind !== "audio")) {
      const source: Item = {
        id: a.kind === "photo" ? `photo:${a.id}` : `resource:${a.id}:1`,
        label:
          a.name +
          (a.kind === "photo"
            ? ` · captured at ${Math.floor(a.offset_ms / 1000)}s`
            : ""),
      };
      if (a.mime === "text/plain")
        source.text = relevantText(
          await (await assetBlob(a.id)).text(),
          question,
          limits.text_resource_chars,
        );
      else {
        total += a.size;
        if (total > 30 * 1024 * 1024)
          throw Error("Combined images/PDFs must be under 30 MiB.");
        source.attachment_index = attachments.length;
        attachments.push(a);
      }
      sources.push(source);
    }
    for (const link of (await all("link")).filter(
      (l) => l.session_id === session.id,
    ))
      sources.push({
        id: `link:${link.id}`,
        label: link.title,
        text: relevantText(
          link.text || "",
          question,
          limits.text_resource_chars,
        ),
        links: [{ title: link.title, url: link.url }],
      });
    const found = body.search
      ? await discover(course, provider, question)
      : await get("discovery", `${course.id}:${provider}`).catch(() => null);
    if (found?.links?.length)
      sources.push({
        id: `web:${course.id}`,
        label: "Public web search; verify original links",
        text: relevantText(found.summary, question, limits.text_resource_chars),
        links: found.links,
      });
    const reply = await makeNote(
      provider,
      {
        course,
        session_title: session.title,
        transcript: relevantText(
          session.transcript || "",
          question,
          limits.transcript_chars,
        ),
        handout: session.handout,
        history,
        question,
        sources,
      },
      attachments,
      true,
    );
    validateChat(reply, sources);
    const assistant = await save("message", {
      id: prefix + ":assistant",
      session_id: session.id,
      role: "assistant",
      content: reply.answer,
      provider,
      model: contract.models[provider],
      status: "complete",
      request_id: requestId,
      sources: sources
        .filter((s) => reply.citations.includes(s.id))
        .map(({ id, label }) => ({ id, label })),
      links: reply.links,
    });
    await save("message", { ...user, status: "complete" });
    return assistant;
  } catch (error) {
    await save("message", {
      ...user,
      status: "failed",
      error:
        error instanceof Error
          ? error.message
          : "Reply failed. Retry your question.",
    });
    throw error;
  } finally {
    chatPending.delete(session.id);
  }
}
export async function localRequest(
  path: string,
  init: RequestInit = {},
): Promise<any> {
  await initialize();
  const url = new URL(path, "https://device.invalid"),
    parts = url.pathname.split("/").filter(Boolean),
    method = init.method || "GET",
    body = typeof init.body === "string" ? JSON.parse(init.body) : {};
  if (parts[1] === "links" && method === "DELETE") {
    const link = await get("link", parts[2]);
    const session = await get("session", link.session_id);
    editable(session);
    await remove(link.id);
    await invalidate(session);
    return;
  }
  if (
    parts[1] === "sessions" &&
    parts.length === 4 &&
    ["chat", "links"].includes(parts[3])
  ) {
    const session = await get("session", parts[2]);
    if (parts[3] === "chat") {
      if (method === "GET")
        return (await all("message"))
          .filter((m) => m.session_id === session.id)
          .reverse();
      if (method === "DELETE") {
        editable(session);
        for (const m of (await all("message")).filter(
          (m) => m.session_id === session.id,
        ))
          await remove(m.id);
        return;
      }
      return sendChat(session, body);
    }
    if (method === "GET")
      return (await all("link")).filter((l) => l.session_id === session.id);
    editable(session);
    const url = validHTTPS(body.url);
    if (!body.title?.trim() || String(body.text || "").length > 20000)
      throw Error("Enter a link title and an excerpt under 20,000 characters.");
    const link = await save("link", {
      session_id: session.id,
      title: body.title.trim().slice(0, 160),
      url,
      text: String(body.text || ""),
    });
    await invalidate(session);
    return link;
  }
  if (path === "/health")
    return { mode: "live", ai_configured: Object.values(keys).some(Boolean) };
  if (path === "/v1/providers")
    return {
      default_provider: "gemini",
      providers: (["openai", "gemini", "claude"] as Provider[]).map((p) => ({
        id: p,
        label: labels[p],
        configured: !!keys[p],
        audio_supported: p !== "claude",
        model: contract.models[p],
      })),
    };
  if (parts[1] === "courses") {
    if (parts.length === 2) {
      if (method === "GET") return all("course");
      if (!body.title?.trim()) throw Error("Enter a course title");
      return save("course", {
        university: "",
        degree: "",
        professor: "",
        code: "",
        lecture_language: "en",
        output_language: "en",
        ...body,
      });
    }
    const course = await get("course", parts[2]),
      provider = (body.provider ||
        url.searchParams.get("provider") ||
        "gemini") as Provider;
    if (parts[3] === "discover") return discover(course, provider);
    return get("discovery", `${course.id}:${provider}`).catch(() => ({
      id: course.id,
      summary: "",
      links: [],
    }));
  }
  if (parts[1] === "sessions") {
    if (parts.length === 2) {
      if (method === "GET") return all("session");
      await get("course", body.course_id);
      if (!body.title?.trim() || !body.recording_permission)
        throw Error("Enter a title and confirm permission to record.");
      return save("session", {
        ...body,
        status: "draft",
        transcript: "",
        handout: null,
        sources: [],
        error: null,
      });
    }
    const s = await get("session", parts[2]);
    if (parts.length === 3) {
      if (method === "DELETE") {
        editable(s);
        for (const a of (await all("asset")).filter(
          (a) => a.session_id === s.id && a.kind !== "resource",
        )) {
          await transaction("files", "readwrite", (f) => f.delete(a.id));
          await remove(a.id);
        }
        for (const item of [
          ...(await all("message")),
          ...(await all("link")),
        ].filter((m) => m.session_id === s.id))
          await remove(item.id);
        await remove(s.id);
        return;
      }
      return { ...s, assets: await assets(s) };
    }
    editable(s);
    if (parts[3] === "assets" && init.body instanceof FormData)
      return upload(s.id, init.body);
    if (parts[3] === "transcript") {
      if (!body.text?.trim()) throw Error("Enter a transcript");
      await invalidate({
        ...s,
        transcript: body.text,
        transcript_provider: "manual",
        transcript_sources: [
          {
            id: "transcript:manual",
            label: "Student-edited transcript; audio timing not verified",
          },
        ],
      });
      return get("session", s.id);
    }
    if (parts[3] === "demo")
      return save("session", {
        ...s,
        status: "ready",
        handout: structuredClone(contract.demo_handout),
        sources: [],
        generated_mode: "demo",
        generated_provider: "demo",
      });
    if (parts[3] === "generate") {
      const provider = (body.provider || "gemini") as Provider,
        audio = (body.transcription_provider ||
          (provider !== "claude" ? provider : "")) as Provider;
      requireKey(provider);
      if (!s.transcript) {
        if (!["gemini", "openai"].includes(audio))
          throw Error("Choose an audio service or save a transcript");
        requireKey(audio);
      }
      const value = await save("session", {
        ...s,
        status: "processing",
        handout: null,
        error: null,
      });
      void generate(s.id, provider, audio);
      return value;
    }
  }
  if (parts[1] === "assets" && method === "DELETE") {
    const a = await get("asset", parts[2]),
      related = (await all("session")).filter(
        (s) =>
          s.course_id === a.course_id &&
          (a.kind === "resource" || s.id === a.session_id),
      );
    related.forEach(editable);
    await transaction("files", "readwrite", (s) => s.delete(a.id));
    await remove(a.id);
    for (const s of related)
      await invalidate(a.kind === "audio" ? { ...s, transcript: "" } : s);
    return;
  }
  throw Error("Unsupported local operation");
}
function escapeHTML(value: string) {
  return value.replace(
    /[&<>"']/g,
    (c) =>
      ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[
        c
      ]!,
  );
}
export async function printHandout(id: string) {
  const s = await get("session", id);
  if (!s.handout) throw Error("Generate a handout first");
  const note = s.handout,
    course = await get("course", s.course_id),
    list = await assets(s),
    photos: string[] = [];
  for (const a of list.filter((a) => a.kind === "photo")) {
    const blob = await assetBlob(a.id);
    photos.push(
      `<figure><img src="data:${a.mime};base64,${await base64(blob)}"/><figcaption>${escapeHTML(a.name)} · captured at ${Math.floor(a.offset_ms / 1000)}s</figcaption></figure>`,
    );
  }
  const p = (text: string) => `<p>${escapeHTML(String(text))}</p>`,
    h = (text: string) => `<h2>${escapeHTML(text)}</h2>`,
    li = (list: string[]) =>
      `<ul>${list.map((v) => `<li>${escapeHTML(v)}</li>`).join("")}</ul>`;
  const html = `<!doctype html><html><head><meta charset="utf-8"><title>${escapeHTML(note.title)}</title><style>@page{margin:18mm}body{font:11pt/1.7 system-ui,sans-serif;color:#233b38;max-width:800px;margin:auto}h1,h2{color:#246d5e;break-after:avoid}h1{font-size:24pt}h2{font-size:15pt}small,figcaption{font-size:9pt;color:#687d70}figure{break-inside:avoid}img{max-width:100%;max-height:70vh}p{white-space:pre-wrap}</style></head><body><small>SeviL / LECTURE NOTES</small><h1>${escapeHTML(note.title)}</h1>${p(course.title + " · " + course.university + " · " + course.professor)}${p("Generated with: " + (s.generated_provider || "demo"))}${p(note.summary)}${h("Learning objectives")}${li(note.objectives)}${note.sections.map((section: Item) => h(section.heading) + p(section.kind) + p(section.body) + section.citations.map((r: string) => p(s.sources.find((v: Item) => v.id === r)?.label || r)).join("")).join("")}${h("Bilingual glossary")}${note.glossary.map((t: Item) => h(t.term + " / " + t.translation) + p(t.definition)).join("")}${h("Review questions")}${li(note.questions)}${h("Check before studying")}${li(note.uncertainties)}${photos.join("")}</body></html>`;
  const iframe = document.createElement("iframe");
  iframe.style.cssText =
    "position:fixed;width:1px;height:1px;left:-9999px;top:0";
  iframe.title = "SeviL PDF export";
  iframe.onload = () => {
    iframe.contentWindow?.focus();
    iframe.contentWindow?.print();
    setTimeout(() => iframe.remove(), 60000);
  };
  iframe.srcdoc = html;
  document.body.appendChild(iframe);
}

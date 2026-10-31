import "fake-indexeddb/auto";
import { test } from "node:test";
import assert from "node:assert/strict";
import { FileReader } from "./file-reader";
import {
  localRequest,
  assetBlob,
  setDeviceKey,
  deviceKey,
  validateNote,
} from "../lib/device-workspace";
Object.assign(globalThis, { FileReader });
const post = (path: string, body: object) =>
  localRequest(path, { method: "POST", body: JSON.stringify(body) });
async function fixture() {
  const c = await post("/v1/courses", {
    title: "Synthetic physics",
    university: "Test university",
  });
  const s = await post("/v1/sessions", {
    title: "Test lecture",
    course_id: c.id,
    recording_permission: true,
  });
  return { c, s };
}
async function attach(id: string, file: File, kind: string, offset = 0) {
  const form = new FormData();
  form.set("file", file);
  form.set("kind", kind);
  form.set("offset_ms", String(offset));
  return localRequest(`/v1/sessions/${id}/assets`, {
    method: "POST",
    body: form,
  });
}
test("local library works offline, keeps course resources and timestamped originals", async () => {
  const original = globalThis.fetch;
  globalThis.fetch = async () => {
    throw Error("Unexpected network access");
  };
  try {
    const { c, s } = await fixture();
    const board = await attach(
      s.id,
      new File(["synthetic-image"], "board.jpg", { type: "image/jpeg" }),
      "photo",
      12500,
    );
    const duplicate = await attach(
      s.id,
      new File(["synthetic-image"], "board.jpg"),
      "photo",
      12500,
    );
    assert.equal(board.id, duplicate.id);
    assert.equal(await (await assetBlob(board.id)).text(), "synthetic-image");
    const resource = await attach(
      s.id,
      new File(["Open textbook extract"], "chapter.txt"),
      "resource",
    );
    const other = await post("/v1/sessions", {
      title: "Second lecture",
      course_id: c.id,
      recording_permission: true,
    });
    assert.equal(
      (await localRequest(`/v1/sessions/${other.id}`)).assets[0].id,
      resource.id,
    );
    await post(`/v1/sessions/${s.id}/demo`, {});
    assert.equal((await localRequest(`/v1/sessions/${s.id}`)).status, "ready");
    await localRequest(`/v1/sessions/${s.id}`, { method: "DELETE" });
    await assert.rejects(assetBlob(board.id));
    assert.equal(
      await (await assetBlob(resource.id)).text(),
      "Open textbook extract",
    );
    assert.equal(
      (await localRequest(`/v1/sessions/${other.id}`)).assets.length,
      1,
    );
  } finally {
    globalThis.fetch = original;
  }
});
test("missing Gemini key fails before network and leaves recording intact", async () => {
  const { s } = await fixture();
  setDeviceKey("gemini", "");
  const audio = await attach(
    s.id,
    new File(["test-audio"], "lecture.wav"),
    "audio",
    3000,
  );
  await assert.rejects(
    post(`/v1/sessions/${s.id}/generate`, { provider: "gemini" }),
    /Settings/,
  );
  assert.equal((await localRequest(`/v1/sessions/${s.id}`)).status, "draft");
  assert.equal(await (await assetBlob(audio.id)).text(), "test-audio");
});
test("Gemini calls Google directly with private header, returns cited notes, never saves key", async () => {
  const { s } = await fixture();
  const audio = await attach(
    s.id,
    new File(["test-audio"], "lecture.wav"),
    "audio",
    1000,
  );
  const original = globalThis.fetch,
    calls: { url: string; body: any }[] = [];
  setDeviceKey("gemini", "synthetic-key-not-a-secret");
  globalThis.fetch = async (input, init) => {
    const url = String(input),
      body = JSON.parse(String(init?.body));
    assert.ok(url.startsWith("https://generativelanguage.googleapis.com/"));
    assert.ok(!url.includes("synthetic-key"));
    assert.equal(
      new Headers(init?.headers).get("x-goog-api-key"),
      "synthetic-key-not-a-secret",
    );
    calls.push({ url, body });
    const note = {
      title: "Momentum",
      summary: "Test summary",
      objectives: ["Explain momentum"],
      sections: [
        {
          heading: "Definition",
          kind: "lecture",
          body: "Momentum is mass times velocity.",
          citations: [`audio:${audio.id}:1000`],
        },
      ],
      glossary: [],
      questions: ["What is momentum?"],
      uncertainties: [],
    };
    return Response.json({
      candidates: [
        {
          finishReason: "STOP",
          content: {
            parts: [
              {
                text:
                  calls.length === 1
                    ? "Momentum is mass times velocity."
                    : JSON.stringify(note),
              },
            ],
          },
        },
      ],
    });
  };
  try {
    await post(`/v1/sessions/${s.id}/generate`, { provider: "gemini" });
    let result: any;
    for (let i = 0; i < 100; i++) {
      result = await localRequest(`/v1/sessions/${s.id}`);
      if (result.status !== "processing") break;
      await new Promise((r) => setTimeout(r, 10));
    }
    assert.equal(result.status, "ready", result.error);
    assert.equal(calls.length, 2);
    assert.ok(calls[0].body.contents[0].parts[1].inlineData.data);
    assert.equal(
      calls[1].body.generationConfig.responseMimeType,
      "application/json",
    );
    assert.equal(result.transcript_provider, "gemini");
    assert.equal(result.generated_provider, "gemini");
    assert.ok(!JSON.stringify(result).includes(deviceKey("gemini")));
  } finally {
    globalThis.fetch = original;
    setDeviceKey("gemini", "");
  }
});
test("unknown citations are rejected", () => {
  const note = {
    title: "Test",
    summary: "Test",
    objectives: [],
    sections: [
      {
        heading: "Claim",
        body: "Claim",
        kind: "lecture",
        citations: ["invented"],
      },
    ],
    glossary: [],
    questions: [],
    uncertainties: [],
  };
  assert.throws(
    () => validateNote(note, [{ id: "transcript:manual" }]),
    /citations/,
  );
});

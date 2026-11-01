import "fake-indexeddb/auto";
import { test } from "node:test";
import assert from "node:assert/strict";
import { FileReader } from "./file-reader";
import { localRequest, setDeviceKey } from "../lib/device-workspace";
import { relevantText, validHTTPS, validateChat } from "../lib/chat-context";
Object.assign(globalThis, { FileReader });
const post = (path: string, body: object) =>
  localRequest(path, { method: "POST", body: JSON.stringify(body) });
async function fixture(title = "Momentum") {
  const course = await post("/v1/courses", { title, output_language: "tr" });
  const session = await post("/v1/sessions", {
    title,
    course_id: course.id,
    recording_permission: true,
  });
  await post(`/v1/sessions/${session.id}/transcript`, {
    text: "Momentum is mass times velocity. Total momentum is conserved without external forces.",
  });
  return session;
}
test("links are local, scoped to a session, validated, and removed with its conversation", async () => {
  const a = await fixture(),
    b = await fixture("Other classroom");
  await assert.rejects(
    post(`/v1/sessions/${a.id}/links`, {
      title: "Bad",
      url: "javascript:alert(1)",
    }),
    /HTTPS/,
  );
  const link = await post(`/v1/sessions/${a.id}/links`, {
    title: "Course reading",
    url: "https://example.edu/reading",
    text: "An isolated system conserves momentum.",
  });
  assert.equal((await localRequest(`/v1/sessions/${a.id}/links`)).length, 1);
  assert.equal((await localRequest(`/v1/sessions/${b.id}/links`)).length, 0);
  await localRequest(`/v1/sessions/${a.id}`, { method: "DELETE" });
  await assert.rejects(
    localRequest(`/v1/links/${link.id}`, { method: "DELETE" }),
    /not found/,
  );
});
for (const provider of ["gemini", "openai", "claude"] as const) {
  test(`${provider} chat uses only its own classroom, preserves follow-ups and deduplicates retries`, async () => {
    const a = await fixture(),
      b = await fixture("PRIVATE_OTHER_CLASS");
    await post(`/v1/sessions/${b.id}/transcript`, {
      text: "PRIVATE_OTHER_CLASS_TRANSCRIPT",
    });
    const link = await post(`/v1/sessions/${a.id}/links`, {
      title: "Course reading",
      url: "https://example.edu/reading",
      text: "A system with no external force conserves momentum.",
    });
    const original = globalThis.fetch;
    let calls = 0;
    setDeviceKey(provider, "synthetic-test-key");
    globalThis.fetch = async (input, init) => {
      calls++;
      assert.ok(
        String(input).startsWith(
          {
            gemini: "https://generativelanguage.googleapis.com/",
            openai: "https://api.openai.com/",
            claude: "https://api.anthropic.com/",
          }[provider],
        ),
      );
      const body = JSON.parse(String(init?.body));
      const serialized = JSON.stringify(body);
      assert.ok(!serialized.includes("PRIVATE_OTHER_CLASS"));
      const content =
        provider === "gemini"
          ? body.contents[0].parts[0].text
          : provider === "openai"
            ? body.input[0].content[0].text
            : body.messages[0].content[0].text;
      const context = JSON.parse(content);
      assert.equal(context.course.output_language, "tr");
      assert.ok(context.transcript.includes("Momentum"));
      assert.ok(context.sources.some((s: any) => s.id === `link:${link.id}`));
      if (calls === 2) {
        assert.equal(context.history.length, 2);
        assert.equal(context.history[0].role, "user");
        assert.equal(context.history[1].role, "assistant");
      }
      const reply = JSON.stringify({
        answer:
          "Momentum is mass times velocity. Here is an additional example.",
        citations: ["transcript:manual", `link:${link.id}`],
        links: [{ title: "Course reading", url: link.url }],
      });
      if (provider === "gemini")
        return Response.json({
          candidates: [
            { finishReason: "STOP", content: { parts: [{ text: reply }] } },
          ],
        });
      if (provider === "openai") {
        assert.equal(body.store, false);
        assert.ok(body.text.format.schema.properties.answer);
        return Response.json({
          output: [{ content: [{ type: "output_text", text: reply }] }],
        });
      }
      assert.equal(
        new Headers(init?.headers).get(
          "anthropic-dangerous-direct-browser-access",
        ),
        "true",
      );
      return Response.json({
        stop_reason: "end_turn",
        content: [{ type: "text", text: reply }],
      });
    };
    try {
      const request = {
        provider,
        text: "Explain momentum simply",
        request_id: "first-question",
      };
      const first = await post(`/v1/sessions/${a.id}/chat`, request);
      const duplicate = await post(`/v1/sessions/${a.id}/chat`, request);
      assert.equal(first.id, duplicate.id);
      assert.equal(calls, 1);
      await post(`/v1/sessions/${a.id}/chat`, {
        provider,
        text: "Now give a second example",
        request_id: "second-question",
      });
      const saved = await localRequest(`/v1/sessions/${a.id}/chat`);
      assert.equal(saved.length, 4);
      assert.equal(saved[0].role, "user");
      assert.equal(saved[1].role, "assistant");
      assert.equal(saved[1].provider, provider);
      assert.equal(saved[1].sources.length, 2);
      assert.equal((await localRequest(`/v1/sessions/${b.id}/chat`)).length, 0);
      await localRequest(`/v1/sessions/${a.id}/chat`, { method: "DELETE" });
      assert.equal((await localRequest(`/v1/sessions/${a.id}/chat`)).length, 0);
    } finally {
      globalThis.fetch = original;
      setDeviceKey(provider, "");
    }
  });
}
test("unsupported sources fail safely and the same question can be retried without duplicate messages", async () => {
  const session = await fixture();
  setDeviceKey("gemini", "synthetic-test-key");
  const original = globalThis.fetch;
  let valid = false;
  globalThis.fetch = async () =>
    Response.json({
      candidates: [
        {
          finishReason: "STOP",
          content: {
            parts: [
              {
                text: JSON.stringify({
                  answer: "An explanation",
                  citations: [valid ? "transcript:manual" : "invented-source"],
                  links: [],
                }),
              },
            ],
          },
        },
      ],
    });
  const request = {
    provider: "gemini",
    text: "Explain",
    request_id: "retry-question",
  };
  try {
    await assert.rejects(
      post(`/v1/sessions/${session.id}/chat`, request),
      /unsupported/,
    );
    assert.equal(
      (await localRequest(`/v1/sessions/${session.id}/chat`))[0].status,
      "failed",
    );
    valid = true;
    await post(`/v1/sessions/${session.id}/chat`, request);
    const messages = await localRequest(`/v1/sessions/${session.id}/chat`);
    assert.equal(messages.length, 2);
    assert.ok(messages.every((m: any) => m.status === "complete"));
  } finally {
    globalThis.fetch = original;
    setDeviceKey("gemini", "");
  }
});
test("chat cannot invent clickable links and long text keeps matching evidence within its budget", () => {
  assert.throws(() => validHTTPS("https://user:password@example.edu"), /HTTPS/);
  assert.throws(
    () =>
      validateChat(
        {
          answer: "Test",
          citations: [],
          links: [{ title: "Invented", url: "https://invented.example/" }],
        },
        [],
      ),
    /unsupported/,
  );
  const text =
    "x".repeat(60000) + " angular momentum conservation " + "y".repeat(60000);
  const excerpt = relevantText(text, "angular momentum", 5000);
  assert.ok(excerpt.includes("angular momentum"));
  assert.ok(excerpt.length <= 5000);
  assert.ok(excerpt.includes("omitted"));
});

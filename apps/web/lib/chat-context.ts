export type Evidence = Record<string, any>;
/** Deterministic, bounded excerpts. Full originals always remain in device storage. */
export function relevantText(text: string, question: string, limit: number) {
  if (text.length <= limit) return text;
  const terms = [
    ...new Set(question.toLocaleLowerCase().match(/[\p{L}\p{N}]{2,}/gu) || []),
  ];
  const chunks: { start: number; text: string; score: number }[] = [];
  for (let start = 0; start < text.length; start += 1200) {
    const content = text.slice(start, start + 1200),
      lower = content.toLocaleLowerCase();
    chunks.push({
      start,
      text: content,
      score: terms.filter((term) => lower.includes(term)).length,
    });
  }
  return (
    "[Selected excerpts; some context is omitted]\n" +
    chunks
      .sort((a, b) => b.score - a.score || a.start - b.start)
      .slice(0, Math.max(1, Math.floor((limit - 100) / 1250)))
      .sort((a, b) => a.start - b.start)
      .map((c) => `[character ${c.start}] ${c.text}`)
      .join("\n…\n")
  );
}
export function validHTTPS(value: unknown) {
  const url = new URL(String(value));
  if (
    url.protocol !== "https:" ||
    !url.hostname ||
    url.username ||
    url.password
  )
    throw Error("Use a public HTTPS link without sign-in credentials.");
  return url.href;
}
export function validateChat(reply: Evidence, sources: Evidence[]) {
  const ids = new Set(sources.map((s) => s.id)),
    urls = new Set(sources.flatMap((s) => s.links || []).map((l) => l.url));
  if (
    typeof reply.answer !== "string" ||
    !reply.answer.trim() ||
    reply.answer.length > 30000 ||
    !Array.isArray(reply.citations) ||
    !reply.citations.every(
      (id: unknown) => typeof id === "string" && ids.has(id),
    ) ||
    !Array.isArray(reply.links) ||
    !reply.links.every(
      (l: Evidence) => typeof l.title === "string" && urls.has(l.url),
    )
  )
    throw Error(
      "The tutor returned an unsupported source or link. Please retry.",
    );
}

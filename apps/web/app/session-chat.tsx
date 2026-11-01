"use client";
import { useEffect, useRef, useState } from "react";
import {
  ArrowUp,
  BookOpen,
  Globe2,
  MessageCircle,
  Sparkles,
  Trash2,
} from "lucide-react";
import { localRequest } from "../lib/device-workspace";
type Message = {
  id: string;
  role: string;
  content: string;
  provider: string;
  status: string;
  request_id: string;
  error?: string;
  sources?: { id: string; label: string }[];
  links?: { title: string; url: string }[];
};
type Props = {
  id: string;
  provider: "gemini" | "openai" | "claude";
  disabled: boolean;
  t: (key: any) => string;
  onSources: () => void;
};
export default function SessionChat({
  id,
  provider,
  disabled,
  t,
  onSources,
}: Props) {
  const [messages, setMessages] = useState<Message[]>([]),
    [draft, setDraft] = useState(""),
    [busy, setBusy] = useState(false),
    [error, setError] = useState(""),
    [search, setSearch] = useState(false),
    [requestId, setRequestId] = useState<string | null>(null);
  const end = useRef<HTMLDivElement>(null);
  async function load() {
    setMessages(await localRequest(`/v1/sessions/${id}/chat`));
  }
  useEffect(() => {
    let alive = true;
    localRequest(`/v1/sessions/${id}/chat`)
      .then((m) => {
        if (alive) setMessages(m);
      })
      .catch((e) => {
        if (alive) setError(e.message);
      });
    return () => {
      alive = false;
    };
  }, [id]);
  useEffect(() => {
    end.current?.scrollIntoView({ block: "nearest", behavior: "smooth" });
  }, [messages.length, busy]);
  async function send(text = draft, retryId = requestId) {
    if (busy || disabled || !text.trim()) return;
    const request = retryId || crypto.randomUUID();
    setRequestId(request);
    setBusy(true);
    setError("");
    const task = localRequest(`/v1/sessions/${id}/chat`, {
      method: "POST",
      body: JSON.stringify({ provider, text, search, request_id: request }),
    });
    void load().catch(() => {});
    try {
      await task;
      setDraft("");
      setRequestId(null);
    } catch (e) {
      setError(e instanceof Error ? e.message : t("error"));
    } finally {
      try {
        await load();
      } catch (e) {
        setError(String(e));
      } finally {
        setBusy(false);
      }
    }
  }
  return (
    <section className="chat-panel" aria-label={t("sessionTutor")}>
      <div className="chat-heading">
        <span className="tutor-avatar">
          <Sparkles size={22} />
        </span>
        <div>
          <strong>{t("sessionTutor")}</strong>
          <small>{t("chatNotice")}</small>
        </div>
        <button
          className="icon-button"
          title={t("clearChat")}
          aria-label={t("clearChat")}
          disabled={busy || disabled || !messages.length}
          onClick={async () => {
            if (confirm(t("clearChatConfirm"))) {
              try {
                await localRequest(`/v1/sessions/${id}/chat`, {
                  method: "DELETE",
                });
                await load();
                setError("");
                setRequestId(null);
              } catch (e) {
                setError(String(e));
              }
            }
          }}
        >
          <Trash2 size={18} />
        </button>
      </div>
      <div
        className="chat-messages"
        role="log"
        aria-live="polite"
        aria-label={t("chat")}
      >
        {!messages.length && (
          <div className="chat-welcome">
            <span className="tutor-illustration">
              <MessageCircle size={36} />
              <Sparkles size={18} />
            </span>
            <h2>{t("askSession")}</h2>
            <p>{t("chatIntro")}</p>
            <div className="prompt-grid">
              {["explainSimply", "giveExample", "quizMe", "findResources"].map(
                (k) => (
                  <button
                    key={k}
                    className="prompt-card"
                    disabled={disabled || busy}
                    onClick={() => {
                      setDraft(t(k));
                      setRequestId(null);
                      if (k === "findResources") setSearch(true);
                    }}
                  >
                    <Sparkles size={16} />
                    {t(k)}
                  </button>
                ),
              )}
            </div>
          </div>
        )}
        {messages.map((m) => (
          <article className={`chat-message ${m.role}`} key={m.id}>
            <div className="message-byline">
              {m.role === "assistant" ? (
                <>
                  <Sparkles size={14} />
                  SeviL ·{" "}
                  {{ gemini: "Gemini", openai: "OpenAI", claude: "Claude" }[
                    m.provider
                  ] || m.provider}
                </>
              ) : (
                t("chat")
              )}
            </div>
            <p>{m.content}</p>
            {m.sources?.length ? (
              <details className="message-sources">
                <summary>
                  <BookOpen size={14} />
                  {t("sources")} · {m.sources.length}
                </summary>
                {m.sources.map((s) => (
                  <button
                    className="source-chip"
                    key={s.id}
                    onClick={onSources}
                  >
                    {s.label}
                  </button>
                ))}
              </details>
            ) : null}
            {m.links?.map((l) => (
              <a
                className="verified-link"
                key={l.url}
                href={l.url}
                target="_blank"
                rel="noopener noreferrer"
              >
                <Globe2 size={15} />
                {l.title}
              </a>
            ))}
            {m.status === "failed" && (
              <div className="message-failed">
                <small>{m.error}</small>
                <button
                  className="text-button"
                  disabled={busy || disabled}
                  onClick={() => send(m.content, m.request_id)}
                >
                  {t("retry")}
                </button>
              </div>
            )}
          </article>
        ))}
        {busy && (
          <div className="chat-thinking" role="status">
            <span className="thinking-dots">
              <i />
              <i />
              <i />
            </span>
            {t("thinking")}
          </div>
        )}
        <div ref={end} />
      </div>
      {error && (
        <p className="chat-error" role="alert">
          {error}
        </p>
      )}
      <form
        className="chat-composer"
        onSubmit={(e) => {
          e.preventDefault();
          void send();
        }}
      >
        <div className="composer-input">
          <textarea
            aria-label={t("chatPlaceholder")}
            placeholder={t("chatPlaceholder")}
            value={draft}
            maxLength={4000}
            rows={2}
            disabled={disabled || busy}
            onChange={(e) => {
              setDraft(e.target.value);
              setRequestId(null);
            }}
            onKeyDown={(e) => {
              if (
                e.key === "Enter" &&
                !e.shiftKey &&
                !e.nativeEvent.isComposing
              ) {
                e.preventDefault();
                void send();
              }
            }}
          />
          <button
            className="send-button"
            aria-label={t("send")}
            disabled={disabled || busy || !draft.trim()}
          >
            <ArrowUp size={22} />
          </button>
        </div>
        <div className="composer-meta">
          <label>
            <input
              type="checkbox"
              checked={search}
              disabled={busy || disabled}
              onChange={(e) => setSearch(e.target.checked)}
            />
            <Globe2 size={15} />
            {t("searchWeb")}
          </label>
          <span>
            {provider === "openai"
              ? "OpenAI"
              : provider === "gemini"
                ? "Gemini"
                : "Claude"}
          </span>
        </div>
      </form>
    </section>
  );
}

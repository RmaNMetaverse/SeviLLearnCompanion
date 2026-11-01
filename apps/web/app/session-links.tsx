"use client";
import { useEffect, useState } from "react";
import { Link2, Plus, Trash2 } from "lucide-react";
import { localRequest } from "../lib/device-workspace";
export default function SessionLinks({
  id,
  t,
  disabled,
  onSave,
}: {
  id: string;
  t: (key: any) => string;
  disabled: boolean;
  onSave: () => Promise<void>;
}) {
  const [links, setLinks] = useState<
      { id: string; title: string; url: string }[]
    >([]),
    [title, setTitle] = useState(""),
    [url, setUrl] = useState(""),
    [text, setText] = useState(""),
    [error, setError] = useState(""),
    [busy, setBusy] = useState(false);
  async function load() {
    setLinks(await localRequest(`/v1/sessions/${id}/links`));
  }
  useEffect(() => {
    void load().catch((e) => setError(e.message));
  }, [id]);
  return (
    <section className="panel links-panel">
      <h3>
        <Link2 size={19} />
        {t("sources")}
      </h3>
      {links.map((l) => (
        <div className="resource" key={l.id}>
          <Link2 size={17} />
          <a href={l.url} target="_blank" rel="noopener noreferrer">
            {l.title}
          </a>
          <button
            className="icon-button"
            aria-label={t("delete")}
            disabled={disabled || busy}
            onClick={async () => {
              try {
                await localRequest(`/v1/links/${l.id}`, { method: "DELETE" });
                await load();
                await onSave();
              } catch (e) {
                setError(String(e));
              }
            }}
          >
            <Trash2 size={16} />
          </button>
        </div>
      ))}
      <details>
        <summary className="add-link">
          <Plus size={17} />
          {t("addLink")}
        </summary>
        <form
          className="link-form"
          onSubmit={async (e) => {
            e.preventDefault();
            setBusy(true);
            setError("");
            try {
              await localRequest(`/v1/sessions/${id}/links`, {
                method: "POST",
                body: JSON.stringify({ title, url, text }),
              });
              setTitle("");
              setUrl("");
              setText("");
              await load();
              await onSave();
            } catch (e) {
              setError(e instanceof Error ? e.message : t("error"));
            } finally {
              setBusy(false);
            }
          }}
        >
          <p>{t("linkNotice")}</p>
          <label>
            {t("title")}
            <input
              required
              maxLength={160}
              value={title}
              onChange={(e) => setTitle(e.target.value)}
            />
          </label>
          <label>
            {t("linkUrl")}
            <input
              required
              type="url"
              value={url}
              onChange={(e) => setUrl(e.target.value)}
              placeholder="https://"
            />
          </label>
          <label>
            {t("linkExcerpt")}
            <textarea
              rows={4}
              maxLength={20000}
              value={text}
              onChange={(e) => setText(e.target.value)}
            />
          </label>
          <button className="primary" disabled={disabled || busy}>
            {t("save")}
          </button>
        </form>
      </details>
      {error && (
        <p role="alert" className="chat-error">
          {error}
        </p>
      )}
    </section>
  );
}

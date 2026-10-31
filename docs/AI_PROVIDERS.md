# AI service selection

Android, iOS and web expose an **AI service** dropdown: OpenAI, Gemini and Claude. The choice is retained on the client and sent explicitly with generation and source-search requests. The server reports which providers have a key configured, never the key itself. The initial client default comes from `SEVIL_DEFAULT_PROVIDER`; a saved choice takes precedence.

| Provider | Audio transcription | Notes and board images | PDF |
|---|---|---|---|
| OpenAI | Configured OpenAI transcription model | OpenAI notes model | Created by SeviL |
| Gemini | Gemini audio input | Gemini structured multimodal output | Created by SeviL |
| Claude | Explicitly choose Gemini/OpenAI, or use a saved transcript | Claude image input and structured output | Created by SeviL |

Claude's Messages integration has no audio input. When Claude is selected, clients display an **Audio service** dropdown. Direct API requests without an explicit audio provider and without a saved transcript are rejected before a provider call. There is no silent cross-provider fallback on missing keys, failures or quota errors. A saved transcript is reused without retranscribing, even when the notes provider changes.

## Server configuration

Keep keys only in the server's ignored `.env` or hosting secret store, never client code, Git or releases.

```dotenv
SEVIL_MODE=live
SEVIL_DEFAULT_PROVIDER=gemini
GEMINI_API_KEY=your-local-secret
GEMINI_MODEL=gemini-3.5-flash-lite
GEMINI_TRANSCRIPTION_MODEL=gemini-3.5-flash-lite
OPENAI_API_KEY=
OPENAI_TRANSCRIPTION_MODEL=gpt-transcribe
OPENAI_NOTES_MODEL=gpt-4.1
ANTHROPIC_API_KEY=
ANTHROPIC_MODEL=claude-sonnet-5-5
```

Restart the API after changing configuration. Model access and quota depend on the account. A free API key does not guarantee unlimited or permanently free requests. Audio, notes and web search each consume provider quota. Gemini-only sessions need no OpenAI requests; PDF rendering needs no external AI API.

Demo mode still generates explicitly synthetic content without provider calls. Live notes record the actual provider, display it in all clients and include it in the PDF. Public-source caches are isolated by course and provider.

## Verification

The supplied Gemini key was saved only in the ignored local environment file. A live Gemini-only smoke test passed with synthetic speech about cell biology, a synthetic board image and a text resource: transcription → structured notes with validated references → a two-page PDF containing the board image. The model was `gemini-3.5-flash-lite`. This does not establish real-classroom accuracy, long-session reliability or free-tier limits.

OpenAI and Claude adapter routing, image formatting, missing-key behavior and no-fallback handling have automated tests; their live requests were not tested with personal credentials in this change. `scripts/smoke-gemini.py` is an explicit opt-in live check using synthetic fixtures, excluded from CI. GitHub Actions receives no local API key.

## References

- [Gemini audio](https://ai.google.dev/gemini-api/docs/audio)
- [Gemini GenerateContent API](https://ai.google.dev/api/generate-content)
- [Gemini structured output](https://ai.google.dev/gemini-api/docs/structured-output)
- [Claude Messages API](https://platform.claude.com/docs/en/api/messages)
- [Claude structured output](https://platform.claude.com/docs/en/build-with-claude/structured-outputs)

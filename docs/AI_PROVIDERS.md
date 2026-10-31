# AI providers on each device

All three clients offer OpenAI, Gemini and Claude in the same service dropdown. Open Settings and enter your own key for the service you select. Gemini is the default. The provider catalog means a key is present on this device; API validity, model access and quota are checked when a request is made. The app never silently switches providers.

| Service | Notes and images/PDFs | Audio | Public-source search |
|---|---|---|---|
| Gemini | Direct generateContent with structured JSON | Gemini audio input, temporary Files API upload for larger clips | Google Search grounding |
| OpenAI | Direct Responses API with JSON schema | Direct gpt-transcribe, under 24 MiB per file | Responses web search |
| Claude | Direct Messages API with structured output | Use explicitly chosen Gemini/OpenAI, or save a transcript | Claude web search |

Models and shared instructions live in generated `shared/ai-contract.json`; update its generator `scripts/sync-ai-contract.py` and synchronize all three copies. Current notes models: `gemini-3.5-flash-lite`, `gpt-4.1`, `claude-sonnet-5-5`. Account availability and provider limits apply.

Gemini can transcribe audio and interpret board photos/PDF resources. The app creates the PDF itself; Gemini returns structured note content, not a finished PDF. Free key issuance does not guarantee unlimited free requests. Provider errors are shown and original files stay saved.

Android stores encrypted credentials with Android Keystore; iOS uses Keychain. Web keys stay in this tab's memory only and clear on reload/close. Credentials are sent in HTTPS authorization headers to the selected provider, never in URLs. The public app does not contain a developer's shared API secret. The laptop's ignored `.env` cannot configure an independent phone.

The app sends selected lecture evidence to the selected provider when generating, and course details when searching. Gemini's larger audio upload is temporary; deletion is attempted after processing, but the provider's own processing/retention policies apply. An interrupted app may prevent immediate cleanup. Browser cross-origin permissions or provider restrictions may prevent a request; use a native client or shorter recording when a large-file browser upload is unavailable.

Original recordings, clip-level transcript references, timestamped images, supporting resources and additional AI explanations remain distinguishable. Invalid source IDs prevent publishing a handout. These checks do not establish factual accuracy; review uncertainties and compare originals.

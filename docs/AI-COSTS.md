# AI costs and suggested student pricing

Planning estimate checked 1 November 2026; USD, paid standard rates, no cache/batch discounts or assumed free quota. These are modeled costs, not measured production averages. The app currently uses the student's own key and does not bill subscriptions.

## Vendor rates used

| App model | Input / 1M tokens | Output / 1M tokens |
|---|---:|---:|
| Gemini 3.5 Flash-Lite | $0.30, including audio | $2.50, including thinking |
| OpenAI GPT-4.1 | $2.00 | $8.00 |
| Claude Sonnet 5.5 | $2.00 | $10.00 |

Sources: [Google pricing](https://ai.google.dev/gemini-api/docs/pricing), [OpenAI GPT-4.1](https://developers.openai.com/api/docs/models/gpt-4.1), [Claude pricing](https://platform.claude.com/docs/en/about-claude/pricing). Model IDs are pinned in the shared AI contract. Recheck lifecycle and rates before a commercial launch; OpenAI currently labels GPT-4.1 deprecated.

[OpenAI gpt-transcribe](https://developers.openai.com/api/docs/pricing) is estimated at $0.0045 per minute. For the app's Gemini Generate Content audio path, [Google documents 1,920 audio tokens per minute](https://ai.google.dev/gemini-api/docs/generate-content/audio). Assume another 175 transcript output tokens per minute; speaking speed, language and timestamp formatting change that figure. Claude needs a separate transcript: use the explicitly selected Gemini/OpenAI transcription provider or supply text.

## Example month

Assume 40 lecture hours (20 two-hour sessions), 20 handouts, and 200 chat answers. Each handout averages 20,000 input + 2,500 output tokens. Each chat turn averages 8,000 input tokens including context/history/materials + 500 output tokens. This is 2M text input + 150,000 text output tokens, plus audio transcription. Transcript output is included in transcription cost and its later reuse is included in the text input budget.

| Provider route | Notes + chat | Transcription | Total/month |
|---|---:|---:|---:|
| Gemini throughout | $0.98 | $2.43 | **$3.41** |
| OpenAI throughout | $5.20 | $10.80 | **$16.00** |
| Claude + Gemini audio | $5.50 | $2.43 | **$7.93** |
| Claude + OpenAI audio | $5.50 | $10.80 | **$16.30** |

At 60 hours, 30 handouts and the same 200 chat answers: approximately $4.75 Gemini, $22.00 OpenAI, or $9.80 Claude with Gemini transcription. Reserve another 30–50% for retries, extra thinking, longer output, document/image tokens and estimation error. No paid search, taxes, storage, payment fees, store commission, support or marketing are included in these totals.

The 8,000-token average per chat is an assumption, not an enforced limit. Current clients bound plain text, but resend PDFs/photos per reply; a large workbook can exceed that budget substantially. Every additional 1M input tokens costs $0.30/$2/$2 respectively. One extra 100k-token document resent across 200 answers would add about $6 Gemini or $40 OpenAI/Claude. Retrieval, caching and token metering should precede an inclusive commercial plan. Different tokenizers/languages also produce different counts.

Google's configured model has free-tier limits for testing, but paid API Search grounding is separate: 5,000 shared monthly requests, then $14/1,000 requests. OpenAI search is $10/1,000 calls plus search-content tokens. Search summaries also add text tokens. Do not promise a whole university a free shared quota. A free API key is not unlimited free usage.

## Suggested launch offers

- **Free local library:** recording, materials, sample, viewing and PDF export. Students can connect their own key and pay the provider directly. A $2.99/month convenience plan is an optional experiment only if it supplies a clear additional benefit; do not charge merely for local access without testing demand.
- **Student Lite $4.99/month:** target 10 hours + 50 ordinary chat answers with Gemini. Same proportional budget is about $0.85 AI usage.
- **Student $9.99/month:** target 40 hours + 200 ordinary chat answers with Gemini. Use explicit AI credits as well as hour/message limits, and sell top-ups. Avoid unlimited use.
- **Premium providers:** credits/add-ons priced for their actual cost. At this scenario, OpenAI needs around $29.99/month and Claude + Gemini around $19.99/month to leave room for fees and overhead. Let students keep the affordable Gemini plan instead of forcing a premium provider.

Illustrative economics for $9.99: $3.41 modeled AI + $1.02 contingency + $0.50 storage/operations allowance + a conservative **assumed** 30% sales-channel fee ($3.00) leaves about **$2.06** before taxes, support, acquisition and fixed costs. This is contribution, not guaranteed net profit. Actual channel fees and storage use must be measured; keeping raw recordings local helps. At 1,000 similarly active paying students this implies about $2,060 contribution/month before those remaining costs.

The managed plans need server-side credentials, authenticated usage accounting, token/attachment budgets, subscription entitlements, cost caps and top-up enforcement across Android/iOS/web. Never embed a company-wide provider key in downloadable clients. Keep cloud sync optional and separately budget retained media; it is not implemented by the UI/chat change. Pilot with real anonymous usage totals, then adjust limits and regional student pricing before selling subscriptions.

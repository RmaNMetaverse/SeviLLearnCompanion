# Shared study workspace

Android uses Kotlin/Compose, iOS uses SwiftUI, and web uses TypeScript/React with a static PWA. Native control rendering differs, but information hierarchy and navigation match at the same available screen width.

`shared/design.json` owns the green palette, 600/1024 width breakpoints, 20-point card radius and 48-point main touch targets. Run `python scripts/sync-design.py` to generate native colors and web CSS. CI checks generated files. Translations remain in `shared/locales.json` for en, tr, zh-Hans, ja, ko, de, fr and es.

| Width | Navigation | Content |
|---|---|---|
| Below 600 | Four bottom destinations | Single-column cards/forms |
| 600–1023 | Compact rail | Two-column course cards |
| 1024 and above | Expanded sidebar | Three-column course cards; web materials can use two columns |

Overview has a green introductory card, three library counts and recent lectures. Courses and sessions have searchable libraries. Every session follows Notes / Chat / Transcript / Materials. Provider configuration lives in Settings and a collapsed session disclosure. Recording controls stay above the tabs so capture remains accessible. Materials contains originals, timestamped photos, shared course resources, link excerpts and public source discovery.

Use white surfaces, forest text, muted green secondary text, mint selection states, generous card spacing and rounded controls. Reserve red for failures/destructive actions. Main actions have explicit labels or accessibility names. Phone forms use sheets, chats use distinct user/tutor bubbles with a composer and optional search. No reference-page link was supplied, so this design extends the existing green palette.

Web includes manifest, install icons, safe-area spacing and the existing offline shell. iOS source includes an app icon and forces the currently supported light appearance. Android uses a corresponding wave icon and Material 3 light theme. Dark appearance, streaming responses and cloud sync are future work.

Verification belongs in PARITY.md. A successful build is not evidence of physical-device camera, background recording or keyboard behavior.

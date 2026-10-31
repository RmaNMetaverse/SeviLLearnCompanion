# SeviL Learn Companion

## Required platform parity
- Every product behavior change must be implemented in native Android (Kotlin), native iOS (Swift), and web (TypeScript), or shared server code consumed consistently by all three.
- Update docs/PARITY.md with implementation and verification status. Never mark unbuilt or untested platforms as verified.
- Maintain the shared API contract and all eight interface languages: en, tr, zh-Hans, ja, ko, de, fr, es.
- iOS source is maintained here; Apple builds run on macOS CI or a Mac, not Windows.
- Keep credentials and classroom recordings out of Git. Only commit synthetic sample data.
- Preserve original recordings and photos; distinguish lecture evidence, supporting sources, and AI explanations.
- Keep local startup simple and deployment configuration portable. Public deployment requires authentication and storage configuration; do not publish the local development server.
- Run relevant checks, commit coherent milestones, and push to the user-authorized GitHub repository without force pushes.

"""Single source of truth for all client interface catalogs. Run --check in CI."""
import json
import sys
from pathlib import Path
root = Path(__file__).resolve().parents[1]
source = root / "shared/locales.json"
catalog = json.loads(source.read_text(encoding="utf-8"))
for locale, values in catalog.items():
    if set(values) != set(catalog["en"]): raise SystemExit(f"Locale keys differ: {locale}")
destinations = [root / "apps/web/shared-locales.json", root / "apps/android/app/src/main/assets/locales.json", root / "apps/ios/SeviL/Resources/locales.json"]
for path in destinations:
    if "--check" in sys.argv:
        if not path.exists() or path.read_bytes() != source.read_bytes(): raise SystemExit(f"Run python scripts/sync-locales.py: {path}")
    else:
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(source.read_bytes())
print("All eight languages are aligned across Android, iOS, and web.")

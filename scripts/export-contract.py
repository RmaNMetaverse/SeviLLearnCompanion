import json
import sys
from pathlib import Path
root = Path(__file__).resolve().parents[1]
sys.path.insert(0,str(root / "services/api"))
from app.main import app
content = json.dumps(app.openapi(),ensure_ascii=False,indent=2,sort_keys=True) + "\n"
path = root / "shared/openapi.json"
if "--check" in sys.argv:
    if not path.exists() or path.read_text(encoding="utf-8") != content: raise SystemExit("API contract changed: run python scripts/export-contract.py")
else: path.write_text(content,encoding="utf-8")
print("Shared API contract is current.")

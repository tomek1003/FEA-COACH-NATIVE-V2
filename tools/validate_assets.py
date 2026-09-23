import json
from pathlib import Path

root = Path(__file__).resolve().parents[1] / "app" / "src" / "main" / "assets"
expected = {"fea.json": 40, "motor.json": 40, "behaviors.json": 26}
for name, count in expected.items():
    data = json.loads((root / "data" / name).read_text(encoding="utf-8"))
    assert len(data) == count, f"{name}: oczekiwano {count}, jest {len(data)}"
images = list((root / "images").glob("*.png"))
assert len(images) == 80, f"Grafiki: oczekiwano 80, jest {len(images)}"
print("Baza kompletna: 40 FEA, 40 motorycznych, 26 zachowań, 80 grafik")

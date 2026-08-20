#!/usr/bin/env python3
from pathlib import Path
import subprocess, json

ROOT = Path(__file__).resolve().parents[1]
RAW = ROOT / "app/src/main/assets/sounds/gtt_classic_dynamic_rpm"
REQUIRED = [
    "engine_start.ogg",
    "idle.ogg",
    "gear1.ogg", "gear2.ogg", "gear3.ogg", "gear4.ogg", "gear5.ogg",
    "shift_1_2.ogg", "shift_2_3.ogg", "shift_3_4.ogg", "shift_4_5.ogg",
    "downshift.ogg", "brake.ogg",
]

for name in REQUIRED:
    path = RAW / name
    assert path.is_file(), f"missing {name}"
    cp = subprocess.run([
        "ffprobe", "-v", "error", "-select_streams", "a:0",
        "-show_entries", "stream=codec_name,sample_rate,channels:format=duration",
        "-of", "json", str(path)
    ], stdout=subprocess.PIPE, stderr=subprocess.PIPE,
       universal_newlines=True, check=True)
    data = json.loads(cp.stdout)
    s = data["streams"][0]
    assert s["codec_name"] == "vorbis", f"{name}: expected Vorbis"
    assert int(s["sample_rate"]) == 44100, f"{name}: sample rate"
    assert int(s["channels"]) == 1, f"{name}: channels"
    duration = float(data["format"]["duration"])
    decoded = int(duration * 44100 * 2)
    assert decoded < 1_000_000, f"{name}: decoded SoundPool sample too large"
    print(f"OK: {name}: {duration:.3f}s, decoded {decoded} bytes")
print("OK: GTT refined runtime assets")

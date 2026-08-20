#!/usr/bin/env python3
from pathlib import Path
import subprocess, json

ROOT = Path(__file__).resolve().parents[1]
RAW = ROOT / "app/src/main/res/raw"
REQUIRED = [
    "gtt_engine_start.ogg",
    "gtt_idle.ogg",
    "gtt_gear1.ogg", "gtt_gear2.ogg", "gtt_gear3.ogg",
    "gtt_gear4.ogg", "gtt_gear5.ogg",
    "gtt_shift_1_2.ogg", "gtt_shift_2_3.ogg",
    "gtt_shift_3_4.ogg", "gtt_shift_4_5.ogg",
    "gtt_downshift.ogg", "gtt_brake.ogg",
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

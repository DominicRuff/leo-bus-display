#!/usr/bin/env python3
import json, hashlib, subprocess
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
PROFILE=ROOT/"app/src/main/assets/sounds/gtt_classic_even_more_real"
CATALOGUE=ROOT/"app/src/main/assets/sounds/profiles.json"
PACK=PROFILE/"soundpack.json"

catalogue=json.loads(CATALOGUE.read_text())
ids=[p["id"] for p in catalogue["profiles"] if p.get("enabled",True)]
assert "gtt_classic_even_more_real" in ids
pack=json.loads(PACK.read_text())
assert pack["schemaVersion"]==1
assert pack["id"]=="gtt_classic_even_more_real"
assert pack["vehicle"]["gears"]==6
assert len(pack["engine"]["gears"])==6
assert pack["effects"]["doorsOpen"]=="doors_open.ogg"
assert pack["effects"]["doorsClose"]=="doors_close.ogg"
assert pack["effects"]["horn"] is None
assert pack["transmission"].get("shift56") is None
assert pack["engine"]["idle"] is None
assert pack["effects"]["stopping"]=="idle.ogg"
assert pack["effects"]["stoppingDurationMs"]==1836

required=[]
required += [g["file"] for g in pack["engine"]["gears"]]
for section,keys in [
    (pack["effects"],["engineStart","stopping","brake","doorsOpen","doorsClose"]),
    (pack["transmission"],["shift12","shift23","shift34","shift45","downshift"])
]:
    for key in keys:
        val=section.get(key)
        if val: required.append(val)

for name in required:
    path=PROFILE/name
    assert path.is_file(), name
    cp=subprocess.run([
        "ffprobe","-v","error","-select_streams","a:0",
        "-show_entries","stream=sample_rate,channels:format=duration",
        "-of","json",str(path)
    ],stdout=subprocess.PIPE,stderr=subprocess.PIPE,universal_newlines=True,check=True)
    info=json.loads(cp.stdout)
    stream=info["streams"][0]
    assert int(stream["sample_rate"])==44100, name
    assert int(stream["channels"])==1, name
    duration=float(info["format"]["duration"])
    assert duration*44100*2 < 1_000_000, (name,"SoundPool decoded size")

# Verify package hashes.
for line in (ROOT/"docs/gtt-classic-even-more-real/SHA256SUMS_EVEN_MORE_REAL.txt").read_text().splitlines():
    expected,name=line.split("  ",1)
    if name == "soundpack.json":
        continue
    actual=hashlib.sha256((PROFILE/name).read_bytes()).hexdigest()
    assert actual==expected,name

print("OK: GTT Classic - Even More Real profile")

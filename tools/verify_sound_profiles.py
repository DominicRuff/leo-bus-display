#!/usr/bin/env python3
"""Lightweight configuration tests for asset-backed sound profiles."""

import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SOUNDS = ROOT / "app/src/main/assets/sounds"


def enabled_profiles(catalogue):
    result = []
    for profile in catalogue["profiles"]:
        if not profile.get("enabled", True):
            continue
        for field in ("id", "name", "folder"):
            assert isinstance(profile.get(field), str) and profile[field].strip(), field
        result.append(profile)
    return result


def rate(fraction, minimum, maximum):
    bounded = max(0.0, min(1.0, fraction))
    return minimum + (maximum - minimum) * bounded


catalogue = json.loads((SOUNDS / "profiles.json").read_text(encoding="utf-8"))
assert catalogue["version"] == 1
profiles = enabled_profiles(catalogue)
assert profiles
assert catalogue["defaultProfile"] in {profile["id"] for profile in profiles}

# Registry behavior: disabled entries are ignored and malformed required data fails.
fixture = {"profiles": [dict(profiles[0]), dict(profiles[0], id="disabled", enabled=False)]}
assert len(enabled_profiles(fixture)) == 1
try:
    enabled_profiles({"profiles": [{"id": "broken", "enabled": True}]})
    raise AssertionError("malformed profile accepted")
except AssertionError as error:
    assert str(error) in ("name", "folder")

loaded = {}
for profile in profiles:
    folder = ROOT / "app/src/main/assets" / profile["folder"]
    pack = json.loads((folder / "soundpack.json").read_text(encoding="utf-8"))
    assert pack["schemaVersion"] == 1
    assert pack["id"] == profile["id"]
    idle = pack["engine"].get("idle")
    gears = pack["engine"]["gears"]
    assert gears, f"{profile['id']}: at least one running sound is required"
    assert pack["effects"].get("stoppingDurationMs", 0) >= 0
    required = ([idle["file"]] if idle is not None else [])
    required += [gear["file"] for gear in gears]
    assert all((folder / name).is_file() for name in required)
    for section, keys in ((pack["effects"],
                           ("engineStart", "stopping", "brake", "horn",
                            "doorsOpen", "doorsClose")),
                          (pack["transmission"],
                           ("shift12", "shift23", "shift34", "shift45",
                            "shift56", "representativeShift", "downshift"))):
        for key in keys:
            name = section.get(key)
            assert name is None or (folder / name).is_file(), (profile["id"], key)
    loaded[profile["id"]] = pack

# Gear definitions are dynamic: the profiles intentionally use different counts.
assert catalogue["defaultProfile"] == "gtt_classic_dynamic_rpm"
assert len(loaded["gtt_classic_dynamic_rpm"]["engine"]["gears"]) == 5
assert len(loaded["prototype_diesel_test"]["engine"]["gears"]) == 1
even_more_real = loaded["gtt_classic_even_more_real"]
even_profile = next(profile for profile in profiles
                    if profile["id"] == "gtt_classic_even_more_real")
assert even_profile["enabled"] is True
assert even_profile["folder"] == "sounds/gtt_classic_even_more_real"
assert even_more_real["id"] == "gtt_classic_even_more_real"
assert even_more_real["vehicle"]["gears"] == 6
assert [gear["gear"] for gear in even_more_real["engine"]["gears"]] == list(range(1, 7))
assert even_more_real["effects"]["doorsOpen"] == "doors_open.ogg"
assert even_more_real["effects"]["doorsClose"] == "doors_close.ogg"
assert even_more_real["effects"]["horn"] is None
assert even_more_real["engine"]["idle"] is None
assert even_more_real["effects"]["stopping"] == "idle.ogg"
assert even_more_real["effects"]["stoppingDurationMs"] == 1836
assert even_more_real["transmission"]["shift56"] is None
assert loaded["gtt_classic_dynamic_rpm"]["effects"]["horn"] is None
assert loaded["gtt_classic_dynamic_rpm"]["effects"]["doorsOpen"] is None
assert loaded["gtt_classic_dynamic_rpm"]["effects"]["doorsClose"] is None
assert loaded["gtt_classic_dynamic_rpm"]["engine"]["idle"] is not None

# Validation policy: required failures invalidate; optional failures only warn.
def validation(required, optional):
    return all(required), [name for name, present in optional.items() if not present]

assert validation([True, True], {"horn": False, "doors": False})[0]
assert not validation([False, True], {"horn": True})[0]

# RPM mapping and clamping.
assert rate(0, 0.94, 1.08) == 0.94
assert rate(1, 0.94, 1.08) == 1.08
assert rate(-1, 0.94, 1.08) == 0.94
assert rate(2, 0.94, 1.08) == 1.08

# Preference fallback behavior mirrors the runtime selection order.
ids = [profile["id"] for profile in profiles]
def resolve(saved):
    if saved in ids:
        return saved
    if catalogue["defaultProfile"] in ids:
        return catalogue["defaultProfile"]
    return ids[0]

assert resolve("prototype_diesel_test") == "prototype_diesel_test"
assert resolve("unknown") == "gtt_classic_dynamic_rpm"

print("OK: registry, dynamic loading, validation, RPM mapping and preference fallback")

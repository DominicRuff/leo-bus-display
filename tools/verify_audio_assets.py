#!/usr/bin/env python3
"""Validate the synthetic SoundPool assets without external dependencies."""

import struct
import wave
from pathlib import Path

SAMPLE_RATE = 22050
MAX_DECODED_BYTES = 512 * 1024
RAW_DIR = Path(__file__).resolve().parents[1] / "app/src/main/res/raw"
REQUIRED = (
    "prototype_engine_start.wav",
    "prototype_engine_loop.wav",
    "prototype_gear_shift.wav",
    "prototype_horn.wav",
)


for name in REQUIRED:
    path = RAW_DIR / name
    assert path.is_file(), f"Missing {path}"
    with wave.open(str(path), "rb") as audio:
        assert audio.getcomptype() == "NONE", f"{name}: WAV must be PCM"
        assert audio.getnchannels() == 1, f"{name}: expected mono"
        assert audio.getframerate() == SAMPLE_RATE, f"{name}: unexpected sample rate"
        assert audio.getsampwidth() == 2, f"{name}: expected 16-bit samples"
        decoded_bytes = audio.getnframes() * audio.getnchannels() * audio.getsampwidth()
        assert 0 < decoded_bytes < MAX_DECODED_BYTES, f"{name}: too large for SoundPool"
        if name == "prototype_engine_loop.wav":
            frames = audio.readframes(audio.getnframes())
            samples = struct.unpack(f"<{len(frames) // 2}h", frames)
            assert abs(samples[-1] - samples[0]) < 2048, \
                f"{name}: excessive loop-boundary discontinuity"
        print(f"OK: {name}: {audio.getnframes()} frames, {decoded_bytes} decoded bytes")

print("OK: all required synthetic audio assets are valid SoundPool-sized PCM WAV files.")

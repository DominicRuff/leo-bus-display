#!/usr/bin/env python3
"""Generate deterministic, original synthetic audio for bus-audio development."""

import math
import struct
import wave
from pathlib import Path
from typing import List

SAMPLE_RATE = 22050
OUTPUT_DIR = Path(__file__).resolve().parents[1] / "app/src/main/res/raw"


def clamp(value: float) -> float:
    return max(-0.92, min(0.92, value))


def write_wav(name: str, samples: List[float]) -> None:
    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    path = OUTPUT_DIR / name
    frames = b"".join(struct.pack("<h", round(clamp(sample) * 32767)) for sample in samples)
    with wave.open(str(path), "wb") as output:
        output.setnchannels(1)
        output.setsampwidth(2)
        output.setframerate(SAMPLE_RATE)
        output.writeframes(frames)


def engine_start() -> List[float]:
    duration = 0.85
    count = round(SAMPLE_RATE * duration)
    samples = []
    phase = 0.0
    for index in range(count):
        t = index / SAMPLE_RATE
        frequency = 38.0 + 24.0 * (t / duration)
        phase += 2.0 * math.pi * frequency / SAMPLE_RATE
        attack = min(1.0, t / 0.08)
        release = min(1.0, (duration - t) / 0.05)
        pulse = math.sin(phase) + 0.35 * math.sin(2.0 * phase) + 0.16 * math.sin(5.0 * phase)
        samples.append(0.38 * attack * release * pulse)
    return samples


def engine_loop() -> List[float]:
    # One exact second: every integer-Hz harmonic completes an integer cycle,
    # so the implicit last-to-first loop transition is continuous.
    samples = []
    for index in range(SAMPLE_RATE):
        t = index / SAMPLE_RATE
        sample = (
            0.34 * math.sin(2.0 * math.pi * 55 * t)
            + 0.18 * math.sin(2.0 * math.pi * 110 * t)
            + 0.10 * math.sin(2.0 * math.pi * 165 * t)
            + 0.05 * math.sin(2.0 * math.pi * 275 * t)
        )
        samples.append(sample)
    return samples


def gear_shift() -> List[float]:
    duration = 0.32
    count = round(SAMPLE_RATE * duration)
    samples = []
    for index in range(count):
        t = index / SAMPLE_RATE
        envelope = math.exp(-12.0 * t)
        sample = envelope * (0.48 * math.sin(2.0 * math.pi * 95 * t)
                             + 0.22 * math.sin(2.0 * math.pi * 310 * t))
        samples.append(sample)
    return samples


def horn() -> List[float]:
    duration = 0.70
    count = round(SAMPLE_RATE * duration)
    samples = []
    for index in range(count):
        t = index / SAMPLE_RATE
        attack = min(1.0, t / 0.025)
        release = min(1.0, (duration - t) / 0.08)
        sample = attack * release * (
            0.42 * math.sin(2.0 * math.pi * 220 * t)
            + 0.30 * math.sin(2.0 * math.pi * 277 * t)
            + 0.10 * math.sin(2.0 * math.pi * 440 * t)
        )
        samples.append(sample)
    return samples


def main() -> None:
    write_wav("prototype_engine_start.wav", engine_start())
    write_wav("prototype_engine_loop.wav", engine_loop())
    write_wav("prototype_gear_shift.wav", gear_shift())
    write_wav("prototype_horn.wav", horn())
    print(f"Generated 4 synthetic WAV files in {OUTPUT_DIR}")


if __name__ == "__main__":
    main()

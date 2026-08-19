#!/usr/bin/env python3
import re
from pathlib import Path

java = Path(
    "app/src/main/java/com/leosprojects/busdisplay/DestinationLibrary.java"
).read_text(encoding="utf-8")

payloads = re.findall(r'DESTINATIONS\.put\(".*?", "([0-9A-F]+)"\);', java)
assert len(payloads) == 11, len(payloads)

for payload in payloads:
    assert len(payload) == 132
    raw = bytes.fromhex(payload)
    assert len(raw) == 66

    # Last four padded columns must remain off.
    for segment_row in range(11):
        final_byte = raw[5 * 11 + segment_row]
        assert (final_byte & 0x0F) == 0

print("OK: 11 destinations, each exact 44x11 + 4 blank padded columns.")

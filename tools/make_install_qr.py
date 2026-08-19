#!/usr/bin/env python3
import sys
import qrcode

if len(sys.argv) != 2:
    raise SystemExit("Usage: make_install_qr.py <public-github-repo-url>")

repo = sys.argv[1].rstrip("/")
if repo.endswith(".git"):
    repo = repo[:-4]

apk_url = (
    repo
    + "/releases/download/leo-bus-display-latest/"
    + "leo-bus-display-moto-g54.apk"
)

qr = qrcode.QRCode(error_correction=qrcode.constants.ERROR_CORRECT_M)
qr.add_data(apk_url)
qr.make(fit=True)
qr.make_image(fill_color="black", back_color="white").save(
    "leo-bus-display-install-qr.png"
)
print(apk_url)

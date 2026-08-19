# Leo Bus Display

Android prototype for the Motorola Moto G54 and compatible Android phones,
controlling an 11×44 Funduino / LeSun B1144-style Bluetooth LED badge.

## Prototype 0.1

- Fixed vehicle number: **4513**
- 44×11 exact bitmap output
- Orange/amber preview
- Static/fixed display — no scrolling
- Bluetooth LE service `FEE0`, write characteristic `FEE1`
- No external Android runtime libraries
- Android minSdk 23, target/compile SDK 35
- Destination presets:
  - Traversella
  - Fondo
  - Capeggio Chiara
  - Inverso
  - Colletta di Bossola
  - Castellamonte
  - Ivrea Movicentro
  - Cuorgnè
  - Rivarolo
  - Pecco
  - Alice Superiore

The destination payloads are pre-rendered exact 44×11 matrices. This means
long names do not rely on the badge's stock font and do not need to scroll.

## Build

This project is deliberately dependency-light. With Android SDK 35 and
Gradle 8.10.2 available:

```bash
gradle :app:assembleDebug
```

The APK will appear at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## GitHub QR install flow

1. Put this project in a **public** GitHub repository.
2. Push to `main`.
3. `.github/workflows/build-apk.yml` builds the APK and creates the stable
   release tag `leo-bus-display-latest`.
4. The stable download URL is:

```text
https://github.com/OWNER/REPO/releases/download/leo-bus-display-latest/leo-bus-display-moto-g54.apk
```

5. Generate the install QR code:

```bash
python3 tools/make_install_qr.py https://github.com/OWNER/REPO
```

Android will still require the normal user confirmation for installing an
APK from outside Google Play.

## Attribution

The Bluetooth badge packet format and FEE0/FEE1 interoperability were
implemented with reference to the open-source FOSSASIA Badge Magic project
(Apache License 2.0). This prototype contains a clean Java implementation
focused only on Leo's fixed 44×11 bus-destination use case.

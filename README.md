# Leo Bus Display

Android prototype for the Motorola Moto G54 and compatible Android phones,
controlling an 11×44 Funduino / LeSun B1144-style Bluetooth LED badge.

## Prototype 0.4

- Fixed vehicle number: **4513**
- 44×11 exact bitmap output
- Orange/amber preview
- Static/fixed display — no scrolling
- Bluetooth LE service `FEE0`, write characteristic `FEE1`
- No external Android runtime libraries
- Android minSdk 31, target/compile SDK 35
- Independent Bluetooth media audio for a paired speaker
- Synthetic Prototype Diesel Test engine, gear-change, and horn sounds
- Manual 0–80 km/h development speed simulator
- GTT Classic Bus layered idle and five-gear sound pack
- Selectable six-gear GTT Classic Even More Real pack with anti-surge loops
- Real door-open and door-close QA effects in the Even More Real pack
- 250 ms crossfaded manual gear transitions with shift effects
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

## Bus audio prototype

The Bluetooth LED badge and Bluetooth speaker are independent. The display
continues to work without a speaker, and the audio controls continue to work
without the FEE0/FEE1 badge.

Pair and select the bus speaker through Android's normal Bluetooth settings.
The app detects Bluetooth media outputs but does not force pairing or an A2DP
connection.

`GTT Classic Bus` is the default sound pack. It maps the manual speed control
to idle and five looped gear recordings, with modest within-gear pitch changes
and crossfaded transitions. Upshifts mix a short shift recording underneath;
downshifts crossfade without automatically playing the long downshift effect.

`Prototype Diesel Test` remains available as a synthetic fallback and retains
its original single-loop behavior. The speed slider is a manual development
simulator; GPS-driven speed and a fully automatic gearbox are planned for
future audio patches.

`GTT Classic - Even More Real` is available for manual A/B selection against
Dynamic RPM. It uses six manual steady-RPM loops with deliberately narrow
playback-rate ranges, provisional 2/14/24/35/47/61 km/h upshift boundaries,
and real door sounds. Its horn and 5→6 shift transient are intentionally
unavailable; the 5→6 engine transition still crossfades normally.

Sound packs are loaded from JSON profiles under `app/src/main/assets/sounds`.
See `docs/bus-sound-profiles.md` for the profile schema and instructions for
adding another bus without changing the playback engine.

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

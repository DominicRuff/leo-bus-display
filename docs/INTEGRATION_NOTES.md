# GTT Classic Bus refined app integration v2

This package is prepared for Leo Bus Display Audio Patch 007.

Runtime source selection
- `idle.ogg` and `gear1.ogg` ... `gear5.ogg` in the Dynamic RPM profile come from the NEW
  refined `loop_ready/` archive and are the files to loop at runtime.
- A common +2 dB gain was applied to those refined loop-ready files for the
  small Bluetooth speaker.
- `engine_start.ogg`, the four short `shift_*` one-shots,
  `downshift.ogg`, and `brake.ogg` are retained from the earlier
  app-ready GTT set because the refined archive focuses on loops and transition
  previews and does not replace those standalone runtime assets.
- GTT horn and door effects are declared unavailable until genuine recordings
  are supplied. The synthetic prototype profile retains its own horn.

Do NOT use `docs/refined_transition_previews/*.ogg` as runtime loops or simple
shift one-shots. The refined source README describes them as standalone
transition previews containing outgoing/incoming RPM overlap plus the shift
transient. They are included only for audition and QA reference.

Refined loop source facts
- 44.1 kHz, mono, Ogg Vorbis.
- Circular raised-cosine tail-to-head loop treatment.
- Source seam crossfades: idle 240 ms; gears 1–3 180 ms; gear4 220 ms; gear5 240 ms.
- Source recommends roughly 250–260 ms equal-power overlap between outgoing
  and incoming RPM loops during gear changes.

Suggested manual-speed gearbox
- IDLE: 0–2 km/h
- GEAR 1: 2–18 km/h
- GEAR 2: 18–30 km/h
- GEAR 3: 30–45 km/h
- GEAR 4: 45–60 km/h
- GEAR 5: 60–80 km/h

Suggested downshift hysteresis
- 5→4 below 55 km/h
- 4→3 below 40 km/h
- 3→2 below 26 km/h
- 2→1 below 14 km/h
- 1→IDLE below 2 km/h

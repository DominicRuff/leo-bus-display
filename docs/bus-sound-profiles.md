# Bus sound profiles

Leo Bus Display discovers enabled packs from
`app/src/main/assets/sounds/profiles.json`. Each entry points to a folder with
a `soundpack.json` and its audio assets.

## Adding another bus

1. Create `app/src/main/assets/sounds/iveco_471/`.
2. Add a `soundpack.json` using schema version 1.
3. Add one or more running/gear loops, an optional stopped/idle loop, and any
   optional effects.
4. Add an enabled entry to `sounds/profiles.json`.
5. Run `python3 tools/verify_sound_profiles.py`.

No playback-engine code change should be required. Gear count, speed bands,
RPM playback-rate ranges, loop gain, shift files, and crossfade duration come
from the profile.

## Required and optional sounds

At least one running gear loop is required. The continuous `engine.idle` loop
is optional and may be `null` when stopped operation is intentionally silent.
Engine start, the one-shot `effects.stopping`, shift transients, downshift,
brake, horn, and door sounds are optional. `stoppingDurationMs` controls when
STOPPING becomes STOPPED and must be non-negative. Missing required assets
invalidate the profile; unavailable optional sounds never receive a silent
substitute.

`rateMin` and `rateMax` map the clamped 0–1 RPM position to SoundPool playback
rate. `gain` scales that loop beneath the user's engine-volume control.
`crossfadeMs` controls the equal-power overlap between engine loops.

## Available profiles

- `gtt_classic_dynamic_rpm`: refined GTT loops, five gears, dynamic RPM.
- `gtt_classic_even_more_real`: six manual steady-RPM loops with narrow
  anti-surge rate ranges, a 280 ms crossfade, and real door-open/close effects.
- `prototype_diesel_test`: original synthetic single-loop fallback.

### Even More Real QA profile

Select `GTT Classic - Even More Real` manually in the Sound Pack spinner to
A/B it against Dynamic RPM without resetting simulated speed. Its provisional
upshift boundaries are 2, 14, 24, 35, 47, and 61 km/h for gears 1 through 6;
the corresponding downshift thresholds are 2, 11, 20, 31, 42, and 55 km/h.
The narrow per-loop rate ranges reduce artificial pitch surging while retaining
dynamic RPM response.

This profile supplies real door-open and door-close effects. Horn and the 5→6
shift transient are intentionally `null`: the engine performs its normal
equal-power crossfade into gear 6 without layering a fabricated effect. The
additional transition-specific downshift recordings are retained beside the
runtime assets for future schema support; the current schema uses
`downshift_6_5.ogg` as its representative downshift.

Its historical `idle.ogg` is a 1836 ms final stopping/pneumatic recording, not
a stationary loop. The profile therefore declares `engine.idle` as `null` and
uses that unchanged file once as `effects.stopping`. STOPPED remains logically
engine-on but silent until a suitable continuous stationary recording exists.

Distinct source audio for GTT Original, Boosted, and a separate Seamless QA
variant is not currently present. Those profiles are intentionally omitted
until their real files are supplied.

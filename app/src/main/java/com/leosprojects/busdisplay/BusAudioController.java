package com.leosprojects.busdisplay;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.media.AudioAttributes;
import android.media.AudioDeviceCallback;
import android.media.AudioDeviceInfo;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.SoundPool;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;

import com.leosprojects.busdisplay.sound.SoundPack;
import com.leosprojects.busdisplay.sound.SoundPackLoader;
import com.leosprojects.busdisplay.sound.SoundPackProfile;
import com.leosprojects.busdisplay.sound.SoundPackRegistry;
import com.leosprojects.busdisplay.sound.SoundPackValidator;
import com.leosprojects.busdisplay.sound.SoundProfilePreferences;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class BusAudioController {
    public interface Listener {
        void onSpeakerStatusChanged(String status);
        void onEngineStateChanged(boolean enabled, String status);
        void onEngineBandChanged(String band);
    }

    private static final String TAG = "BusAudioController";
    private static final long RATE_UPDATE_MS = 50;
    private static final long CROSSFADE_UPDATE_MS = 25;

    private final Context context;
    private final AudioManager audioManager;
    private final Listener listener;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final SoundPool soundPool;
    private final AudioFocusRequest focusRequest;
    private final Runnable startEngineLoop;
    private final Runnable crossfadeStep;
    private final Runnable finishStopping;
    private final EngineMotionState motionState = new EngineMotionState();
    private final SoundProfilePreferences profilePreferences;
    private final List<SoundPackProfile> availableProfiles = new ArrayList<>();
    private final Map<String, SoundPack> packsById = new LinkedHashMap<>();
    private final Map<String, SoundPackValidator.Result> validationById = new LinkedHashMap<>();
    private final Map<String, Integer> soundIdsByAsset = new HashMap<>();
    private final Map<Integer, Boolean> loadedSoundIds = new HashMap<>();

    private SoundPackProfile selectedProfile;
    private SoundPack soundPack;
    private int engineStartStream;
    private int engineLoopStream;
    private int fadingEngineStream;
    private int stoppingStream;
    private int currentSpeedKmh;
    private int currentBand;
    private int playingBand;
    private long crossfadeStartedAtMs;
    private boolean engineRequested;
    private boolean resumeAfterFocusGain;
    private boolean focusOwned;
    private boolean ducked;
    private boolean released;
    private boolean soundLoadFailed;
    private float engineVolume = 1f;
    private float effectsVolume = 1f;
    private float currentRate = 1f;
    private float targetRate = 1f;
    private float activeLoopGain = 1f;
    private float fadingLoopGain = 1f;

    public BusAudioController(Context context, Listener listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;
        profilePreferences = new SoundProfilePreferences(this.context);
        audioManager = (AudioManager) this.context.getSystemService(Context.AUDIO_SERVICE);

        AudioAttributes attributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build();
        soundPool = new SoundPool.Builder()
                .setMaxStreams(8)
                .setAudioAttributes(attributes)
                .build();
        startEngineLoop = this::startEngineLoopNow;
        crossfadeStep = this::runCrossfadeStep;
        finishStopping = this::finishStoppingNow;
        soundPool.setOnLoadCompleteListener((pool, sampleId, status) -> {
            if (!soundIdsByAsset.containsValue(sampleId)) return;
            loadedSoundIds.put(sampleId, status == 0);
            if (status != 0) {
                engineRequested = false;
                stopEngineStreams();
                abandonAudioFocus();
                listener.onEngineStateChanged(false, "Could not load a bus sound.");
            } else if (engineRequested) {
                if (engineLoopStream != 0 && soundPack != null && !isSingleLoop()
                        && playingBand != currentBand && isLoaded(currentLoopAsset())) {
                    transitionToBand(playingBand, currentBand);
                } else {
                    tryStartEngine();
                }
            }
        });

        focusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(attributes)
                .setOnAudioFocusChangeListener(this::onAudioFocusChange, handler)
                .build();

        if (audioManager != null) {
            audioManager.registerAudioDeviceCallback(audioDeviceCallback, handler);
        }
        initializeProfiles();
        refreshSpeakerStatus();
    }

    private void initializeProfiles() {
        SoundPackRegistry registry = new SoundPackRegistry(context);
        SoundPackLoader loader = new SoundPackLoader(context);
        SoundPackValidator validator = new SoundPackValidator(context);
        List<SoundPackProfile> configured = registry.loadProfiles();
        for (SoundPackProfile profile : configured) {
            try {
                SoundPack pack = loader.load(profile);
                SoundPackValidator.Result validation = validator.validate(pack);
                validationById.put(profile.id, validation);
                if (validation.valid) {
                    availableProfiles.add(profile);
                    packsById.put(profile.id, pack);
                } else {
                    Log.e(TAG, "Ignoring invalid sound profile " + profile.id);
                }
            } catch (Exception error) {
                Log.e(TAG, "Could not load sound profile " + profile.id, error);
            }
        }

        SoundPackProfile selected = findAvailableProfile(profilePreferences.getSelectedProfileId());
        if (selected == null) {
            selected = findAvailableProfile(registry.getDefaultProfileId());
            Log.w(TAG, "Saved sound profile unavailable; using configured default");
        }
        if (selected == null && !availableProfiles.isEmpty()) {
            selected = availableProfiles.get(0);
            Log.w(TAG, "Default sound profile unavailable; using first valid profile");
        }
        if (selected != null) setSoundPackProfile(selected);
        else Log.e(TAG, "No valid sound profiles are available");
    }

    private SoundPackProfile findAvailableProfile(String id) {
        if (id == null) return null;
        for (SoundPackProfile profile : availableProfiles) if (id.equals(profile.id)) return profile;
        return null;
    }

    public List<SoundPackProfile> getAvailableProfiles() {
        return Collections.unmodifiableList(availableProfiles);
    }

    public SoundPackProfile getSelectedProfile() { return selectedProfile; }

    public String getSelectedProfileQaInfo() {
        if (selectedProfile == null || soundPack == null) return "No valid sound profile";
        SoundPackValidator.Result result = validationById.get(selectedProfile.id);
        return "Profile: " + selectedProfile.name + "\nID: " + selectedProfile.id
                + "\nEngine: " + soundPack.declaredGears + "-speed " + soundPack.transmission
                + "\nDynamic RPM: " + (soundPack.gears.isEmpty() ? "Unavailable" : "Enabled")
                + "\nCrossfade: " + soundPack.crossfadeMs + " ms\n\nSounds:\n"
                + (result == null ? "Validation unavailable" : result.summary());
    }

    public boolean setSoundPackProfile(SoundPackProfile profile) {
        if (released || profile == null) return false;
        SoundPack pack = packsById.get(profile.id);
        if (pack == null) return false;
        if (selectedProfile != null && selectedProfile.id.equals(profile.id)) return true;
        boolean restartEngine = engineRequested;
        stopEngine();
        for (int soundId : soundIdsByAsset.values()) soundPool.unload(soundId);
        soundIdsByAsset.clear();
        loadedSoundIds.clear();
        soundPack = pack;
        selectedProfile = profile;
        motionState.reset(currentSpeedKmh);
        soundLoadFailed = false;
        for (String asset : pack.declaredAssets()) load(asset);
        currentBand = initialBandForSpeed(currentSpeedKmh);
        playingBand = currentBand;
        updateTargetRate();
        listener.onEngineBandChanged(bandName());
        profilePreferences.setSelectedProfileId(profile.id);
        if (restartEngine) setEngineEnabled(true);
        return true;
    }

    public void setEngineEnabled(boolean enabled) {
        if (released) return;
        if (!enabled) {
            stopEngine();
            return;
        }
        engineRequested = true;
        tryStartEngine();
    }

    public boolean isEngineEnabled() { return engineRequested; }

    public void setSpeedKmh(int speedKmh) {
        currentSpeedKmh = Math.max(0, Math.min(80, speedKmh));
        if (isStopAware() && !engineRequested) {
            motionState.reset(currentSpeedKmh);
        } else if (isStopAware()) {
            EngineMotionState.Action action = motionState.onSpeedKmh(currentSpeedKmh);
            if (action == EngineMotionState.Action.START_STOPPING) {
                currentBand = 0;
                updateTargetRate();
                beginStopping();
                return;
            }
            if (action == EngineMotionState.Action.START_MOVING) {
                currentBand = initialBandForSpeed(currentSpeedKmh);
                updateTargetRate();
                listener.onEngineBandChanged(bandName());
                cancelStoppingAndStartMoving();
                return;
            }
            if (motionState.state() != EngineMotionState.State.MOVING) return;
            if (currentSpeedKmh < 3) {
                updateTargetRate();
                if (engineLoopStream != 0) startRateSmoother();
                return;
            }
        }
        if (soundPack != null && !isSingleLoop()) {
            int oldBand = currentBand;
            currentBand = layeredBandWithHysteresis(currentSpeedKmh, currentBand);
            updateTargetRate();
            if (currentBand != oldBand) {
                listener.onEngineBandChanged(bandName());
                if (engineLoopStream != 0) transitionToBand(playingBand, currentBand);
            } else if (engineLoopStream != 0) {
                startRateSmoother();
            }
        } else {
            updateTargetRate();
            if (engineLoopStream != 0) startRateSmoother();
        }
    }

    public void setEngineVolume(int percent) {
        engineVolume = boundedVolume(percent);
        applyEngineVolume();
    }

    public void setEffectsVolume(int percent) { effectsVolume = boundedVolume(percent); }
    public void playHorn() { playEffect(soundPack == null ? null : soundPack.hornAsset); }
    public void playBrake() { playEffect(soundPack == null ? null : soundPack.brakeAsset); }
    public void openDoors() { playEffect(soundPack == null ? null : soundPack.doorsOpenAsset); }
    public void closeDoors() { playEffect(soundPack == null ? null : soundPack.doorsCloseAsset); }
    public void playGearChange() {
        if (soundPack == null) return;
        String effect = soundPack.representativeShiftAsset;
        if (effect == null && !soundPack.shiftAssets.isEmpty()) {
            effect = soundPack.shiftAssets.values().iterator().next();
        }
        playEffect(effect);
    }

    public void refreshSpeakerStatus() {
        if (!released) listener.onSpeakerStatusChanged(findBluetoothSpeakerStatus());
    }

    public void release() {
        if (released) return;
        stopEngine();
        released = true;
        handler.removeCallbacksAndMessages(null);
        if (audioManager != null) audioManager.unregisterAudioDeviceCallback(audioDeviceCallback);
        soundPool.setOnLoadCompleteListener(null);
        soundPool.release();
    }

    private void load(String assetPath) {
        try (AssetFileDescriptor descriptor = context.getAssets().openFd(assetPath)) {
            int soundId = soundPool.load(descriptor, 1);
            if (soundId == 0) {
                soundLoadFailed = true;
                Log.e(TAG, "SoundPool rejected sound asset " + assetPath);
                return;
            }
            soundIdsByAsset.put(assetPath, soundId);
            loadedSoundIds.put(soundId, false);
        } catch (Exception error) {
            soundLoadFailed = true;
            Log.e(TAG, "Could not load sound asset " + assetPath, error);
        }
    }

    private boolean isLoaded(String assetPath) {
        Integer soundId = soundIdsByAsset.get(assetPath);
        return soundId != null && Boolean.TRUE.equals(loadedSoundIds.get(soundId));
    }

    private void tryStartEngine() {
        if (soundLoadFailed) {
            engineRequested = false;
            listener.onEngineStateChanged(false, "The selected sound profile could not be loaded.");
            return;
        }
        String loopAsset = currentLoopAsset();
        boolean silentStopped = isStopAware()
                && motionState.state() != EngineMotionState.State.MOVING
                && loopAsset == null;
        if (released || !engineRequested || soundPack == null
                || (!silentStopped && !isLoaded(loopAsset))
                || engineStartStream != 0 || engineLoopStream != 0) return;

        if (soundPack.engineStartAsset != null && !isLoaded(soundPack.engineStartAsset)) return;

        if (!requestAudioFocus()) {
            engineRequested = false;
            listener.onEngineStateChanged(false, "Audio focus was not granted.");
            return;
        }

        currentRate = targetRate;
        if (soundPack.engineStartAsset == null) {
            listener.onEngineStateChanged(true, "Engine running.");
            startEngineLoopNow();
            return;
        }
        int soundId = soundIdsByAsset.get(soundPack.engineStartAsset);
        engineStartStream = soundPool.play(soundId, effectiveEngineVolume(),
                effectiveEngineVolume(), 10, 0, 1f);
        if (engineStartStream == 0) {
            stopEngine();
            listener.onEngineStateChanged(false, "Could not start engine audio.");
            return;
        }
        listener.onEngineStateChanged(true, "Engine running.");
        handler.removeCallbacks(startEngineLoop);
        handler.postDelayed(startEngineLoop, soundPack.engineStartDurationMs);
    }

    private void startEngineLoopNow() {
        engineStartStream = 0;
        if (released || !engineRequested || soundPack == null || engineLoopStream != 0) return;
        if (isStopAware() && motionState.state() == EngineMotionState.State.STOPPING) return;
        SoundPack.Gear loop = currentLoop();
        if (loop == null) {
            listener.onEngineBandChanged("STOPPED");
            return;
        }
        int soundId = soundIdsByAsset.get(loop.assetPath);
        activeLoopGain = loop.gain;
        float volume = effectiveEngineVolume() * activeLoopGain;
        engineLoopStream = soundPool.play(soundId, volume, volume, 10, -1, currentRate);
        if (engineLoopStream == 0) {
            stopEngine();
            listener.onEngineStateChanged(false, "Could not start the engine loop.");
        } else {
            playingBand = currentBand;
            startRateSmoother();
        }
    }

    private void transitionToBand(int oldBand, int newBand) {
        finishActiveCrossfade();
        SoundPack.Gear incoming = loopForBand(newBand);
        if (incoming == null || !isLoaded(incoming.assetPath)) return;
        int oldStream = engineLoopStream;
        int newStream = soundPool.play(soundIdsByAsset.get(incoming.assetPath), 0f, 0f,
                10, -1, targetRate);
        if (newStream == 0) return;

        fadingEngineStream = oldStream;
        fadingLoopGain = activeLoopGain;
        engineLoopStream = newStream;
        activeLoopGain = incoming.gain;
        playingBand = newBand;
        currentRate = targetRate;
        if (newBand > oldBand) playEffect(soundPack.shiftAsset(oldBand, newBand));
        crossfadeStartedAtMs = SystemClock.uptimeMillis();
        handler.post(crossfadeStep);
    }

    private void runCrossfadeStep() {
        handler.removeCallbacks(crossfadeStep);
        if (released || fadingEngineStream == 0 || engineLoopStream == 0) return;
        float progress = Math.min(1f,
                (SystemClock.uptimeMillis() - crossfadeStartedAtMs)
                        / (float) Math.max(1, soundPack.crossfadeMs));
        float angle = progress * (float) Math.PI / 2f;
        float volume = effectiveEngineVolume();
        float oldVolume = volume * fadingLoopGain * (float) Math.cos(angle);
        float newVolume = volume * activeLoopGain * (float) Math.sin(angle);
        soundPool.setVolume(fadingEngineStream, oldVolume, oldVolume);
        soundPool.setVolume(engineLoopStream, newVolume, newVolume);
        if (progress >= 1f) {
            soundPool.stop(fadingEngineStream);
            fadingEngineStream = 0;
            startRateSmoother();
        } else {
            handler.postDelayed(crossfadeStep, CROSSFADE_UPDATE_MS);
        }
    }

    private void finishActiveCrossfade() {
        handler.removeCallbacks(crossfadeStep);
        if (fadingEngineStream != 0) soundPool.stop(fadingEngineStream);
        fadingEngineStream = 0;
        if (engineLoopStream != 0) {
            float volume = effectiveEngineVolume();
            soundPool.setVolume(engineLoopStream, volume * activeLoopGain,
                    volume * activeLoopGain);
        }
    }

    private void beginStopping() {
        handler.removeCallbacks(finishStopping);
        handler.removeCallbacks(startEngineLoop);
        finishActiveCrossfade();
        handler.removeCallbacks(rateSmoother);
        if (engineStartStream != 0) {
            soundPool.setVolume(engineStartStream, 0f, 0f);
            soundPool.stop(engineStartStream);
        }
        engineStartStream = 0;
        if (engineLoopStream != 0) {
            soundPool.setVolume(engineLoopStream, 0f, 0f);
            soundPool.stop(engineLoopStream);
        }
        engineLoopStream = 0;
        if (stoppingStream != 0) soundPool.stop(stoppingStream);
        stoppingStream = 0;
        listener.onEngineBandChanged("STOPPING");
        if (engineRequested && isLoaded(soundPack.stoppingAsset)) {
            float volume = effectsVolume;
            stoppingStream = soundPool.play(soundIdsByAsset.get(soundPack.stoppingAsset),
                    volume, volume, 10, 0, 1f);
        }
        handler.postDelayed(finishStopping, soundPack.stoppingDurationMs);
    }

    private void finishStoppingNow() {
        handler.removeCallbacks(finishStopping);
        if (stoppingStream != 0) {
            soundPool.setVolume(stoppingStream, 0f, 0f);
            soundPool.stop(stoppingStream);
        }
        stoppingStream = 0;
        motionState.completeStopping();
        if (!engineRequested || released) return;
        listener.onEngineBandChanged("STOPPED");
        if (soundPack.idle != null && isLoaded(soundPack.idle.assetPath)) {
            currentBand = 0;
            updateTargetRate();
            startEngineLoopNow();
        }
    }

    private void cancelStoppingAndStartMoving() {
        handler.removeCallbacks(finishStopping);
        if (stoppingStream != 0) {
            soundPool.setVolume(stoppingStream, 0f, 0f);
            soundPool.stop(stoppingStream);
        }
        stoppingStream = 0;
        if (engineRequested && engineStartStream == 0 && engineLoopStream == 0) {
            startEngineLoopNow();
        }
    }

    private void stopEngineStreams() {
        handler.removeCallbacks(startEngineLoop);
        handler.removeCallbacks(rateSmoother);
        handler.removeCallbacks(crossfadeStep);
        handler.removeCallbacks(finishStopping);
        if (engineStartStream != 0) soundPool.stop(engineStartStream);
        if (engineLoopStream != 0) soundPool.stop(engineLoopStream);
        if (fadingEngineStream != 0) soundPool.stop(fadingEngineStream);
        if (stoppingStream != 0) soundPool.stop(stoppingStream);
        engineStartStream = 0;
        engineLoopStream = 0;
        fadingEngineStream = 0;
        stoppingStream = 0;
        if (motionState.state() == EngineMotionState.State.STOPPING) {
            motionState.completeStopping();
        }
    }

    private void stopEngine() {
        engineRequested = false;
        resumeAfterFocusGain = false;
        stopEngineStreams();
        motionState.reset(currentSpeedKmh);
        abandonAudioFocus();
        if (!released) listener.onEngineStateChanged(false, "Engine stopped.");
    }

    private void playEffect(String assetPath) {
        if (released || assetPath == null || !isLoaded(assetPath)) return;
        soundPool.play(soundIdsByAsset.get(assetPath), effectsVolume, effectsVolume, 1, 0, 1f);
    }

    private boolean requestAudioFocus() {
        if (focusOwned) return true;
        if (audioManager == null) return false;
        focusOwned = audioManager.requestAudioFocus(focusRequest)
                == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
        return focusOwned;
    }

    private void abandonAudioFocus() {
        if (focusOwned && audioManager != null) audioManager.abandonAudioFocusRequest(focusRequest);
        focusOwned = false;
        ducked = false;
    }

    private void onAudioFocusChange(int change) {
        if (released) return;
        if (change == AudioManager.AUDIOFOCUS_LOSS) {
            stopEngine();
        } else if (change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
            resumeAfterFocusGain = engineRequested;
            stopEngineStreams();
            listener.onEngineStateChanged(engineRequested, "Engine paused for other audio.");
        } else if (change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK) {
            ducked = true;
            applyEngineVolume();
        } else if (change == AudioManager.AUDIOFOCUS_GAIN) {
            ducked = false;
            applyEngineVolume();
            if (resumeAfterFocusGain && engineRequested) {
                resumeAfterFocusGain = false;
                tryStartEngine();
            }
        }
    }

    private void updateTargetRate() {
        SoundPack.Gear loop = currentLoop();
        if (loop == null) {
            targetRate = 1f;
            return;
        }
        float fraction = (currentSpeedKmh - loop.speedMin)
                / (float) Math.max(1, loop.speedMax - loop.speedMin);
        targetRate = loop.playbackRate(fraction);
    }

    private int initialBandForSpeed(int speed) {
        if (soundPack == null || isSingleLoop()) return 0;
        int band = 0;
        for (SoundPack.Gear gear : soundPack.gears) {
            if (speed >= gear.speedMin) band = gear.number;
        }
        return band;
    }

    private int layeredBandWithHysteresis(int speed, int band) {
        if (soundPack == null || isSingleLoop()) return 0;
        boolean changed;
        do {
            changed = false;
            SoundPack.Gear next = soundPack.getGear(band + 1);
            if (next != null && speed >= next.speedMin) {
                band++;
                changed = true;
            }
        } while (changed);
        while (band > 0) {
            SoundPack.Gear current = soundPack.getGear(band);
            if (current == null || speed >= current.downshiftBelow) break;
            band--;
        }
        return band;
    }

    public void setGear(int gear) {
        if (soundPack == null) return;
        int bounded = Math.max(0, Math.min(soundPack.gears.size(), gear));
        int old = currentBand;
        currentBand = bounded;
        updateTargetRate();
        if (old != currentBand) {
            listener.onEngineBandChanged(bandName());
            if (engineLoopStream != 0) transitionToBand(playingBand, currentBand);
        }
    }

    public void setRpm(float rpmFraction) {
        SoundPack.Gear loop = currentLoop();
        if (loop == null) return;
        targetRate = loop.playbackRate(rpmFraction);
        if (engineLoopStream != 0) startRateSmoother();
    }

    public void setThrottle(float throttleFraction) {
        // Reserved vehicle state input; profiles currently render throttle through RPM.
    }

    private boolean isSingleLoop() {
        return soundPack != null && soundPack.gears.size() == 1
                && soundPack.idle != null
                && soundPack.idle.assetPath.equals(soundPack.gears.get(0).assetPath);
    }

    private SoundPack.Gear loopForBand(int band) {
        return band <= 0 ? soundPack.idle : soundPack.getGear(band);
    }

    private SoundPack.Gear currentLoop() {
        if (soundPack == null) return null;
        if (!isStopAware()) return loopForBand(currentBand);
        int loopBand = motionState.continuousLoopBand(currentBand, soundPack.idle != null);
        return loopBand < 0 ? null : loopForBand(loopBand);
    }

    private String currentLoopAsset() {
        SoundPack.Gear loop = currentLoop();
        return loop == null ? null : loop.assetPath;
    }

    private String bandName() {
        if (isStopAware() && motionState.state() != EngineMotionState.State.MOVING) {
            return motionState.state().name();
        }
        if (soundPack == null || isSingleLoop()) return "ENGINE LOOP";
        return currentBand == 0 ? "IDLE" : "GEAR " + currentBand;
    }

    private boolean isStopAware() {
        return soundPack != null && (soundPack.stoppingAsset != null || soundPack.idle == null);
    }

    private void startRateSmoother() {
        handler.removeCallbacks(rateSmoother);
        handler.post(rateSmoother);
    }

    private final Runnable rateSmoother = new Runnable() {
        @Override
        public void run() {
            if (released || engineLoopStream == 0) return;
            float difference = targetRate - currentRate;
            if (Math.abs(difference) < 0.005f) currentRate = targetRate;
            else currentRate += difference * 0.20f;
            currentRate = Math.max(0.5f, Math.min(2.0f, currentRate));
            soundPool.setRate(engineLoopStream, currentRate);
            if (currentRate != targetRate) handler.postDelayed(this, RATE_UPDATE_MS);
        }
    };

    private void applyEngineVolume() {
        if (fadingEngineStream != 0) {
            runCrossfadeStep();
            return;
        }
        float volume = effectiveEngineVolume();
        if (engineStartStream != 0) soundPool.setVolume(engineStartStream, volume, volume);
        if (engineLoopStream != 0) soundPool.setVolume(engineLoopStream,
                volume * activeLoopGain, volume * activeLoopGain);
    }

    private float effectiveEngineVolume() { return engineVolume * (ducked ? 0.25f : 1f); }
    private float boundedVolume(int percent) {
        return Math.max(0, Math.min(100, percent)) / 100f;
    }

    private String findBluetoothSpeakerStatus() {
        if (audioManager != null) {
            for (AudioDeviceInfo device : audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)) {
                int type = device.getType();
                if (type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP
                        || type == AudioDeviceInfo.TYPE_BLE_SPEAKER
                        || type == AudioDeviceInfo.TYPE_BLE_HEADSET) {
                    CharSequence productName = device.getProductName();
                    String name = productName == null ? "Bluetooth audio device"
                            : productName.toString().trim();
                    if (name.isEmpty()) name = "Bluetooth audio device";
                    return "Bluetooth speaker: " + name;
                }
            }
        }
        return "No Bluetooth media speaker detected";
    }

    private final AudioDeviceCallback audioDeviceCallback = new AudioDeviceCallback() {
        @Override
        public void onAudioDevicesAdded(AudioDeviceInfo[] addedDevices) { refreshSpeakerStatus(); }
        @Override
        public void onAudioDevicesRemoved(AudioDeviceInfo[] removedDevices) { refreshSpeakerStatus(); }
    };
}

package com.leosprojects.busdisplay;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioDeviceCallback;
import android.media.AudioDeviceInfo;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.SoundPool;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import java.util.HashMap;
import java.util.Map;

public final class BusAudioController {
    public interface Listener {
        void onSpeakerStatusChanged(String status);
        void onEngineStateChanged(boolean enabled, String status);
        void onEngineBandChanged(String band);
    }

    private static final float PROTOTYPE_MIN_RATE = 0.70f;
    private static final float PROTOTYPE_MAX_RATE = 1.55f;
    private static final float GTT_MIN_RATE = 0.94f;
    private static final float GTT_MAX_RATE = 1.08f;
    private static final long RATE_UPDATE_MS = 50;
    private static final long CROSSFADE_DURATION_MS = 250;
    private static final long CROSSFADE_UPDATE_MS = 25;

    private final Context context;
    private final AudioManager audioManager;
    private final Listener listener;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final SoundPool soundPool;
    private final AudioFocusRequest focusRequest;
    private final Runnable startEngineLoop;
    private final Runnable crossfadeStep;
    private final Map<Integer, Integer> soundIdsByResource = new HashMap<>();
    private final Map<Integer, Boolean> loadedSoundIds = new HashMap<>();

    private BusSoundPack soundPack;
    private int engineStartStream;
    private int engineLoopStream;
    private int fadingEngineStream;
    private int currentSpeedKmh;
    private int currentBand;
    private int playingBand;
    private long crossfadeStartedAtMs;
    private boolean engineRequested;
    private boolean resumeAfterFocusGain;
    private boolean focusOwned;
    private boolean ducked;
    private boolean released;
    private float engineVolume = 1f;
    private float effectsVolume = 1f;
    private float currentRate = PROTOTYPE_MIN_RATE;
    private float targetRate = PROTOTYPE_MIN_RATE;

    public BusAudioController(Context context, Listener listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;
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
        soundPool.setOnLoadCompleteListener((pool, sampleId, status) -> {
            loadedSoundIds.put(sampleId, status == 0);
            if (status != 0) {
                engineRequested = false;
                stopEngineStreams();
                abandonAudioFocus();
                listener.onEngineStateChanged(false, "Could not load a bus sound.");
            } else if (engineRequested) {
                if (engineLoopStream != 0 && soundPack != null && soundPack.isLayered()
                        && playingBand != currentBand && isLoaded(currentLoopResource())) {
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
        setSoundPack(BusSoundPack.GTT_CLASSIC_BUS);
        refreshSpeakerStatus();
    }

    public void setSoundPack(BusSoundPack pack) {
        if (released || pack == null || pack == soundPack) return;
        stopEngine();
        for (int soundId : soundIdsByResource.values()) soundPool.unload(soundId);
        soundIdsByResource.clear();
        loadedSoundIds.clear();
        soundPack = pack;
        for (int resource : pack.allResources()) load(resource);
        currentBand = pack.isLayered() ? initialBandForSpeed(currentSpeedKmh) : 0;
        playingBand = currentBand;
        updateTargetRate();
        listener.onEngineBandChanged(bandName());
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
        if (soundPack != null && soundPack.isLayered()) {
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
    public void playHorn() { playEffect(soundPack == null ? 0 : soundPack.hornResource()); }
    public void playGearChange() {
        playEffect(soundPack == null ? 0 : soundPack.gearChangeResource());
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

    private void load(int resource) {
        int soundId = soundPool.load(context, resource, 1);
        soundIdsByResource.put(resource, soundId);
        loadedSoundIds.put(soundId, false);
    }

    private boolean isLoaded(int resource) {
        Integer soundId = soundIdsByResource.get(resource);
        return soundId != null && Boolean.TRUE.equals(loadedSoundIds.get(soundId));
    }

    private void tryStartEngine() {
        int loopResource = currentLoopResource();
        if (released || !engineRequested || soundPack == null
                || !isLoaded(soundPack.engineStartResource()) || !isLoaded(loopResource)
                || engineStartStream != 0 || engineLoopStream != 0) return;

        if (!requestAudioFocus()) {
            engineRequested = false;
            listener.onEngineStateChanged(false, "Audio focus was not granted.");
            return;
        }

        currentRate = targetRate;
        int soundId = soundIdsByResource.get(soundPack.engineStartResource());
        engineStartStream = soundPool.play(soundId, effectiveEngineVolume(),
                effectiveEngineVolume(), 10, 0, 1f);
        if (engineStartStream == 0) {
            stopEngine();
            listener.onEngineStateChanged(false, "Could not start engine audio.");
            return;
        }
        listener.onEngineStateChanged(true, "Engine running.");
        handler.removeCallbacks(startEngineLoop);
        handler.postDelayed(startEngineLoop, soundPack.engineStartDurationMs());
    }

    private void startEngineLoopNow() {
        engineStartStream = 0;
        if (released || !engineRequested || soundPack == null || engineLoopStream != 0) return;
        int soundId = soundIdsByResource.get(currentLoopResource());
        engineLoopStream = soundPool.play(soundId, effectiveEngineVolume(),
                effectiveEngineVolume(), 10, -1, currentRate);
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
        int resource = soundPack.engineLoopResource(newBand);
        if (!isLoaded(resource)) return;
        int oldStream = engineLoopStream;
        int newStream = soundPool.play(soundIdsByResource.get(resource), 0f, 0f,
                10, -1, targetRate);
        if (newStream == 0) return;

        fadingEngineStream = oldStream;
        engineLoopStream = newStream;
        playingBand = newBand;
        currentRate = targetRate;
        if (newBand > oldBand) playEffect(soundPack.upshiftResourceForBand(newBand));
        crossfadeStartedAtMs = SystemClock.uptimeMillis();
        handler.post(crossfadeStep);
    }

    private void runCrossfadeStep() {
        handler.removeCallbacks(crossfadeStep);
        if (released || fadingEngineStream == 0 || engineLoopStream == 0) return;
        float progress = Math.min(1f,
                (SystemClock.uptimeMillis() - crossfadeStartedAtMs) / (float) CROSSFADE_DURATION_MS);
        float angle = progress * (float) Math.PI / 2f;
        float volume = effectiveEngineVolume();
        soundPool.setVolume(fadingEngineStream, volume * (float) Math.cos(angle),
                volume * (float) Math.cos(angle));
        soundPool.setVolume(engineLoopStream, volume * (float) Math.sin(angle),
                volume * (float) Math.sin(angle));
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
            soundPool.setVolume(engineLoopStream, volume, volume);
        }
    }

    private void stopEngineStreams() {
        handler.removeCallbacks(startEngineLoop);
        handler.removeCallbacks(rateSmoother);
        handler.removeCallbacks(crossfadeStep);
        if (engineStartStream != 0) soundPool.stop(engineStartStream);
        if (engineLoopStream != 0) soundPool.stop(engineLoopStream);
        if (fadingEngineStream != 0) soundPool.stop(fadingEngineStream);
        engineStartStream = 0;
        engineLoopStream = 0;
        fadingEngineStream = 0;
    }

    private void stopEngine() {
        engineRequested = false;
        resumeAfterFocusGain = false;
        stopEngineStreams();
        abandonAudioFocus();
        if (!released) listener.onEngineStateChanged(false, "Engine stopped.");
    }

    private void playEffect(int resource) {
        if (released || resource == 0 || !isLoaded(resource)) return;
        soundPool.play(soundIdsByResource.get(resource), effectsVolume, effectsVolume, 1, 0, 1f);
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
        if (soundPack != null && soundPack.isLayered()) {
            targetRate = layeredRate(currentSpeedKmh, currentBand);
        } else {
            targetRate = PROTOTYPE_MIN_RATE
                    + (PROTOTYPE_MAX_RATE - PROTOTYPE_MIN_RATE) * currentSpeedKmh / 80f;
        }
    }

    private int initialBandForSpeed(int speed) {
        if (speed >= 60) return 5;
        if (speed >= 45) return 4;
        if (speed >= 30) return 3;
        if (speed >= 18) return 2;
        if (speed >= 2) return 1;
        return 0;
    }

    private int layeredBandWithHysteresis(int speed, int band) {
        int[] upshiftAt = {2, 18, 30, 45, 60};
        int[] downshiftBelow = {0, 2, 14, 26, 40, 55};
        while (band < 5 && speed >= upshiftAt[band]) band++;
        while (band > 0 && speed < downshiftBelow[band]) band--;
        return band;
    }

    private float layeredRate(int speed, int band) {
        int[] low = {0, 2, 18, 30, 45, 60};
        int[] high = {2, 18, 30, 45, 60, 80};
        float position = (speed - low[band]) / (float) Math.max(1, high[band] - low[band]);
        position = Math.max(0f, Math.min(1f, position));
        return GTT_MIN_RATE + (GTT_MAX_RATE - GTT_MIN_RATE) * position;
    }

    private int currentLoopResource() {
        return soundPack.isLayered()
                ? soundPack.engineLoopResource(currentBand) : soundPack.engineLoopResource();
    }

    private String bandName() {
        if (soundPack == null || !soundPack.isLayered()) return "ENGINE LOOP";
        return currentBand == 0 ? "IDLE" : "GEAR " + currentBand;
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
        if (engineLoopStream != 0) soundPool.setVolume(engineLoopStream, volume, volume);
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

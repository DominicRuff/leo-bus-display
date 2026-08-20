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

import java.util.HashMap;
import java.util.Map;

public final class BusAudioController {
    public interface Listener {
        void onSpeakerStatusChanged(String status);
        void onEngineStateChanged(boolean enabled, String status);
    }

    private static final float MIN_RATE = 0.70f;
    private static final float MAX_RATE = 1.55f;
    private static final long RATE_UPDATE_MS = 50;

    private final Context context;
    private final AudioManager audioManager;
    private final Listener listener;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final SoundPool soundPool;
    private final AudioFocusRequest focusRequest;
    private final Map<Integer, Integer> soundIdsByResource = new HashMap<>();
    private final Map<Integer, Boolean> loadedSoundIds = new HashMap<>();

    private BusSoundPack soundPack;
    private int engineStartStream;
    private int engineLoopStream;
    private boolean engineRequested;
    private boolean resumeAfterFocusGain;
    private boolean focusOwned;
    private boolean ducked;
    private boolean released;
    private float engineVolume = 0.75f;
    private float effectsVolume = 0.85f;
    private float currentRate = MIN_RATE;
    private float targetRate = MIN_RATE;

    public BusAudioController(Context context, Listener listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;
        audioManager = (AudioManager) this.context.getSystemService(Context.AUDIO_SERVICE);

        AudioAttributes attributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build();
        soundPool = new SoundPool.Builder()
                .setMaxStreams(4)
                .setAudioAttributes(attributes)
                .build();
        soundPool.setOnLoadCompleteListener((pool, sampleId, status) -> {
            loadedSoundIds.put(sampleId, status == 0);
            if (status != 0) {
                engineRequested = false;
                stopEngineStreams();
                abandonAudioFocus();
                listener.onEngineStateChanged(false, "Could not load a bus sound.");
            } else if (engineRequested) {
                tryStartEngine();
            }
        });

        focusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(attributes)
                .setOnAudioFocusChangeListener(this::onAudioFocusChange, handler)
                .build();

        if (audioManager != null) {
            audioManager.registerAudioDeviceCallback(audioDeviceCallback, handler);
        }
        setSoundPack(BusSoundPack.PROTOTYPE_DIESEL_TEST);
        refreshSpeakerStatus();
    }

    public void setSoundPack(BusSoundPack pack) {
        if (released || pack == null || pack == soundPack) return;
        stopEngine();
        for (int soundId : soundIdsByResource.values()) soundPool.unload(soundId);
        soundIdsByResource.clear();
        loadedSoundIds.clear();
        soundPack = pack;
        load(pack.engineStartResource());
        load(pack.engineLoopResource());
        load(pack.gearChangeResource());
        load(pack.hornResource());
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

    public boolean isEngineEnabled() {
        return engineRequested;
    }

    public void setSpeedKmh(int speedKmh) {
        int bounded = Math.max(0, Math.min(80, speedKmh));
        targetRate = MIN_RATE + (MAX_RATE - MIN_RATE) * bounded / 80f;
        if (engineLoopStream != 0) startRateSmoother();
    }

    public void setEngineVolume(int percent) {
        engineVolume = boundedVolume(percent);
        applyEngineVolume();
    }

    public void setEffectsVolume(int percent) {
        effectsVolume = boundedVolume(percent);
    }

    public void playHorn() {
        playEffect(soundPack == null ? 0 : soundPack.hornResource());
    }

    public void playGearChange() {
        playEffect(soundPack == null ? 0 : soundPack.gearChangeResource());
    }

    public void refreshSpeakerStatus() {
        if (released) return;
        listener.onSpeakerStatusChanged(findBluetoothSpeakerStatus());
    }

    public void release() {
        if (released) return;
        stopEngine();
        released = true;
        handler.removeCallbacksAndMessages(null);
        if (audioManager != null) {
            audioManager.unregisterAudioDeviceCallback(audioDeviceCallback);
        }
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
        if (released || !engineRequested || soundPack == null
                || !isLoaded(soundPack.engineStartResource())
                || !isLoaded(soundPack.engineLoopResource())
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

    private final Runnable startEngineLoop = () -> {
        engineStartStream = 0;
        if (released || !engineRequested || soundPack == null || engineLoopStream != 0) return;
        int soundId = soundIdsByResource.get(soundPack.engineLoopResource());
        engineLoopStream = soundPool.play(soundId, effectiveEngineVolume(),
                effectiveEngineVolume(), 10, -1, currentRate);
        if (engineLoopStream == 0) {
            stopEngine();
            listener.onEngineStateChanged(false, "Could not start the engine loop.");
        } else {
            startRateSmoother();
        }
    };

    private void stopEngineStreams() {
        handler.removeCallbacks(startEngineLoop);
        handler.removeCallbacks(rateSmoother);
        if (engineStartStream != 0) soundPool.stop(engineStartStream);
        if (engineLoopStream != 0) soundPool.stop(engineLoopStream);
        engineStartStream = 0;
        engineLoopStream = 0;
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
        int soundId = soundIdsByResource.get(resource);
        soundPool.play(soundId, effectsVolume, effectsVolume, 1, 0, 1f);
    }

    private boolean requestAudioFocus() {
        if (focusOwned) return true;
        if (audioManager == null) return false;
        focusOwned = audioManager.requestAudioFocus(focusRequest)
                == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
        return focusOwned;
    }

    private void abandonAudioFocus() {
        if (focusOwned && audioManager != null) {
            audioManager.abandonAudioFocusRequest(focusRequest);
        }
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

    private void startRateSmoother() {
        handler.removeCallbacks(rateSmoother);
        handler.post(rateSmoother);
    }

    private final Runnable rateSmoother = new Runnable() {
        @Override
        public void run() {
            if (released || engineLoopStream == 0) return;
            float difference = targetRate - currentRate;
            if (Math.abs(difference) < 0.005f) {
                currentRate = targetRate;
            } else {
                currentRate += difference * 0.20f;
            }
            currentRate = Math.max(0.5f, Math.min(2.0f, currentRate));
            soundPool.setRate(engineLoopStream, currentRate);
            if (currentRate != targetRate) handler.postDelayed(this, RATE_UPDATE_MS);
        }
    };

    private void applyEngineVolume() {
        float volume = effectiveEngineVolume();
        if (engineStartStream != 0) soundPool.setVolume(engineStartStream, volume, volume);
        if (engineLoopStream != 0) soundPool.setVolume(engineLoopStream, volume, volume);
    }

    private float effectiveEngineVolume() {
        return engineVolume * (ducked ? 0.25f : 1f);
    }

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
        public void onAudioDevicesAdded(AudioDeviceInfo[] addedDevices) {
            refreshSpeakerStatus();
        }

        @Override
        public void onAudioDevicesRemoved(AudioDeviceInfo[] removedDevices) {
            refreshSpeakerStatus();
        }
    };
}

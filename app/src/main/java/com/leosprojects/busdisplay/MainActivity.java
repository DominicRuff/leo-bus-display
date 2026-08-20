package com.leosprojects.busdisplay;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.AdapterView;

import com.leosprojects.busdisplay.sound.SoundPackProfile;

import java.util.ArrayList;
import java.util.List;

public final class MainActivity extends Activity implements BadgeBleClient.Listener,
        BusAudioController.Listener, GpsSpeedController.Listener {
    private static final int BLE_PERMISSION_REQUEST = 4513;
    private static final int LOCATION_PERMISSION_REQUEST = 4514;
    private static final String SPEED_PREFS = "bus_speed_settings";
    private static final String SPEED_SOURCE_KEY = "speed_source";
    private static final String GPS_RESPONSE_KEY = "gps_response_percent";
    private static final int DEFAULT_GPS_RESPONSE = 80;
    private static final String MANUAL_SOURCE = "Manual Simulator";
    private static final String GPS_SOURCE = "GPS Road Speed";

    private final int amber = Color.rgb(255, 145, 20);
    private final int textPrimary = Color.rgb(245, 245, 245);
    private final int textSecondary = Color.rgb(185, 185, 185);
    private final int background = Color.rgb(16, 16, 16);

    private LedMatrixView preview;
    private Spinner destinationSpinner;
    private TextView statusText;
    private ProgressBar progressBar;
    private Button sendButton;
    private BadgeBleClient bleClient;
    private BusAudioController audioController;
    private GpsSpeedController gpsSpeedController;
    private TextView speakerStatusText;
    private TextView speedText;
    private TextView engineBandText;
    private TextView audioStatusText;
    private TextView soundPackDescriptionText;
    private TextView soundPackQaText;
    private Spinner soundPackSpinner;
    private Spinner speedSourceSpinner;
    private SeekBar speedSeekBar;
    private SeekBar gpsResponseSeekBar;
    private TextView speedLabelText;
    private TextView gpsStatusText;
    private TextView gpsResponseText;
    private Button engineButton;
    private boolean gpsMode;
    private boolean activityResumed;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        bleClient = new BadgeBleClient(this, this);
        setContentView(buildUi());
        audioController = new BusAudioController(this, this);
        gpsSpeedController = new GpsSpeedController(this, this);
        configureSoundProfiles();
        configureSpeedSource();

        List<String> names = DestinationLibrary.names();
        if (!names.isEmpty()) {
            updatePreview(names.get(0));
        }
    }

    private View buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(background);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(18), dp(20), dp(24));
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        scroll.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView title = text("LEO BUS DISPLAY", 24, textPrimary, true);
        title.setGravity(Gravity.CENTER);
        root.addView(title, matchWrap());

        TextView bus = text("BUS 4513", 18, amber, true);
        bus.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams busLp = matchWrap();
        busLp.topMargin = dp(4);
        root.addView(bus, busLp);

        TextView sub = text("44×11  •  FIXED  •  ORANGE / AMBER", 12, textSecondary, false);
        sub.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams subLp = matchWrap();
        subLp.topMargin = dp(4);
        root.addView(sub, subLp);

        preview = new LedMatrixView(this);
        LinearLayout.LayoutParams previewLp =
                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(210));
        previewLp.topMargin = dp(16);
        root.addView(preview, previewLp);

        TextView destinationLabel = text("Destination", 14, textSecondary, true);
        LinearLayout.LayoutParams labelLp = matchWrap();
        labelLp.topMargin = dp(16);
        root.addView(destinationLabel, labelLp);

        destinationSpinner = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                DestinationLibrary.names());
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        destinationSpinner.setAdapter(adapter);
        destinationSpinner.setPopupBackgroundDrawable(
                new android.graphics.drawable.ColorDrawable(Color.WHITE));
        destinationSpinner.setBackgroundTintList(ColorStateList.valueOf(amber));
        destinationSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view,
                                       int position, long id) {
                String name = (String) parent.getItemAtPosition(position);
                updatePreview(name);
                statusText.setText("Ready.");
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        LinearLayout.LayoutParams spinnerLp =
                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56));
        spinnerLp.topMargin = dp(4);
        root.addView(destinationSpinner, spinnerLp);

        sendButton = new Button(this);
        sendButton.setText("SEND TO BUS");
        sendButton.setTextSize(16);
        sendButton.setAllCaps(true);
        sendButton.setTextColor(Color.BLACK);
        sendButton.setBackgroundTintList(ColorStateList.valueOf(amber));
        sendButton.setOnClickListener(v -> sendSelected());

        LinearLayout.LayoutParams buttonLp =
                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(58));
        buttonLp.topMargin = dp(20);
        root.addView(sendButton, buttonLp);

        progressBar = new ProgressBar(
                this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(100);
        progressBar.setProgress(0);
        progressBar.setProgressTintList(ColorStateList.valueOf(amber));
        progressBar.setIndeterminateTintList(ColorStateList.valueOf(amber));
        LinearLayout.LayoutParams progressLp =
                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(8));
        progressLp.topMargin = dp(14);
        root.addView(progressBar, progressLp);

        addSoundUi(root);

        statusText = text("Ready.", 14, textSecondary, false);
        statusText.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams statusLp = matchWrap();
        statusLp.topMargin = dp(12);
        root.addView(statusText, statusLp);

        TextView help = text(
                "Turn on the Funduino / B1144 display and Bluetooth, "
                        + "choose a destination, then press SEND TO BUS. "
                        + "The sign is sent as a static 44×11 bitmap — no scrolling.",
                13, textSecondary, false);
        help.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams helpLp = matchWrap();
        helpLp.topMargin = dp(22);
        root.addView(help, helpLp);

        TextView credit = text(
                "Prototype 0.6 • BLE display + responsive GPS bus audio",
                11, Color.rgb(120, 120, 120), false);
        credit.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams creditLp = matchWrap();
        creditLp.topMargin = dp(22);
        root.addView(credit, creditLp);

        return scroll;
    }

    private void addSoundUi(LinearLayout root) {
        View divider = new View(this);
        divider.setBackgroundColor(Color.rgb(65, 65, 65));
        LinearLayout.LayoutParams dividerLp =
                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1));
        dividerLp.topMargin = dp(28);
        dividerLp.bottomMargin = dp(22);
        root.addView(divider, dividerLp);

        TextView soundTitle = text("BUS SOUND", 22, amber, true);
        soundTitle.setGravity(Gravity.CENTER);
        root.addView(soundTitle, matchWrap());

        TextView speakerLabel = text("Bluetooth Speaker", 14, textPrimary, true);
        LinearLayout.LayoutParams speakerLabelLp = matchWrap();
        speakerLabelLp.topMargin = dp(18);
        root.addView(speakerLabel, speakerLabelLp);

        speakerStatusText = text("Checking Bluetooth media output…", 14, textSecondary, false);
        root.addView(speakerStatusText, matchWrap());

        Button chooseSpeaker = soundButton("CHOOSE BUS SPEAKER");
        chooseSpeaker.setOnClickListener(v ->
                startActivity(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS)));
        LinearLayout.LayoutParams chooseLp = matchWrapHeight(54);
        chooseLp.topMargin = dp(10);
        root.addView(chooseSpeaker, chooseLp);

        Button refreshSpeaker = soundButton("REFRESH SPEAKER");
        refreshSpeaker.setOnClickListener(v -> {
            if (audioController != null) audioController.refreshSpeakerStatus();
        });
        LinearLayout.LayoutParams refreshLp = matchWrapHeight(50);
        refreshLp.topMargin = dp(8);
        root.addView(refreshSpeaker, refreshLp);

        TextView packLabel = text("Sound Pack", 14, textSecondary, true);
        LinearLayout.LayoutParams packLabelLp = matchWrap();
        packLabelLp.topMargin = dp(18);
        root.addView(packLabel, packLabelLp);

        soundPackSpinner = new Spinner(this);
        soundPackSpinner.setPopupBackgroundDrawable(
                new android.graphics.drawable.ColorDrawable(Color.WHITE));
        soundPackSpinner.setBackgroundTintList(ColorStateList.valueOf(amber));
        LinearLayout.LayoutParams packLp = matchWrapHeight(56);
        packLp.topMargin = dp(4);
        root.addView(soundPackSpinner, packLp);

        soundPackDescriptionText = text("Loading sound profiles…", 13, textSecondary, false);
        root.addView(soundPackDescriptionText, matchWrap());

        soundPackQaText = text("", 12, textSecondary, false);
        LinearLayout.LayoutParams qaLp = matchWrap();
        qaLp.topMargin = dp(8);
        root.addView(soundPackQaText, qaLp);

        TextView speedSourceLabel = text("Speed Source", 14, textSecondary, true);
        LinearLayout.LayoutParams sourceLabelLp = matchWrap();
        sourceLabelLp.topMargin = dp(20);
        root.addView(speedSourceLabel, sourceLabelLp);

        speedSourceSpinner = new Spinner(this);
        speedSourceSpinner.setPopupBackgroundDrawable(
                new android.graphics.drawable.ColorDrawable(Color.WHITE));
        speedSourceSpinner.setBackgroundTintList(ColorStateList.valueOf(amber));
        root.addView(speedSourceSpinner, matchWrapHeight(56));

        gpsStatusText = text("Manual simulator active.", 13, textSecondary, false);
        root.addView(gpsStatusText, matchWrap());

        gpsResponseText = text("GPS Response • 80%", 14, textSecondary, true);
        LinearLayout.LayoutParams responseLabelLp = matchWrap();
        responseLabelLp.topMargin = dp(10);
        root.addView(gpsResponseText, responseLabelLp);

        gpsResponseSeekBar = seekBar(80, 60);
        gpsResponseSeekBar.setOnSeekBarChangeListener(seekListener(value -> {
            int percent = value + 20;
            gpsResponseText.setText("GPS Response • " + percent + "%");
            if (gpsSpeedController != null) {
                gpsSpeedController.setSmoothingAlpha(percent / 100f);
                getSharedPreferences(SPEED_PREFS, MODE_PRIVATE).edit()
                        .putInt(GPS_RESPONSE_KEY, percent).apply();
            }
        }));
        root.addView(gpsResponseSeekBar, matchWrapHeight(46));

        TextView responseHelp = text(
                "Higher = faster • Lower = smoother", 12, textSecondary, false);
        root.addView(responseHelp, matchWrap());

        Button gpsSettings = soundButton("GPS SETTINGS");
        gpsSettings.setOnClickListener(v ->
                startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)));
        LinearLayout.LayoutParams settingsLp = matchWrapHeight(48);
        settingsLp.topMargin = dp(6);
        root.addView(gpsSettings, settingsLp);

        speedLabelText = text("SIMULATED SPEED", 14, textSecondary, true);
        speedLabelText.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams speedLabelLp = matchWrap();
        speedLabelLp.topMargin = dp(14);
        root.addView(speedLabelText, speedLabelLp);

        speedText = text("00 km/h", 28, amber, true);
        speedText.setGravity(Gravity.CENTER);
        root.addView(speedText, matchWrap());

        engineBandText = text("IDLE", 18, textPrimary, true);
        engineBandText.setGravity(Gravity.CENTER);
        root.addView(engineBandText, matchWrap());

        speedSeekBar = seekBar(80, 0);
        speedSeekBar.setOnSeekBarChangeListener(seekListener(value -> {
            if (!gpsMode) applyManualSpeed();
        }));
        root.addView(speedSeekBar, matchWrapHeight(52));

        engineButton = soundButton("ENGINE ON");
        engineButton.setTextSize(17);
        engineButton.setOnClickListener(v -> {
            if (audioController != null) {
                audioController.setEngineEnabled(!audioController.isEngineEnabled());
            }
        });
        LinearLayout.LayoutParams engineLp = matchWrapHeight(60);
        engineLp.topMargin = dp(8);
        root.addView(engineButton, engineLp);

        audioStatusText = text("Engine stopped.", 13, textSecondary, false);
        audioStatusText.setGravity(Gravity.CENTER);
        root.addView(audioStatusText, matchWrap());

        TextView engineVolumeLabel = text("Engine volume", 14, textSecondary, true);
        LinearLayout.LayoutParams engineVolumeLp = matchWrap();
        engineVolumeLp.topMargin = dp(18);
        root.addView(engineVolumeLabel, engineVolumeLp);
        SeekBar engineVolume = seekBar(100, 100);
        engineVolume.setOnSeekBarChangeListener(seekListener(value -> {
            if (audioController != null) audioController.setEngineVolume(value);
        }));
        root.addView(engineVolume, matchWrapHeight(46));

        TextView effectsVolumeLabel = text("Effects volume", 14, textSecondary, true);
        root.addView(effectsVolumeLabel, matchWrap());
        SeekBar effectsVolume = seekBar(100, 100);
        effectsVolume.setOnSeekBarChangeListener(seekListener(value -> {
            if (audioController != null) audioController.setEffectsVolume(value);
        }));
        root.addView(effectsVolume, matchWrapHeight(46));

        Button horn = soundButton("TOOT!");
        horn.setTextSize(22);
        horn.setOnClickListener(v -> {
            if (audioController != null) audioController.playHorn();
        });
        LinearLayout.LayoutParams hornLp = matchWrapHeight(72);
        hornLp.topMargin = dp(10);
        root.addView(horn, hornLp);

        Button gear = soundButton("TEST GEAR CHANGE");
        gear.setOnClickListener(v -> {
            if (audioController != null) audioController.playGearChange();
        });
        LinearLayout.LayoutParams gearLp = matchWrapHeight(52);
        gearLp.topMargin = dp(8);
        gearLp.bottomMargin = dp(18);
        root.addView(gear, gearLp);

        Button testIdle = soundButton("TEST IDLE");
        testIdle.setOnClickListener(v -> {
            speedSeekBar.setProgress(0);
            if (audioController != null) audioController.setEngineEnabled(true);
        });
        root.addView(testIdle, matchWrapHeight(50));

        Button testAcceleration = soundButton("TEST ACCELERATION");
        testAcceleration.setOnClickListener(v -> {
            speedSeekBar.setProgress(Math.min(80, speedSeekBar.getProgress() + 10));
            if (audioController != null) audioController.setEngineEnabled(true);
        });
        root.addView(testAcceleration, matchWrapHeight(50));

        Button testBrake = soundButton("TEST BRAKE");
        testBrake.setOnClickListener(v -> {
            if (audioController != null) audioController.playBrake();
        });
        root.addView(testBrake, matchWrapHeight(50));

        Button testDoorsOpen = soundButton("TEST DOORS OPEN");
        testDoorsOpen.setOnClickListener(v -> {
            if (audioController != null) audioController.openDoors();
        });
        root.addView(testDoorsOpen, matchWrapHeight(50));

        Button testDoorsClose = soundButton("TEST DOORS CLOSE");
        testDoorsClose.setOnClickListener(v -> {
            if (audioController != null) audioController.closeDoors();
        });
        root.addView(testDoorsClose, matchWrapHeight(50));

        Button testAll = soundButton("TEST ALL SOUNDS");
        testAll.setOnClickListener(v -> {
            if (audioController != null) {
                audioController.setEngineEnabled(true);
                audioController.playGearChange();
                audioController.playBrake();
                audioController.playHorn();
            }
        });
        LinearLayout.LayoutParams allLp = matchWrapHeight(50);
        allLp.bottomMargin = dp(18);
        root.addView(testAll, allLp);
    }

    private void configureSoundProfiles() {
        List<SoundPackProfile> profiles = audioController == null
                ? new ArrayList<>() : audioController.getAvailableProfiles();
        ArrayAdapter<SoundPackProfile> adapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, profiles);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        soundPackSpinner.setAdapter(adapter);

        SoundPackProfile selected = audioController == null
                ? null : audioController.getSelectedProfile();
        int selectedIndex = 0;
        for (int index = 0; index < profiles.size(); index++) {
            if (selected != null && selected.id.equals(profiles.get(index).id)) selectedIndex = index;
        }
        if (!profiles.isEmpty()) soundPackSpinner.setSelection(selectedIndex, false);
        updateSoundProfileInfo(selected);
        soundPackSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                SoundPackProfile profile = (SoundPackProfile) parent.getItemAtPosition(position);
                if (audioController != null && audioController.setSoundPackProfile(profile)) {
                    updateSoundProfileInfo(profile);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private void configureSpeedSource() {
        List<String> sources = java.util.Arrays.asList(MANUAL_SOURCE, GPS_SOURCE);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, sources);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        speedSourceSpinner.setAdapter(adapter);

        SharedPreferences preferences = getSharedPreferences(SPEED_PREFS, MODE_PRIVATE);
        int response = Math.max(20, Math.min(100,
                preferences.getInt(GPS_RESPONSE_KEY, DEFAULT_GPS_RESPONSE)));
        gpsResponseSeekBar.setProgress(response - 20);
        gpsSpeedController.setSmoothingAlpha(response / 100f);
        boolean savedGps = GPS_SOURCE.equals(preferences.getString(SPEED_SOURCE_KEY, MANUAL_SOURCE));
        if (savedGps && gpsSpeedController.hasPreciseLocationPermission()) {
            gpsMode = true;
            speedSourceSpinner.setSelection(1, false);
            updateSpeedSourceUi();
        } else {
            gpsMode = false;
            speedSourceSpinner.setSelection(0, false);
            if (savedGps) {
                preferences.edit().putString(SPEED_SOURCE_KEY, MANUAL_SOURCE).apply();
                gpsStatusText.setText("Precise location is required for GPS speed.");
            }
            updateSpeedSourceUi();
            applyManualSpeed();
        }

        speedSourceSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position == 1 && !gpsMode) selectGpsExplicitly();
                else if (position == 0 && gpsMode) selectManualMode(null);
            }

            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private void selectGpsExplicitly() {
        gpsMode = true;
        updateSpeedSourceUi();
        if (!gpsSpeedController.hasPreciseLocationPermission()) {
            gpsStatusText.setText("Precise location permission is required.");
            requestPermissions(new String[] {
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_FINE_LOCATION
            }, LOCATION_PERMISSION_REQUEST);
            return;
        }
        saveSpeedSource(GPS_SOURCE);
        if (activityResumed) gpsSpeedController.start();
    }

    private void selectManualMode(String status) {
        gpsMode = false;
        gpsSpeedController.stop();
        gpsSpeedController.resetFilter();
        saveSpeedSource(MANUAL_SOURCE);
        if (speedSourceSpinner.getSelectedItemPosition() != 0) {
            speedSourceSpinner.setSelection(0, false);
        }
        updateSpeedSourceUi();
        applyManualSpeed();
        gpsStatusText.setText(status == null ? "Manual simulator active." : status);
    }

    private void updateSpeedSourceUi() {
        speedSeekBar.setEnabled(!gpsMode);
        speedSeekBar.setAlpha(gpsMode ? 0.45f : 1f);
        gpsResponseSeekBar.setEnabled(gpsMode);
        gpsResponseSeekBar.setAlpha(gpsMode ? 1f : 0.45f);
        speedLabelText.setText(gpsMode ? "GPS ROAD SPEED" : "SIMULATED SPEED");
        if (gpsMode) gpsStatusText.setText("Waiting for GPS fix...");
    }

    private void applyManualSpeed() {
        int speed = speedSeekBar.getProgress();
        speedText.setText(String.format(java.util.Locale.US, "%02d km/h", speed));
        if (audioController != null) audioController.setSpeedKmh(speed);
    }

    private void saveSpeedSource(String source) {
        getSharedPreferences(SPEED_PREFS, MODE_PRIVATE).edit()
                .putString(SPEED_SOURCE_KEY, source).apply();
    }

    private void updateSoundProfileInfo(SoundPackProfile profile) {
        soundPackDescriptionText.setText(profile == null
                ? "No valid sound profile available" : profile.description);
        soundPackQaText.setText(audioController == null
                ? "" : audioController.getSelectedProfileQaInfo());
    }

    private void sendSelected() {
        if (!bleClient.hasRuntimePermissions()) {
            statusText.setText("Allow Bluetooth permission, then press SEND TO BUS.");
            bleClient.requestRuntimePermissions(BLE_PERMISSION_REQUEST);
            return;
        }

        String destination = (String) destinationSpinner.getSelectedItem();
        if (destination == null) return;

        List<byte[]> chunks =
                BadgePacketBuilder.build(DestinationLibrary.payloadHex(destination));

        sendButton.setEnabled(false);
        progressBar.setProgress(0);
        statusText.setText("Preparing " + destination + "…");
        bleClient.send(chunks);
    }

    private void updatePreview(String destination) {
        preview.setMatrix(DestinationLibrary.matrix(destination));
    }

    @Override
    public void onStatus(String text) {
        runOnUiThread(() -> statusText.setText(text));
    }

    @Override
    public void onProgress(int percent) {
        runOnUiThread(() -> progressBar.setProgress(percent));
    }

    @Override
    public void onFinished(boolean success, String message) {
        runOnUiThread(() -> {
            sendButton.setEnabled(true);
            statusText.setText(message);
            if (success) progressBar.setProgress(100);
        });
    }

    @Override
    public void onSpeakerStatusChanged(String status) {
        runOnUiThread(() -> speakerStatusText.setText(status));
    }

    @Override
    public void onEngineStateChanged(boolean enabled, String status) {
        runOnUiThread(() -> {
            engineButton.setText(enabled ? "ENGINE OFF" : "ENGINE ON");
            if (status != null && !status.isEmpty()) audioStatusText.setText(status);
        });
    }

    @Override
    public void onEngineBandChanged(String band) {
        runOnUiThread(() -> engineBandText.setText(band));
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String[] permissions,
                                           int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == BLE_PERMISSION_REQUEST) {
            boolean granted = true;
            for (int result : grantResults) {
                granted &= result == PackageManager.PERMISSION_GRANTED;
            }
            statusText.setText(granted
                    ? "Bluetooth permission granted. Press SEND TO BUS."
                    : "Bluetooth permission was not granted.");
        } else if (requestCode == LOCATION_PERMISSION_REQUEST) {
            if (gpsSpeedController.hasPreciseLocationPermission()) {
                saveSpeedSource(GPS_SOURCE);
                if (activityResumed) gpsSpeedController.start();
            } else {
                selectManualMode("Precise location is required for GPS speed.");
            }
        }
    }

    @Override
    public void onGpsSpeed(float filteredKmh) {
        runOnUiThread(() -> {
            if (!gpsMode) return;
            speedText.setText(String.format(java.util.Locale.US, "%.1f km/h", filteredKmh));
            int appliedSpeed = Math.max(0, Math.min(80, Math.round(filteredKmh)));
            if (audioController != null) audioController.setSpeedKmh(appliedSpeed);
        });
    }

    @Override
    public void onGpsStatus(String status) {
        runOnUiThread(() -> {
            if (!gpsMode) return;
            if (!gpsSpeedController.hasPreciseLocationPermission()
                    && "Precise location is required for GPS speed.".equals(status)) {
                selectManualMode(status);
            } else {
                gpsStatusText.setText(status);
            }
        });
    }

    @Override
    protected void onDestroy() {
        if (bleClient != null) bleClient.cancel();
        if (gpsSpeedController != null) gpsSpeedController.release();
        if (audioController != null) audioController.release();
        super.onDestroy();
    }

    @Override
    protected void onStop() {
        activityResumed = false;
        if (gpsSpeedController != null) gpsSpeedController.stop();
        if (audioController != null) audioController.setEngineEnabled(false);
        super.onStop();
    }

    @Override
    protected void onResume() {
        super.onResume();
        activityResumed = true;
        if (audioController != null) audioController.refreshSpeakerStatus();
        if (gpsMode) {
            if (gpsSpeedController.hasPreciseLocationPermission()) {
                gpsSpeedController.start();
            } else {
                selectManualMode("Precise location is required for GPS speed.");
            }
        }
    }

    private TextView text(String value, int sp, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(color);
        view.setLineSpacing(0, 1.1f);
        if (bold) view.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        return view;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams matchWrapHeight(int heightDp) {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(heightDp));
    }

    private Button soundButton(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(true);
        button.setTextColor(Color.BLACK);
        button.setBackgroundTintList(ColorStateList.valueOf(amber));
        return button;
    }

    private SeekBar seekBar(int max, int progress) {
        SeekBar seekBar = new SeekBar(this);
        seekBar.setMax(max);
        seekBar.setProgress(progress);
        seekBar.setProgressTintList(ColorStateList.valueOf(amber));
        seekBar.setThumbTintList(ColorStateList.valueOf(amber));
        return seekBar;
    }

    private SeekBar.OnSeekBarChangeListener seekListener(
            java.util.function.IntConsumer onChanged) {
        return new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                onChanged.accept(progress);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        };
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}

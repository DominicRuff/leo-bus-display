package com.leosprojects.busdisplay;

import android.app.Activity;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.AdapterView;

import java.util.List;

public final class MainActivity extends Activity implements BadgeBleClient.Listener {
    private static final int BLE_PERMISSION_REQUEST = 4513;

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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        bleClient = new BadgeBleClient(this, this);
        setContentView(buildUi());

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
                "Prototype 0.1 • BLE FEE0/FEE1 • Based on the open Badge Magic protocol",
                11, Color.rgb(120, 120, 120), false);
        credit.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams creditLp = matchWrap();
        creditLp.topMargin = dp(22);
        root.addView(credit, creditLp);

        return scroll;
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
        }
    }

    @Override
    protected void onDestroy() {
        if (bleClient != null) bleClient.cancel();
        super.onDestroy();
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

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}

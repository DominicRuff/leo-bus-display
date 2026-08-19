package com.leosprojects.busdisplay;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelUuid;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

final class BadgeBleClient {
    private static final long OPERATION_TIMEOUT_MS = 15000;

    interface Listener {
        void onStatus(String text);
        void onProgress(int percent);
        void onFinished(boolean success, String message);
    }

    static final UUID SERVICE_UUID =
            UUID.fromString("0000fee0-0000-1000-8000-00805f9b34fb");
    static final UUID CHARACTERISTIC_UUID =
            UUID.fromString("0000fee1-0000-1000-8000-00805f9b34fb");

    private final Activity activity;
    private final Listener listener;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private BluetoothLeScanner scanner;
    private BluetoothGatt gatt;
    private List<byte[]> chunks = new ArrayList<>();
    private int chunkIndex = 0;
    private boolean finished = false;
    private String operationTimeoutMessage;

    BadgeBleClient(Activity activity, Listener listener) {
        this.activity = activity;
        this.listener = listener;
    }

    boolean hasRuntimePermissions() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true;
        return activity.checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN)
                    == PackageManager.PERMISSION_GRANTED
                && activity.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)
                    == PackageManager.PERMISSION_GRANTED;
    }

    void requestRuntimePermissions(int requestCode) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            activity.requestPermissions(
                    new String[] {
                            Manifest.permission.BLUETOOTH_SCAN,
                            Manifest.permission.BLUETOOTH_CONNECT
                    },
                    requestCode
            );
        }
    }

    void send(List<byte[]> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            finish(false, "Nothing to send.");
            return;
        }
        if (!hasRuntimePermissions()) {
            finish(false, "Bluetooth permission is required.");
            return;
        }

        BluetoothManager manager =
                (BluetoothManager) activity.getSystemService(Context.BLUETOOTH_SERVICE);
        BluetoothAdapter adapter = manager == null ? null : manager.getAdapter();

        if (adapter == null) {
            finish(false, "Bluetooth is not available on this phone.");
            return;
        }
        if (!adapter.isEnabled()) {
            finish(false, "Turn Bluetooth on, then press SEND TO BUS again.");
            return;
        }

        this.chunks = chunks;
        this.chunkIndex = 0;
        this.finished = false;
        this.scanner = adapter.getBluetoothLeScanner();

        if (scanner == null) {
            finish(false, "Bluetooth LE scanner is unavailable.");
            return;
        }

        listener.onStatus("Searching for the bus display…");
        listener.onProgress(0);

        // Scan without a name filter. Compatible badges are identified by
        // FEE0 service UUID; LSLED/VBLAB names are accepted as fallback.
        scanner.startScan(scanCallback);
        handler.postDelayed(scanTimeout, 15000);
    }

    void cancel() {
        stopScan();
        if (gatt != null) {
            try { gatt.disconnect(); } catch (Exception ignored) {}
            try { gatt.close(); } catch (Exception ignored) {}
            gatt = null;
        }
        if (!finished) finish(false, "Transfer cancelled.");
    }

    private final Runnable scanTimeout = () -> {
        if (!finished && gatt == null) {
            stopScan();
            finish(false, "No compatible 11×44 Bluetooth badge found.");
        }
    };

    private final ScanCallback scanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            if (finished || gatt != null) return;
            if (!looksCompatible(result)) return;

            stopScan();
            handler.removeCallbacks(scanTimeout);

            BluetoothDevice device = result.getDevice();
            String name = safeDeviceName(device);
            listener.onStatus("Found " + (name.isEmpty() ? "LED badge" : name)
                    + ". Connecting…");

            gatt = device.connectGatt(
                    activity,
                    false,
                    gattCallback,
                    BluetoothDevice.TRANSPORT_LE
            );
            scheduleOperationTimeout("Timed out connecting to the LED badge.");
        }

        @Override
        public void onScanFailed(int errorCode) {
            stopScan();
            finish(false, "Bluetooth scan failed (code " + errorCode + ").");
        }
    };

    private boolean looksCompatible(ScanResult result) {
        if (result.getScanRecord() != null) {
            List<ParcelUuid> uuids = result.getScanRecord().getServiceUuids();
            if (uuids != null) {
                for (ParcelUuid uuid : uuids) {
                    if (SERVICE_UUID.equals(uuid.getUuid())) return true;
                }
            }
            String recordName = result.getScanRecord().getDeviceName();
            if (nameLooksCompatible(recordName)) return true;
        }
        return nameLooksCompatible(safeDeviceName(result.getDevice()));
    }

    private boolean nameLooksCompatible(String name) {
        if (name == null) return false;
        String n = name.trim().toUpperCase(Locale.ROOT);
        return n.equals("LSLED") || n.equals("VBLAB") || n.contains("LED");
    }

    private String safeDeviceName(BluetoothDevice device) {
        try {
            String name = device.getName();
            return name == null ? "" : name;
        } catch (SecurityException ignored) {
            return "";
        }
    }

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt bluetoothGatt,
                                            int status,
                                            int newState) {
            if (finished) return;
            clearOperationTimeout();

            if (status != BluetoothGatt.GATT_SUCCESS) {
                finishAndClose(false, "Could not connect to the LED badge.");
                return;
            }

            if (newState == BluetoothProfile.STATE_CONNECTED) {
                listener.onStatus("Connected. Discovering display service…");
                if (!bluetoothGatt.discoverServices()) {
                    finishAndClose(false, "Could not discover badge services.");
                } else {
                    scheduleOperationTimeout("Timed out discovering badge services.");
                }
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                if (chunkIndex >= chunks.size()) {
                    finishAndClose(true, "Destination sent to bus 4513.");
                } else {
                    finishAndClose(false, "The LED badge disconnected too early.");
                }
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt bluetoothGatt, int status) {
            if (finished) return;
            clearOperationTimeout();
            if (status != BluetoothGatt.GATT_SUCCESS) {
                finishAndClose(false, "Badge service discovery failed.");
                return;
            }

            BluetoothGattService service = bluetoothGatt.getService(SERVICE_UUID);
            if (service == null) {
                finishAndClose(false, "Compatible FEE0 badge service not found.");
                return;
            }

            BluetoothGattCharacteristic characteristic =
                    service.getCharacteristic(CHARACTERISTIC_UUID);
            if (characteristic == null) {
                finishAndClose(false, "Compatible FEE1 write characteristic not found.");
                return;
            }

            chunkIndex = 0;
            listener.onStatus("Sending destination…");
            writeNext(bluetoothGatt, characteristic);
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt bluetoothGatt,
                                          BluetoothGattCharacteristic characteristic,
                                          int status) {
            if (finished) return;
            clearOperationTimeout();
            if (status != BluetoothGatt.GATT_SUCCESS) {
                finishAndClose(false, "Badge write failed (status " + status + ").");
                return;
            }

            chunkIndex++;
            int percent = Math.min(100,
                    Math.round((chunkIndex * 100f) / Math.max(1, chunks.size())));
            listener.onProgress(percent);

            if (chunkIndex >= chunks.size()) {
                handler.postDelayed(() ->
                        finishAndClose(true, "Destination sent to bus 4513."), 350);
            } else {
                handler.postDelayed(() ->
                        writeNext(bluetoothGatt, characteristic), 120);
            }
        }
    };

    private void writeNext(BluetoothGatt bluetoothGatt,
                           BluetoothGattCharacteristic characteristic) {
        if (finished || chunkIndex >= chunks.size()) return;
        byte[] chunk = chunks.get(chunkIndex);

        boolean started;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            int result = bluetoothGatt.writeCharacteristic(
                    characteristic,
                    chunk,
                    BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            );
            started = result == BluetoothGatt.GATT_SUCCESS;
        } else {
            characteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
            characteristic.setValue(chunk);
            started = bluetoothGatt.writeCharacteristic(characteristic);
        }

        if (!started) {
            finishAndClose(false, "Could not start Bluetooth write.");
        } else {
            scheduleOperationTimeout("Timed out writing to the LED badge.");
        }
    }

    private void scheduleOperationTimeout(String message) {
        clearOperationTimeout();
        operationTimeoutMessage = message;
        handler.postDelayed(operationTimeout, OPERATION_TIMEOUT_MS);
    }

    private void clearOperationTimeout() {
        handler.removeCallbacks(operationTimeout);
        operationTimeoutMessage = null;
    }

    private final Runnable operationTimeout = () -> {
        if (!finished) {
            finishAndClose(false, operationTimeoutMessage == null
                    ? "Bluetooth operation timed out."
                    : operationTimeoutMessage);
        }
    };

    private void stopScan() {
        handler.removeCallbacks(scanTimeout);
        if (scanner != null) {
            try { scanner.stopScan(scanCallback); } catch (Exception ignored) {}
            scanner = null;
        }
    }

    private void finishAndClose(boolean success, String message) {
        if (finished) return;
        finished = true;
        clearOperationTimeout();
        stopScan();
        BluetoothGatt oldGatt = gatt;
        gatt = null;
        if (oldGatt != null) {
            try { oldGatt.disconnect(); } catch (Exception ignored) {}
            handler.postDelayed(() -> {
                try { oldGatt.close(); } catch (Exception ignored) {}
            }, 250);
        }
        handler.post(() -> listener.onFinished(success, message));
    }

    private void finish(boolean success, String message) {
        if (finished) return;
        finished = true;
        clearOperationTimeout();
        stopScan();
        handler.post(() -> listener.onFinished(success, message));
    }
}

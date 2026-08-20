package com.leosprojects.busdisplay;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.SystemClock;

import java.util.Locale;

/** Owns foreground-only platform GPS updates and fix-quality validation. */
public final class GpsSpeedController implements LocationListener {
    public interface Listener {
        void onGpsSpeed(float filteredKmh);
        void onGpsStatus(String status);
    }

    private static final long MIN_TIME_MS = 500;
    private static final float MIN_DISTANCE_METRES = 0f;
    private static final long MAX_FIX_AGE_NANOS = 3_000_000_000L;
    private static final float MAX_ACCURACY_METRES = 50f;

    private final Context context;
    private final LocationManager locationManager;
    private final Listener listener;
    private final GpsSpeedFilter filter = new GpsSpeedFilter();
    private boolean running;

    public GpsSpeedController(Context context, Listener listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;
        locationManager = (LocationManager) this.context.getSystemService(Context.LOCATION_SERVICE);
    }

    public boolean hasPreciseLocationPermission() {
        return context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    public void start() {
        stop();
        filter.reset();
        if (!hasPreciseLocationPermission()) {
            listener.onGpsStatus("Precise location is required for GPS speed.");
            return;
        }
        try {
            if (locationManager == null
                    || !locationManager.getAllProviders().contains(LocationManager.GPS_PROVIDER)) {
                listener.onGpsStatus("GPS provider is unavailable on this device.");
                return;
            }
            if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                listener.onGpsStatus("GPS is disabled. Enable Location in Android settings.");
                return;
            }
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER,
                    MIN_TIME_MS, MIN_DISTANCE_METRES, this);
            running = true;
            listener.onGpsStatus("Waiting for GPS fix...");
        } catch (SecurityException error) {
            listener.onGpsStatus("Precise location is required for GPS speed.");
        } catch (IllegalArgumentException error) {
            listener.onGpsStatus("GPS provider is unavailable on this device.");
        }
    }

    public void stop() {
        if (locationManager != null && running) {
            try {
                locationManager.removeUpdates(this);
            } catch (SecurityException ignored) {
                // Permission may have been revoked while the Activity was stopped.
            }
        }
        running = false;
    }

    public void resetFilter() { filter.reset(); }
    public void setSmoothingAlpha(float smoothingAlpha) {
        filter.setSmoothingAlpha(smoothingAlpha);
    }

    public void release() {
        stop();
        filter.reset();
    }

    @Override
    public void onLocationChanged(Location location) {
        if (!running || location == null || !location.hasSpeed()) {
            listener.onGpsStatus("GPS fix rejected • speed unavailable");
            return;
        }
        long fixNanos = location.getElapsedRealtimeNanos();
        long ageNanos = SystemClock.elapsedRealtimeNanos() - fixNanos;
        if (fixNanos <= 0 || ageNanos < 0 || ageNanos > MAX_FIX_AGE_NANOS) {
            listener.onGpsStatus("GPS fix rejected • stale location");
            return;
        }
        if (location.hasAccuracy() && location.getAccuracy() > MAX_ACCURACY_METRES) {
            listener.onGpsStatus(String.format(Locale.US,
                    "GPS fix rejected • accuracy %.0f m", location.getAccuracy()));
            return;
        }

        float filteredKmh = filter.update(location.getSpeed() * 3.6f);
        listener.onGpsSpeed(filteredKmh);
        String quality = location.hasAccuracy()
                ? String.format(Locale.US, "GPS fix • accuracy %.0f m", location.getAccuracy())
                : "GPS fix";
        if (filteredKmh > 80f) {
            quality = String.format(Locale.US,
                    "GPS %.1f km/h • bus simulator limited to 80 km/h", filteredKmh);
        }
        listener.onGpsStatus(quality);
    }

    @Override
    public void onProviderDisabled(String provider) {
        if (LocationManager.GPS_PROVIDER.equals(provider)) {
            listener.onGpsStatus("GPS is disabled. Enable Location in Android settings.");
        }
    }

    @Override public void onProviderEnabled(String provider) {}
    @Override public void onStatusChanged(String provider, int status, Bundle extras) {}
}

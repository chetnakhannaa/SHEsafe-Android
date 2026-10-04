package com.shesafe.app;

import android.Manifest;
import android.accessibilityservice.AccessibilityService;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class VolumeSosAccessibilityService extends AccessibilityService {
    private static final String DATABASE_URL = "https://women-safety-10-default-rtdb.asia-southeast1.firebasedatabase.app/alerts.json";
    private int volumePressCount = 0;
    private long firstVolumePressAt = 0L;
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
    }

    @Override
    public void onInterrupt() {
    }

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        if (event.getAction() != KeyEvent.ACTION_DOWN) {
            return false;
        }

        int keyCode = event.getKeyCode();
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            handleVolumePress();
            return true;
        }

        return false;
    }

    private void handleVolumePress() {
        long now = System.currentTimeMillis();
        if (now - firstVolumePressAt > 2200) {
            firstVolumePressAt = now;
            volumePressCount = 0;
        }

        volumePressCount++;
        toast("SHEsafe Volume SOS " + volumePressCount + "/3");
        handler.removeCallbacksAndMessages(null);
        handler.postDelayed(() -> volumePressCount = 0, 2300);

        if (volumePressCount >= 3) {
            volumePressCount = 0;
            sendBackgroundPoliceSos();
        }
    }

    private void sendBackgroundPoliceSos() {
        SharedPreferences prefs = getSharedPreferences("shesafe", MODE_PRIVATE);
        String name = prefs.getString("name", "");
        String phone = prefs.getString("phone", "");
        String contactName = prefs.getString("contactName", "");
        String contactPhone = prefs.getString("contactPhone", "");

        if (name.isEmpty() || phone.isEmpty() || contactName.isEmpty() || contactPhone.isEmpty()) {
            toast("Open SHEsafe once and complete login/contact setup.");
            return;
        }

        if (!hasLocationPermission()) {
            toast("Open SHEsafe and allow location permission.");
            return;
        }

        toast("SHEsafe SOS triggered.");

        new Thread(() -> {
            Location location = getBestLocation();
            if (location == null) {
                toast("Could not get live location. Turn on GPS.");
                return;
            }

            String map = "https://www.google.com/maps?q=" + location.getLatitude() + "," + location.getLongitude();

            try {
                JSONObject alert = new JSONObject();
                alert.put("name", name);
                alert.put("phone", contactPhone);
                alert.put("victimPhone", phone);
                alert.put("emergencyContactName", contactName);
                alert.put("emergencyContactPhone", contactPhone);
                alert.put("sosTarget", "volume-button-police");
                alert.put("latitude", location.getLatitude());
                alert.put("longitude", location.getLongitude());
                alert.put("time", new SimpleDateFormat("dd/MM/yyyy, hh:mm:ss a", Locale.getDefault()).format(new Date()));
                alert.put("map", map);
                alert.put("status", "Active");

                boolean sent = postToFirebase(alert);
                toast(sent ? "SOS sent to police dashboard." : "SOS failed. Check internet.");
            } catch (Exception e) {
                toast("SOS failed. Try again.");
            }
        }).start();
    }

    private boolean postToFirebase(JSONObject alert) {
        try {
            URL url = new URL(DATABASE_URL);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setDoOutput(true);
            byte[] body = alert.toString().getBytes(StandardCharsets.UTF_8);
            try (OutputStream output = connection.getOutputStream()) {
                output.write(body);
            }
            int code = connection.getResponseCode();
            connection.disconnect();
            return code >= 200 && code < 300;
        } catch (Exception e) {
            return false;
        }
    }

    private Location getBestLocation() {
        try {
            LocationManager manager = (LocationManager) getSystemService(LOCATION_SERVICE);
            if (manager == null) return null;
            Location gps = manager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            Location network = manager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            if (gps == null) return network;
            if (network == null) return gps;
            return gps.getTime() > network.getTime() ? gps : network;
        } catch (SecurityException e) {
            return null;
        }
    }

    private boolean hasLocationPermission() {
        return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private void toast(String message) {
        handler.post(() -> Toast.makeText(this, message, Toast.LENGTH_SHORT).show());
    }
}

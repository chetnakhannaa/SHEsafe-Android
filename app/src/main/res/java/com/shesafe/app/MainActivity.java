package com.shesafe.app;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final String DATABASE_URL = "https://women-safety-10-default-rtdb.asia-southeast1.firebasedatabase.app/alerts.json";
    private static final int LOCATION_REQUEST = 44;

    private SharedPreferences prefs;
    private LinearLayout root;
    private TextView stepStatus;
    private TextView connectionStatus;
    private TextView statusStrip;
    private TextView userNameView;
    private TextView userPhoneView;
    private TextView contactView;
    private TextView lastLocationView;
    private TextView incidentView;
    private TextView responseView;
    private int volumePressCount = 0;
    private long firstVolumePressAt = 0L;
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("shesafe", MODE_PRIVATE);
        requestLocationPermission();
        showInitialScreen();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            handleVolumePress();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    private void handleVolumePress() {
        long now = System.currentTimeMillis();
        if (now - firstVolumePressAt > 2200) {
            firstVolumePressAt = now;
            volumePressCount = 0;
        }

        volumePressCount++;
        toast("Volume SOS " + volumePressCount + "/3");
        handler.removeCallbacksAndMessages(null);
        handler.postDelayed(() -> volumePressCount = 0, 2300);

        if (volumePressCount >= 3) {
            volumePressCount = 0;
            sendSos("police");
        }
    }

    private void showInitialScreen() {
        if (get("name").isEmpty() || get("phone").isEmpty()) {
            showLoginScreen();
        } else if (get("contactName").isEmpty() || get("contactPhone").isEmpty()) {
            showContactScreen();
        } else {
            showSosScreen();
        }
    }

    private void buildBase(String stepText) {
        ScrollView scrollView = new ScrollView(this);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(22), dp(18), dp(22));
        root.setBackgroundColor(Color.rgb(244, 247, 251));
        scrollView.addView(root);
        setContentView(scrollView);

        LinearLayout header = card();
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        TextView logo = text("S", 24, Color.WHITE, true);
        logo.setGravity(Gravity.CENTER);
        logo.setBackgroundColor(Color.rgb(16, 24, 40));
        header.addView(logo, new LinearLayout.LayoutParams(dp(48), dp(48)));

        LinearLayout brand = new LinearLayout(this);
        brand.setOrientation(LinearLayout.VERTICAL);
        brand.setPadding(dp(12), 0, 0, 0);
        brand.addView(text("SHEsafe", 18, Color.rgb(16, 24, 40), true));
        brand.addView(text("Women safety response app", 13, Color.rgb(102, 112, 133), true));
        header.addView(brand, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        stepStatus = pill(stepText);
        header.addView(stepStatus);
        root.addView(header);
    }

    private void showLoginScreen() {
        buildBase("Step 1 of 3");
        TextView title = title("Create your safety profile");
        root.addView(title);
        root.addView(description("Your details are saved on this phone, so login is needed only the first time."));

        LinearLayout form = card();
        form.setOrientation(LinearLayout.VERTICAL);
        EditText name = input("Full name", get("name"));
        EditText phone = input("Your phone number", get("phone"));
        Button next = primaryButton("Continue");
        next.setOnClickListener(v -> {
            if (name.getText().toString().trim().isEmpty() || phone.getText().toString().trim().isEmpty()) {
                toast("Enter name and phone.");
                return;
            }
            prefs.edit()
                    .putString("name", name.getText().toString().trim())
                    .putString("phone", phone.getText().toString().trim())
                    .apply();
            showContactScreen();
        });
        form.addView(label("Full name"));
        form.addView(name);
        form.addView(label("Your phone number"));
        form.addView(phone);
        form.addView(next);
        root.addView(form);
    }

    private void showContactScreen() {
        buildBase("Step 2 of 3");
        root.addView(title("Add emergency contact"));
        root.addView(description("This person can receive your live location when you press Contact SOS or Both SOS."));

        LinearLayout form = card();
        form.setOrientation(LinearLayout.VERTICAL);
        EditText name = input("Contact name", get("contactName"));
        EditText phone = input("Contact phone number", get("contactPhone"));
        Button save = primaryButton("Save contact");
        Button back = secondaryButton("Back");
        save.setOnClickListener(v -> {
            if (name.getText().toString().trim().isEmpty() || phone.getText().toString().trim().isEmpty()) {
                toast("Enter emergency contact details.");
                return;
            }
            prefs.edit()
                    .putString("contactName", name.getText().toString().trim())
                    .putString("contactPhone", phone.getText().toString().trim())
                    .putBoolean("onboarded", true)
                    .apply();
            showSosScreen();
        });
        back.setOnClickListener(v -> showLoginScreen());
        form.addView(label("Contact name"));
        form.addView(name);
        form.addView(label("Contact phone number"));
        form.addView(phone);
        form.addView(save);
        form.addView(back);
        root.addView(form);
    }

    private void showSosScreen() {
        buildBase("Step 3 of 3");

        LinearLayout top = card();
        top.setOrientation(LinearLayout.VERTICAL);
        top.addView(text("Emergency mode", 12, Color.rgb(102, 112, 133), true));
        top.addView(title("Get help fast"));
        connectionStatus = pill("Firebase ready");
        top.addView(connectionStatus);
        root.addView(top);

        LinearLayout profile = card();
        profile.setOrientation(LinearLayout.VERTICAL);
        userNameView = text(get("name"), 19, Color.rgb(16, 24, 40), true);
        userPhoneView = text(get("phone"), 14, Color.rgb(102, 112, 133), true);
        contactView = text("Contact: " + get("contactName") + " - " + get("contactPhone"), 14, Color.rgb(52, 64, 84), true);
        Button edit = secondaryButton("Edit profile");
        edit.setOnClickListener(v -> showLoginScreen());
        profile.addView(text("Emergency profile", 12, Color.rgb(102, 112, 133), true));
        profile.addView(userNameView);
        profile.addView(userPhoneView);
        profile.addView(contactView);
        profile.addView(edit);
        root.addView(profile);

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.VERTICAL);
        buttons.addView(sosButton("Police SOS", Color.rgb(217, 45, 32), "police"));
        buttons.addView(sosButton("Contact SOS", Color.rgb(0, 137, 123), "contact"));
        buttons.addView(sosButton("Both SOS", Color.rgb(181, 71, 8), "both"));
        root.addView(buttons);

        LinearLayout status = card();
        status.setOrientation(LinearLayout.VERTICAL);
        lastLocationView = text("Last location: Not shared", 15, Color.rgb(52, 64, 84), true);
        incidentView = text("Incident reference: No active alert", 15, Color.rgb(52, 64, 84), true);
        responseView = text("Response state: Standing by", 15, Color.rgb(52, 64, 84), true);
        statusStrip = text("Press volume button 3 times to trigger Police SOS.", 15, Color.rgb(52, 64, 84), true);
        status.addView(lastLocationView);
        status.addView(incidentView);
        status.addView(responseView);
        status.addView(statusStrip);
        root.addView(status);

        LinearLayout backgroundCard = card();
        backgroundCard.setOrientation(LinearLayout.VERTICAL);
        backgroundCard.addView(text("Background trigger", 12, Color.rgb(102, 112, 133), true));
        backgroundCard.addView(description("Enable SHEsafe Volume SOS once in Accessibility Settings. Then three volume-button presses can trigger Police SOS even when this screen is not open."));
        Button enableAccessibility = primaryButton("Enable Volume SOS");
        enableAccessibility.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        backgroundCard.addView(enableAccessibility);
        root.addView(backgroundCard);
    }

    private Button sosButton(String text, int color, String target) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextColor(Color.WHITE);
        button.setTextSize(22);
        button.setAllCaps(false);
        button.setTypeface(null, 1);
        button.setBackgroundColor(color);
        button.setOnClickListener(v -> sendSos(target));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(88)
        );
        params.setMargins(0, 0, 0, dp(12));
        button.setLayoutParams(params);
        return button;
    }

    private void sendSos(String target) {
        if (!hasLocationPermission()) {
            requestLocationPermission();
            toast("Allow location permission, then try again.");
            return;
        }

        if (!isProfileComplete()) {
            toast("Complete login and contact first.");
            showInitialScreen();
            return;
        }

        if (statusStrip != null) statusStrip.setText("Getting live location...");
        if (responseView != null) responseView.setText("Response state: Locating");

        new Thread(() -> {
            Location location = getBestLocation();
            if (location == null) {
                runOnUiThread(() -> {
                    if (statusStrip != null) statusStrip.setText("Could not get live location. Turn on GPS and try again.");
                    if (responseView != null) responseView.setText("Response state: Location needed");
                });
                return;
            }

            String reference = "SOS-" + String.valueOf(System.currentTimeMillis()).substring(7);
            String map = "https://www.google.com/maps?q=" + location.getLatitude() + "," + location.getLongitude();

            try {
                JSONObject alert = new JSONObject();
                alert.put("name", get("name"));
                alert.put("phone", get("contactPhone"));
                alert.put("victimPhone", get("phone"));
                alert.put("emergencyContactName", get("contactName"));
                alert.put("emergencyContactPhone", get("contactPhone"));
                alert.put("sosTarget", target);
                alert.put("latitude", location.getLatitude());
                alert.put("longitude", location.getLongitude());
                alert.put("time", new SimpleDateFormat("dd/MM/yyyy, hh:mm:ss a", Locale.getDefault()).format(new Date()));
                alert.put("map", map);
                alert.put("status", "Active");

                boolean sent = postToFirebase(alert);
                runOnUiThread(() -> {
                    lastLocationView.setText("Last location: " + round(location.getLatitude()) + ", " + round(location.getLongitude()));
                    incidentView.setText("Incident reference: " + reference);
                    responseView.setText(sent ? "Response state: Police notified" : "Response state: Saved locally");
                    statusStrip.setText(sent ? "SOS sent to police dashboard with live location." : "Could not reach Firebase.");
                    if (target.equals("contact") || target.equals("both")) {
                        openSms(map, reference);
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (statusStrip != null) statusStrip.setText("SOS failed. Check internet and try again.");
                    if (responseView != null) responseView.setText("Response state: Retry needed");
                });
            }
        }).start();
    }

    private boolean  isProfileComplete() {
        return !get("name").isEmpty()
                && !get("phone").isEmpty()
                && !get("contactName").isEmpty()
                && !get("contactPhone").isEmpty();
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
            Location gps = null;
            Location network = null;
            if (hasLocationPermission()) {
                gps = manager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                network = manager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            }
            if (gps == null) return network;
            if (network == null) return gps;
            return gps.getTime() > network.getTime() ? gps : network;
        } catch (SecurityException e) {
            return null;
        }
    }

    private void openSms(String map, String reference) {
        String body = "SHEsafe SOS: " + get("name") + " needs help. Live location: " + map + ". Ref: " + reference;
        Intent intent = new Intent(Intent.ACTION_SENDTO);
        intent.setData(Uri.parse("smsto:" + Uri.encode(get("contactPhone"))));
        intent.putExtra("sms_body", body);
        startActivity(intent);
    }

    private void requestLocationPermission() {
        if (!hasLocationPermission()) {
            requestPermissions(new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
            }, LOCATION_REQUEST);
        }
    }

    private boolean hasLocationPermission() {
        return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private String get(String key) {
        return prefs.getString(key, "");
    }

    private String round(double value) {
        return String.format(Locale.US, "%.4f", value);
    }

    private LinearLayout card() {
        LinearLayout layout = new LinearLayout(this);
        layout.setPadding(dp(16), dp(16), dp(16), dp(16));
        layout.setBackgroundColor(Color.WHITE);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, 0, 0, dp(14));
        layout.setLayoutParams(params);
        return layout;
    }

    private TextView title(String value) {
        return text(value, 32, Color.rgb(16, 24, 40), true);
    }

    private TextView description(String value) {
        TextView view = text(value, 15, Color.rgb(102, 112, 133), true);
        view.setPadding(0, dp(8), 0, dp(12));
        return view;
    }

    private TextView label(String value) {
        TextView view = text(value, 14, Color.rgb(52, 64, 84), true);
        view.setPadding(0, dp(10), 0, dp(5));
        return view;
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        if (bold) view.setTypeface(null, 1);
        return view;
    }

    private TextView pill(String value) {
        TextView view = text(value, 13, Color.rgb(0, 105, 92), true);
        view.setPadding(dp(12), dp(8), dp(12), dp(8));
        view.setBackgroundColor(Color.rgb(229, 246, 242));
        return view;
    }

    private EditText input(String hint, String value) {
        EditText input = new EditText(this);
        input.setHint(hint);
        input.setText(value);
        input.setSingleLine(true);
        input.setTextSize(16);
        input.setPadding(dp(12), 0, dp(12), 0);
        input.setBackgroundColor(Color.rgb(244, 247, 251));
        input.setMinHeight(dp(48));
        return input;
    }

    private Button primaryButton(String value) {
        Button button = new Button(this);
        button.setText(value);
        button.setAllCaps(false);
        button.setTextColor(Color.WHITE);
        button.setTypeface(null, 1);
        button.setBackgroundColor(Color.rgb(0, 137, 123));
        return button;
    }

    private Button secondaryButton(String value) {
        Button button = new Button(this);
        button.setText(value);
        button.setAllCaps(false);
        button.setTextColor(Color.rgb(52, 64, 84));
        button.setTypeface(null, 1);
        button.setBackgroundColor(Color.WHITE);
        return button;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}

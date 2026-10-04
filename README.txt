SHEsafe Android APK Project

What this Android app does:
- Screen 1: Login / user profile
- Screen 2: Emergency contact
- Screen 3: SOS controls
- Police SOS sends live location to the Firebase police dashboard
- Contact SOS opens an SMS with live location for the saved emergency contact
- Triple volume-button press can trigger Police SOS even when the app screen is not open

Important setup for background volume SOS:
1. Install and open the SHEsafe app once.
2. Complete login and emergency contact.
3. Allow location permission.
4. Tap "Enable Volume SOS" inside the app.
5. Android Accessibility Settings will open.
6. Enable "SHEsafe Volume SOS".

After this, pressing the volume button 3 times can trigger Police SOS in the background.

How to make APK:
1. Open Android Studio.
2. Choose "Open".
3. Select this folder:
   C:\Users\chetn\Documents\Codex\2026-07-05\ma\outputs\SHEsafe-Android
4. Wait for Gradle sync.
5. Click Build > Build Bundle(s) / APK(s) > Build APK(s).
6. Android Studio will show the APK location.

Note:
Android does not allow ordinary apps to secretly listen to volume buttons in the background.
This project uses an Accessibility Service, which the phone user must enable manually.

# Cast Device Selector Dialog Fix

## Problem
When pressing "Play Sample Video", the Cast device chooser dialog was not being displayed, preventing users from selecting a Chromecast device.

## Root Cause
The `requestSession()` method in `IonicChromecastPlugin.java` was using a programmatic approach with `MediaRouteButton.showDialog()` which doesn't work reliably in all contexts.

## Solution
Changed the implementation to use `MediaRouteChooserDialogFragment`, which is the proper way to display the Cast device selector dialog according to Google Cast SDK best practices.

## Changes Made

### File: `ionic-chromecast/android/src/main/java/com/fabianacevedo/ionicchromecast/IonicChromecastPlugin.java`

**Before:**
```java
// Create a temporary MediaRouteButton to trigger the Cast device selector
AppCompatActivity activity = (AppCompatActivity) getActivity();
androidx.mediarouter.app.MediaRouteButton routeButton = new androidx.mediarouter.app.MediaRouteButton(activity);

String receiverId = CastOptionsProvider.sReceiverApplicationId;
if (TextUtils.isEmpty(receiverId)) {
    receiverId = "CC1AD845";
}

MediaRouteSelector selector = new MediaRouteSelector.Builder()
        .addControlCategory(CastMediaControlIntent.categoryForCast(receiverId))
        .build();

routeButton.setRouteSelector(selector);
routeButton.showDialog();
```

**After:**
```java
// Use MediaRouteChooserDialogFragment - the proper way to show Cast device selector
AppCompatActivity activity = (AppCompatActivity) getActivity();
FragmentManager fm = activity.getSupportFragmentManager();

String receiverId = CastOptionsProvider.sReceiverApplicationId;
if (TextUtils.isEmpty(receiverId)) {
    receiverId = "CC1AD845";
}

MediaRouteSelector selector = new MediaRouteSelector.Builder()
        .addControlCategory(CastMediaControlIntent.categoryForCast(receiverId))
        .build();

MediaRouteChooserDialogFragment dialogFragment = new MediaRouteChooserDialogFragment();
dialogFragment.setRouteSelector(selector);

// Show the dialog
dialogFragment.show(fm, "chromecast-device-chooser");
```

## Testing Steps

1. **Open the app** on your Android device
2. **Press "Play Sample Video"** button
3. **Expected behavior:**
   - A dialog titled "Cast. Disconnect" or "Choose a device" should appear immediately
   - Your Chromecast device should be listed in the dialog
   - After selecting the device, the session should connect (wait up to 45 seconds)
   - The video should start playing on your TV

4. **Check logs:**
   - Look for: `🚀 Showing Cast device selector via MediaRouteChooserDialog...`
   - Look for: `✅ Cast device selector dialog displayed`
   - Look for: `⭐ onSessionStarted called! sessionId=...`
   - Look for: `STEP 3: waitForSessionActive => true`
   - Look for: `Media load success`

## What to Monitor

### On UI:
- "Play Sample Video" button should trigger the Cast device chooser immediately
- After device selection, the session status should change to "✅ Connected to Chromecast"
- The play status should change to "▶️ Playing on TV!"

### In Logcat:
```bash
adb logcat | grep -E "IonicChromecast|CastSession|MediaRouteChooserDialog"
```

Look for:
1. Dialog displayed confirmation
2. Session lifecycle events (onSessionStarting → onSessionStarted)
3. Media load success

### In UI Event Log:
- `STEP 1: isSessionActive => false`
- `STEP 2: requestSession => {"success":true,...}`
- `STEP 3: Waiting up to 45s for session connection...`
- `⭐ onSessionStarted` (from listener)
- `STEP 3: waitForSessionActive => true`
- `STEP 4: Sending loadMedia request...`
- `STEP 4: loadMedia result => {"success":true}`
- `✅ SUCCESS: Video is now playing on Chromecast`

## Additional Notes

- The app is now using the recommended approach from Google's Cast SDK documentation
- The dialog is a proper Android DialogFragment that follows Android UI best practices
- This approach should work reliably across different Android versions and device configurations
- The session listener should fire `onSessionStarted` automatically when the user selects a device

## If Issues Persist

If the dialog still doesn't appear or the session doesn't connect:

1. Check that your Chromecast and phone are on the same WiFi network
2. Verify the Cast SDK is initialized (should see "✅ Cast SDK initialized successfully")
3. Check Android permissions are granted (location, network)
4. Try restarting the Chromecast device
5. Check logcat for any error messages

## Next Steps

If this works:
- Test with different video URLs
- Test pause/resume functionality
- Test disconnect/reconnect
- Clean up any remaining debug logs
- Update documentation with the working flow

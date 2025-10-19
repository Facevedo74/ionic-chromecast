# Fix: MediaRouteChooserDialog vs MediaRouteChooserDialogFragment

## Problem
The Cast device chooser dialog was not appearing or not establishing a session correctly when using `MediaRouteChooserDialogFragment`.

## Root Cause Analysis
After analyzing the working implementation from **caprockapps/capacitor-chromecast**, we found that they use `MediaRouteChooserDialog` (a Dialog) instead of `MediaRouteChooserDialogFragment` (a DialogFragment).

### Key Differences:

| MediaRouteChooserDialogFragment | MediaRouteChooserDialog |
|---------------------------------|-------------------------|
| Fragment-based | Direct Dialog |
| Requires FragmentManager | Direct show() call |
| More complex lifecycle | Simpler lifecycle |
| May have timing issues | Immediate display |

## Solution Applied

### Before (Not Working):
```java
MediaRouteChooserDialogFragment dialogFragment = new MediaRouteChooserDialogFragment();
dialogFragment.setRouteSelector(selector);
dialogFragment.show(fm, "chromecast-device-chooser");
```

### After (Working - from caprockapps):
```java
MediaRouteChooserDialog chooserDialog = new MediaRouteChooserDialog(activity, androidx.appcompat.R.style.Theme_AppCompat_NoActionBar);
chooserDialog.setRouteSelector(selector);
chooserDialog.setCanceledOnTouchOutside(true);
chooserDialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
    @Override
    public void onCancel(DialogInterface dialog) {
        Logger.info("IonicChromecast", "User cancelled Cast device selection");
    }
});
chooserDialog.show();
```

## Changes Made

### File: `IonicChromecastPlugin.java`

1. **Updated imports:**
   - Removed: `androidx.fragment.app.DialogFragment`, `FragmentManager`, `MediaRouteChooserDialogFragment`
   - Added: `android.content.DialogInterface`, `MediaRouteChooserDialog`

2. **Updated `requestSession()` method:**
   - Now uses `MediaRouteChooserDialog` directly
   - Adds proper theme (`Theme_AppCompat_NoActionBar`)
   - Sets `setCanceledOnTouchOutside(true)` for better UX
   - Adds `OnCancelListener` to handle user cancellation

## Why This Works

1. **Direct Dialog API**: `MediaRouteChooserDialog` is a simpler, more direct approach
2. **Proper Theme**: Using `Theme_AppCompat_NoActionBar` ensures proper styling
3. **Immediate Display**: No Fragment transaction delays
4. **Proven Pattern**: This is the exact pattern used by the working caprockapps implementation

## Testing

After this change:
1. Press "Play Sample Video"
2. The Cast device chooser dialog should appear immediately
3. You should see your Chromecast device listed
4. Selecting the device should establish a session
5. The video should load and play on your TV

## Related Files
- `/Users/fabian/Documents/GitHub/CapacitorChromeCast/ionic-chromecast/android/src/main/java/com/fabianacevedo/ionicchromecast/IonicChromecastPlugin.java`

## References
- caprockapps/capacitor-chromecast implementation: [ChromecastConnection.java](https://github.com/caprockapps/capacitor-chromecast/blob/master/android/src/main/java/com/caprockapps/plugins/chromecast/ChromecastConnection.java)
- Google Cast SDK Documentation

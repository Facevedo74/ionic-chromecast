# Testing Different Receiver Application IDs

## Changes Applied

### 1. Switched back to Default Media Receiver
- **From:** `C0868879` (Styled Media Receiver)
- **To:** `CC1AD845` (Default Media Receiver)
- **Reason:** Most compatible, widely tested, no special requirements

### 2. Added Session Stabilization Delay
- Added 2-second delay after session connection before sending media
- This allows the Cast session to fully establish before loading media
- Prevents race conditions where media is sent before session is ready

## Code Changes

### JavaScript (example.js)

```javascript
// Changed receiver ID
receiverApplicationId: 'CC1AD845' // Default Media Receiver - most compatible

// Added stabilization delay after session connects
sessionEl.textContent = '✅ Connected to Chromecast';
sessionEl.style.color = 'green';
appendLog('STEP 3: Session connected successfully');

// STEP 3.5: Wait a moment for the session to fully stabilize
appendLog('STEP 3.5: Waiting 2s for session to stabilize...');
await new Promise(r => setTimeout(r, 2000));
```

## Testing Instructions

1. **Launch the app** (already running on your device)
2. **Press "Play Sample Video"**
3. **Select your Chromecast** in the dialog
4. **Wait for the flow:**
   - Session connects
   - 2-second stabilization delay
   - Video loads
5. **Observe what plays on your TV**

## What to Check

### In the App UI:
- Event log shows: `STEP 3.5: Waiting 2s for session to stabilize...`
- Then shows: `STEP 4: Sending loadMedia request...`
- Finally shows: `✅ SUCCESS: Video is now playing on Chromecast`

### On the TV:
- Should see the Big Buck Bunny video
- Title: "Big Buck Bunny"
- Subtitle: "Blender Foundation"
- Thumbnail image should appear

### In Logcat (if we check):
```
📥 loadMedia called with URL: https://commondatastorage.googleapis.com/.../BigBuckBunny.mp4
📥 Extracted - title: Big Buck Bunny, subtitle: Blender Foundation, contentType: video/mp4
📹 Building MediaInfo: URL=...BigBuckBunny.mp4, contentType=video/mp4
✅ MediaInfo built successfully
Sending media load request: URL=...
Media load success
```

## Known Receiver IDs

| ID | Name | Best For |
|----|------|----------|
| `CC1AD845` | Default Media Receiver | ✅ **Most compatible** - use this |
| `C0868879` | Styled Media Receiver | Nice UI but can have issues |
| `[Custom]` | Your Custom Receiver | Production apps with custom needs |

## Troubleshooting

If you still see a different video:
1. The Chromecast may be resuming a previous session
2. Try power-cycling your Chromecast
3. Check if there's an existing app running on the Chromecast
4. We may need to explicitly stop any existing media before loading new media

## Next Steps

After testing:
- Report what video you see on the TV
- Check if title/subtitle appear
- Share the event log from the app UI
- We'll analyze the native logs if needed

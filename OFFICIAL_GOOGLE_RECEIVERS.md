# Google Cast Official Receiver Application IDs

## Official Google Receivers for Development and Testing

### 1. Default Media Receiver (Production)
- **App ID**: `CC1AD845`
- **Purpose**: Production use
- **Limitations**: Only accepts media from registered domains or specific formats
- **Use Case**: Final production apps

### 2. Styled Media Receiver (Production)
- **App ID**: `C0868879`
- **Purpose**: Production with better UI
- **Limitations**: Similar to Default Media Receiver
- **Use Case**: Production apps with enhanced UI

### 3. Debug/Development Receivers

Google doesn't provide a public "debug" receiver ID. The issue is that **ALL receiver IDs require domain whitelisting or specific configuration** in the Google Cast Developer Console.

## The Real Solution

**You MUST register your own receiver application** at https://cast.google.com/publish/

### Why?
- Google deprecated most public/demo receiver IDs
- Security: Google doesn't allow arbitrary URLs to be cast without registration
- Control: You need to whitelist your media domains

### How to Register (5 minutes, $5 one-time fee):

1. **Go to**: https://cast.google.com/publish/
2. **Pay $5 USD** (one-time, lifetime registration)
3. **Create New Application**
4. **Choose**: Custom Receiver
5. **Receiver URL**: Use Google's hosted receiver:
   ```
   https://www.gstatic.com/cast/sdk/libs/caf_receiver/v3/cast_receiver.html
   ```
6. **Get your App ID**: Format `XXXXXXXX`
7. **Add your Chromecast as test device**:
   - Find your Chromecast serial number (in Chromecast settings)
   - Add it in the Developer Console
   - **Reboot your Chromecast** (power cycle)
8. **Wait 5-15 minutes** for propagation

### Alternative: Use Google's CAF Receiver (Most Compatible)

```javascript
receiverApplicationId: 'CC1AD845' // Default Media Receiver
```

**But you need to use specific Google sample videos that are whitelisted:**

```javascript
// These URLs are guaranteed to work with CC1AD845:
const GOOGLE_SAMPLE_VIDEOS = {
  bunny: 'http://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4',
  sintel: 'http://commondatastorage.googleapis.com/gtv-videos-bucket/sample/Sintel.mp4',
  tears: 'http://commondatastorage.googleapis.com/gtv-videos-bucket/sample/TearsOfSteel.mp4',
  elephant: 'http://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4'
};
```

**Important**: Use `http://` not `https://` for these Google samples.

## Current Situation

With public receiver IDs like `CC1AD845` or `4F8B3483`:
- ✅ Session connects (you see Cast logo)
- ❌ Media doesn't load (security restrictions)
- 💡 **Solution**: Register your own receiver

## Recommended Next Steps

### Option A: Quick Test (Use Whitelisted URLs)
Keep using `CC1AD845` but ONLY with Google's sample videos listed above.

### Option B: Production Ready (Register Your Receiver)
1. Pay $5 at https://cast.google.com/publish/
2. Create Custom Receiver
3. Add your Chromecast as test device
4. Use your own App ID

### Option C: Use Existing App ID (If You Have Access)
If you or someone on your team already has a registered receiver, use that App ID.

## Why Your Current Setup Shows Only Cast Logo

The Cast logo appears because:
1. ✅ SDK initialized correctly
2. ✅ Session established correctly
3. ✅ Your plugin code works correctly
4. ❌ **But the receiver refuses to load the video due to security policies**

The receiver IDs like `CC1AD845` and `4F8B3483` have strict policies:
- Only accept specific whitelisted media URLs
- Or only work with registered developer accounts
- This is by design for security

## Summary

**There is NO free public receiver ID that accepts arbitrary video URLs.**

Your options:
1. ✅ **Register your own** ($5, permanent solution)
2. ⚠️ **Use only Google sample videos** (temporary workaround)
3. ❌ Keep trying random App IDs (won't work)

**Bottom line**: For a production app or even serious testing, you need to register your own receiver application. It's quick, cheap, and the only reliable solution.

## Created with Capacitor Create App

This app was created using [`@capacitor/create-app`](https://github.com/ionic-team/create-capacitor-app),
and comes with a very minimal shell for building an app.

### Running this example

To run the provided example, you can use `npm start` command.

```bash
npm start
```

### Fire TV manual check

1. After syncing and installing the Android example app, connect the phone and Fire TV Stick to the same Wi-Fi network. If the receiver stopped responding in a previous test, restart the Stick before testing again.
2. Open the example app and tap **Check Devices**. The diagnostic message should include `Fire TV discovery: 1 built-in receiver(s)` or more.
3. Tap **Select playback device**. Choose the entry ending in `(Amazon Fire TV)`; the session should report `transport: fire-tv`.
4. Tap **Play Sample Video**. The phone now relays the MP4 over Wi-Fi with byte-range support. Confirm on the Fire TV screen that the public MP4 starts playing; a successful `loadMedia()` response alone does not prove playback. If this first load fails, stop here and record the full error, including `MP4 relay`.
5. Tap **Play HLS Test** with the Apple URL, then with the R2 URL. When an HLS origin serves its playlists as generic binary data, the Fire TV transport starts a local HLS relay on the phone that supplies HLS media types. Wait for the load result without tapping again; the plugin can retry the same HLS URL once if the relay received no request. Check both the app result and the TV. If the app warns while the TV plays, record the complete warning, including `playback check`, `HLS retry attempted`, and `HLS relay`, and continue the switching test. If the TV does not play, stop and record the same details.
6. Tap **Play Sample Video** to switch back from HLS. While it plays, tap **Play Long MP4 Test** once, then **Play Sample Video** once. Confirm each MP4 replaces the previous one without opening the device selector or tapping twice. Repeat both directions once. Then test **Play HLS Test → Play Long MP4 Test → Play HLS Test** using the same HLS URL. If any switch fails, copy the full message, including `MP4 relay` or `HLS relay`, and note what appeared on the TV.
7. Tap **Stop Playback Session** and confirm playback ends. You can then select Samsung DLNA or Google Cast to check that those paths still work.

The Fire TV integration uses Amazon's built-in Fling receiver. A Fling error or timeout can occur even when playback starts on the TV. Relay bytes show that the Stick requested media, not that it decoded or displayed the video. Manual results from one Stick do not establish compatibility with other models.

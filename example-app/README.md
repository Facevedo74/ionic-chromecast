## Created with Capacitor Create App

This app was created using [`@capacitor/create-app`](https://github.com/ionic-team/create-capacitor-app),
and comes with a very minimal shell for building an app.

### Running this example

To run the provided example, you can use `npm start` command.

```bash
npm start
```

### Fire TV manual check

1. After syncing and installing the Android example app, connect the phone and Fire TV Stick to the same Wi-Fi network.
2. Open the example app and tap **Check Devices**. The diagnostic message should include `Fire TV discovery: 1 built-in receiver(s)` or more.
3. Tap **Select playback device**. Choose the entry ending in `(Amazon Fire TV)`; the session should report `transport: fire-tv`.
4. Tap **Play Sample Video**. Confirm on the Fire TV screen that the public MP4 starts playing. A successful `loadMedia()` response alone confirms only that the receiver accepted the request.
5. While it plays, tap **Play Long MP4 Test**. The second video should replace the first without opening the device selector again.
6. Tap **Stop Cast Session** and confirm playback ends. You can then select Samsung DLNA or Google Cast to check that those paths still work.

The Fire TV integration uses Amazon's built-in Fling receiver. Amazon ended standard support for Fling in March 2026, so the on-device result is essential before claiming compatibility.

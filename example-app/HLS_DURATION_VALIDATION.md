# HLS duration and seeking on Hisense VIDAA

Validated on 2026-10-20 using this repository's `example-app` (`com.example.plugin`).

## Result

The user confirmed on the TV itself that the HLS test shows approximately **00:23:40** and that seeking with the TV remote continues playback.

The test source is the episode 03 `master.m3u8` prefilled in the example. Its 1080p variant has 84 MPEG-TS segments, totaling 217,312,020 bytes. The example supplies `metadata.duration = 1420.002345` seconds.

## Change

The Android HLS relay previously concatenated segments using chunked transfer, without a known total size or byte-range support. Supplying duration in DIDL metadata alone did not enable the TV's duration display.

The relay now indexes segment sizes with bounded concurrent HEAD requests, supplies Content-Length and Accept-Ranges, and serves exact ranges across segment boundaries. DIDL advertises the resource size and byte-seek capability only when indexing succeeds. Segments are fetched as needed, without downloading or converting the whole episode first. If indexing fails or exceeds its deadline, the previous sequential relay remains available.

The existing complete-VOD, unencrypted MPEG-TS restrictions still apply. This validation does not cover live streams, encrypted HLS, fMP4 segments, or other TVs.

## Reproduce

1. Build the plugin: `npm run build` from the repository root.
2. In `example-app`, run `npm run build` and `npx cap sync android`.
3. Build and install the example APK on an Android phone on the TV's Wi-Fi network.
4. Tap **Play HLS Test**, select the Hisense, and wait for playback to load.
5. Check the total duration on the TV and seek with its remote.

The diagnostic button reads the actual UPnP response. On this Hisense, `TrackDuration` still reports `00:00:00` even when the TV's native player shows **00:23:40** and seeks successfully. Therefore the UPnP field alone is not a reliable pass/fail indicator for its on-screen duration.

## Automated checks

Run `python3 scripts/test-dlna-hls-relay.py` from the repository root (Python 3 and JDK 17+ required). It compiles the real relay with minimal Android stubs and exercises it against a local HTTP origin: complete content, HEAD size, cross-segment ranges, suffix/open-ended ranges, invalid ranges, an origin ignoring Range, and the sequential fallback when HEAD sizes are unavailable. All 18 checks passed.

Plugin TypeScript/Rollup and the example Android debug build also completed successfully. These changes have not been published to npm.

# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.0.19] - 2026-10-25

### Changed
- Restore sequential HLS delivery without duration hints for Samsung DLNA renderers; this path was confirmed to play on a Samsung BU8000.
- Require Samsung MP4 playback confirmation from both the receiver state and relay media bytes, and report relay origin details when loading fails.
- Use the supplied AnimeSlime MP4 URL for the example's long-video test without a hardcoded duration. Playback of this new test URL still requires device validation.

## [0.0.18] - 2026-10-24

### Documentation
- Describe the current Google Cast, DLNA/UPnP, and Fire TV transports, including session reuse and local MP4/HLS relays on Android.
- Add a staged Fire TV manual test for first playback and switching between MP4 and HLS.
- Record the observed Fling limitation: a media load can report an error or timeout while the TV plays the video. Relay activity confirms a media request, not playback.

### Package metadata
- Update the package description and keywords for the supported Android transports.

## [0.0.14] - 2026-10-19

### Added
- Emit `deviceChooserShown` when the Google Cast or DLNA device picker is visible so apps can dismiss loading overlays.

## [0.0.13] - 2026-10-19

### Fixed
- Follow the highest-bandwidth variant in HLS master playlists and allow discontinuity markers in compatible MPEG-TS VOD streams.
- Report unsupported HLS playlist features with a specific error.

## [0.0.12] - 2026-10-19

### Fixed
- Relay compatible HLS VOD playlists over local HTTP for all DLNA renderers instead of sending `.m3u8` URLs directly to the TV.

## [0.0.7] - 2026-01-04

### Changed
- Android Cast metadata now uses `MEDIA_TYPE_GENERIC` so subtitles display correctly alongside title and cover art on receiver HUDs.

## [0.0.4] - 2026-01-04

### Changed
- Fixed media load flow to dispatch on main thread and avoid deadlocks, with clearer errors/status codes.
- Improved Cast session/device handling in the Android implementation.
- Example app now defaults to the CastVideos CAF receiver (`4F8B3483`) for better TV-side UI visibility.

### Added
- Troubleshooting guidance in README for receiver selection.

## [0.0.3] - 2025-12-12

### Changed
- Internal stability improvements for Cast initialization and device discovery.

## [0.0.2] - 2025-10-18

### Added
- Comprehensive documentation with 4 complete usage examples
- Example 1: Basic Cast Integration
- Example 2: Video Player with Cast
- Example 3: Advanced Cast Controls with event listeners
- Example 4: Cast Button Component
- Detailed API documentation for all endpoints
- Quick start guide
- Best practices section

### Changed
- Improved README with step-by-step examples
- Enhanced feature list with current capabilities
- Better organization of documentation

## [0.0.1] - 2025-10-18

### Added
- Initial release of ionic-chromecast plugin
- `initialize()` method for Google Cast SDK initialization
- `requestSession()` method to request Cast sessions
- `isSessionActive()` method to check session status
- `areDevicesAvailable()` method to detect Chromecast devices
- `loadMedia()` method to cast videos with rich metadata
- `addListener()` method for event handling (TypeScript definition)
- Android support with Google Cast Framework
- TypeScript definitions
- Example application for testing
- Comprehensive documentation

### Supported Platforms
- Android (API 23+)

### Coming Soon
- iOS support
- Native event listeners implementation
- Session management (end session)
- Media playback controls (play, pause, stop, seek)
- Volume controls
- Queue management

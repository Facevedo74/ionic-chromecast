# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

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

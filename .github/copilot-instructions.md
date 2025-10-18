<!-- Use this file to provide workspace-specific custom instructions to Copilot. For more details, visit https://code.visualstudio.com/docs/copilot/copilot-customization#_use-a-githubcopilotinstructionsmd-file -->

# Ionic Chromecast Plugin

This is a Capacitor plugin for integrating Google Cast SDK (Chromecast) with Ionic/Capacitor applications.

## Project Structure

- `src/` - TypeScript definitions and web implementation
- `android/` - Android native implementation using Google Cast SDK
- `ios/` - iOS native implementation (future)
- `example-app/` - Test application for development

## Development Guidelines

### Android Implementation

- Use Google Cast SDK for Android (play-services-cast-framework)
- Follow Capacitor plugin patterns for method implementation
- All Cast-related methods should check if the SDK is initialized
- Use proper error handling and logging

### TypeScript/JavaScript

- Export all interfaces and types from `definitions.ts`
- Maintain web fallback implementation in `web.ts`
- Follow Capacitor plugin documentation patterns

### Code Style

- Use proper JSDoc comments for all public methods
- Follow Java naming conventions for Android code
- Use TypeScript best practices for web code
- Keep methods simple and focused on single responsibility

## Current Features

- **initialize()** - Initializes the Google Cast SDK with a receiver application ID

## Next Steps

Potential features to implement:
- Start casting session
- Load media
- Play/pause controls
- Volume control
- Session state management
- Device discovery

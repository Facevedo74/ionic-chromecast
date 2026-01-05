# ionic-chromecast

A Capacitor plugin for integrating Google Cast SDK (Chromecast) with Ionic/Capacitor applications.

## Features

- ✅ Initialize Google Cast SDK
- ✅ Session management (request, check status)
- ✅ Device discovery
- ✅ Media playback with rich metadata
- ✅ End active Cast session from app
- ✅ Android support
- ✅ Event listeners
- 🚧 iOS support (coming soon)

## Install

```bash
npm install ionic-chromecast
npx cap sync
```

## Android Configuration

The plugin automatically configures the necessary permissions and Cast options:
- `INTERNET`
- `ACCESS_NETWORK_STATE`
- `ACCESS_WIFI_STATE`

### Requirements
- Android API 23+
- Google Play Services
- Chromecast device on the same WiFi network

## Quick Start

### 1. Initialize the Cast SDK

```typescript
import { IonicChromecast } from 'ionic-chromecast';

// In your app.component.ts or main initialization
async initializeCast() {
  const result = await IonicChromecast.initialize({
    // Prefer CastVideos CAF receiver for reliable UI on TV. Use 'CC1AD845' if you need the default.
    receiverApplicationId: '4F8B3483'
  });
  
  if (result.success) {
    console.log('✅ Cast SDK ready!');
  }
}
```

### 2. Check for Available Devices

```typescript
async checkDevices() {
  const { available } = await IonicChromecast.areDevicesAvailable();
  
  if (available) {
    console.log('📡 Chromecast devices found!');
  } else {
    console.log('❌ No devices available');
  }
}
```

### 3. Start a Cast Session

```typescript
async startCasting() {
  const result = await IonicChromecast.requestSession();
  
  if (result.success) {
    console.log('🎬 Cast session started!');
  }
}
```

### 4. Load Media

```typescript
async playVideo() {
  const result = await IonicChromecast.loadMedia({
    url: 'https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4',
    metadata: {
      title: 'Big Buck Bunny',
      subtitle: 'Blender Foundation',
      images: ['https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/images/BigBuckBunny.jpg'],
      contentType: 'video/mp4',
      duration: 596
    }
  });
  
  if (result.success) {
    console.log('▶️ Video is playing on TV!');
  }
}

// Optional: end the current Cast session from the app
async stopCasting() {
  const result = await IonicChromecast.endSession();
  if (result.success) {
    console.log('⏹ Cast session ended');
  }
}
```

## Complete Examples

### Example 1: Basic Cast Integration

```typescript
import { Component, OnInit } from '@angular/core';
import { IonicChromecast } from 'ionic-chromecast';

@Component({
  selector: 'app-home',
  templateUrl: 'home.page.html',
})
export class HomePage implements OnInit {
  
  castAvailable = false;
  sessionActive = false;
  
  async ngOnInit() {
    // Initialize Cast SDK
    await IonicChromecast.initialize({
      receiverApplicationId: 'CC1AD845'
    });
    
    // Check for devices
    const { available } = await IonicChromecast.areDevicesAvailable();
    this.castAvailable = available;
  }
  
  async connectToTV() {
    const result = await IonicChromecast.requestSession();
    if (result.success) {
      this.sessionActive = true;
    }
  }
  
  async checkSession() {
    const { active } = await IonicChromecast.isSessionActive();
    this.sessionActive = active;
    return active;
  }
}
```

### Example 2: Video Player with Cast

```typescript
import { Component } from '@angular/core';
import { IonicChromecast } from 'ionic-chromecast';

@Component({
  selector: 'app-player',
  templateUrl: 'player.page.html',
})
export class PlayerPage {
  
  async castVideo(videoUrl: string, videoTitle: string, posterUrl: string) {
    // Check if session is active
    const { active } = await IonicChromecast.isSessionActive();
    
    if (!active) {
      // Request session first
      await IonicChromecast.requestSession();
    }
    
    // Load the video
    const result = await IonicChromecast.loadMedia({
      url: videoUrl,
      metadata: {
        title: videoTitle,
        subtitle: 'Your App Name',
        images: [posterUrl],
        contentType: 'video/mp4'
      }
    });
    
    if (result.success) {
      console.log('🎥 Now casting:', videoTitle);
    }
  }
  
  async castFromLibrary() {
    const videos = [
      {
        title: 'Big Buck Bunny',
        url: 'https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4',
        poster: 'https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/images/BigBuckBunny.jpg'
      },
      {
        title: 'Elephants Dream',
        url: 'https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4',
        poster: 'https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/images/ElephantsDream.jpg'
      }
    ];
    
    // Cast first video
    await this.castVideo(videos[0].url, videos[0].title, videos[0].poster);
  }
}
```

### Example 3: Advanced Cast Controls

```typescript
import { Component, OnDestroy } from '@angular/core';
import { IonicChromecast } from 'ionic-chromecast';

@Component({
  selector: 'app-cast-control',
  templateUrl: 'cast-control.page.html',
})
export class CastControlPage implements OnDestroy {
  
  private eventListeners: any[] = [];
  
  async ngOnInit() {
    // Initialize
    await IonicChromecast.initialize({
      receiverApplicationId: 'CC1AD845'
    });
    
    // Listen to events
    this.setupEventListeners();
  }
  
  async setupEventListeners() {
    const sessionHandle = await IonicChromecast.addListener('sessionStarted', (event) => {
      console.log('✅ Session started:', event);
    });
    
    const mediaHandle = await IonicChromecast.addListener('mediaLoaded', (event) => {
      console.log('🎬 Media loaded:', event);
    });
    
    this.eventListeners.push(sessionHandle, mediaHandle);
  }
  
  async fullCastWorkflow() {
    // 1. Check for devices
    const devicesResult = await IonicChromecast.areDevicesAvailable();
    if (!devicesResult.available) {
      alert('No Chromecast devices found. Make sure you are on the same WiFi network.');
      return;
    }
    
    // 2. Request session
    const sessionResult = await IonicChromecast.requestSession();
    if (!sessionResult.success) {
      alert('Failed to connect to Chromecast');
      return;
    }
    
    // 3. Wait a moment for session to establish
    await new Promise(resolve => setTimeout(resolve, 1000));
    
    // 4. Load media
    const mediaResult = await IonicChromecast.loadMedia({
      url: 'https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4',
      metadata: {
        title: 'Big Buck Bunny',
        subtitle: 'A Blender Open Movie',
        studio: 'Blender Foundation',
        images: [
          'https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/images/BigBuckBunny.jpg'
        ],
        contentType: 'video/mp4',
        duration: 596 // seconds
      }
    });
    
    if (mediaResult.success) {
      console.log('🎉 Successfully casting video!');
    }
  }
  
  ngOnDestroy() {
    // Clean up listeners
    this.eventListeners.forEach(handle => handle.remove());
  }

  async stopCast() {
    await IonicChromecast.endSession();
  }
}
```

### Example 4: Cast Button Component

```typescript
// cast-button.component.ts
import { Component, OnInit } from '@angular/core';
import { IonicChromecast } from 'ionic-chromecast';

@Component({
  selector: 'app-cast-button',
  template: `
    <ion-button 
      *ngIf="devicesAvailable" 
      (click)="toggleCast()"
      [color]="sessionActive ? 'primary' : 'medium'">
      <ion-icon [name]="sessionActive ? 'wifi' : 'wifi-outline'"></ion-icon>
      {{ sessionActive ? 'Casting' : 'Cast' }}
    </ion-button>
  `
})
export class CastButtonComponent implements OnInit {
  
  devicesAvailable = false;
  sessionActive = false;
  
  async ngOnInit() {
    await this.checkDevices();
    await this.checkSession();
    
    // Check periodically
    setInterval(() => {
      this.checkDevices();
      this.checkSession();
    }, 5000);
  }
  
  async checkDevices() {
    const result = await IonicChromecast.areDevicesAvailable();
    this.devicesAvailable = result.available;
  }
  
  async checkSession() {
    const result = await IonicChromecast.isSessionActive();
    this.sessionActive = result.active;
  }
  
  async toggleCast() {
    if (this.sessionActive) {
      // Session is active - could implement endSession() here
      console.log('Session is active');
    } else {
      // Request new session
      await IonicChromecast.requestSession();
    }
  }
}
```

## API

<docgen-index>

* [`initialize(...)`](#initialize)
* [`echo(...)`](#echo)
* [`requestSession()`](#requestsession)
* [`isSessionActive()`](#issessionactive)
* [`areDevicesAvailable()`](#aredevicesavailable)
* [`loadMedia(...)`](#loadmedia)
* [`endSession()`](#endsession)
* [`addListener(ChromecastEventType, ...)`](#addlistenerchromecasteventtype-)
* [Interfaces](#interfaces)
* [Type Aliases](#type-aliases)

</docgen-index>

<docgen-api>
<!--Update the source file JSDoc comments and rerun docgen to update the docs below-->

### initialize(...)

```typescript
initialize(options: InitializeOptions) => Promise<{ success: boolean; }>
```

Initialize the Google Cast SDK
Must be called before any other Cast operations

| Param         | Type                                                            |
| ------------- | --------------------------------------------------------------- |
| **`options`** | <code><a href="#initializeoptions">InitializeOptions</a></code> |

**Returns:** <code>Promise&lt;{ success: boolean; }&gt;</code>

--------------------


### echo(...)

```typescript
echo(options: { value: string; }) => Promise<{ value: string; }>
```

| Param         | Type                            |
| ------------- | ------------------------------- |
| **`options`** | <code>{ value: string; }</code> |

**Returns:** <code>Promise&lt;{ value: string; }&gt;</code>

--------------------


### requestSession()

```typescript
requestSession() => Promise<RequestSessionResult>
```

Request a Cast session (Android only)

**Returns:** <code>Promise&lt;<a href="#requestsessionresult">RequestSessionResult</a>&gt;</code>

--------------------


### isSessionActive()

```typescript
isSessionActive() => Promise<SessionStatusResult>
```

Check if there is an active Cast session (Android only)

**Returns:** <code>Promise&lt;<a href="#sessionstatusresult">SessionStatusResult</a>&gt;</code>

--------------------


### areDevicesAvailable()

```typescript
areDevicesAvailable() => Promise<DevicesAvailableResult>
```

Check if there are available Cast devices (Android only)

**Returns:** <code>Promise&lt;<a href="#devicesavailableresult">DevicesAvailableResult</a>&gt;</code>

--------------------


### loadMedia(...)

```typescript
loadMedia(options: LoadMediaOptions) => Promise<{ success: boolean; message?: string; }>
```

Load media on the Cast device (Android only)

| Param         | Type                                                          |
| ------------- | ------------------------------------------------------------- |
| **`options`** | <code><a href="#loadmediaoptions">LoadMediaOptions</a></code> |

**Returns:** <code>Promise&lt;{ success: boolean; message?: string; }&gt;</code>

--------------------


### endSession()

```typescript
endSession() => Promise<{ success: boolean; message?: string; }>
```

End the current Cast session (Android only)

**Returns:** <code>Promise&lt;{ success: boolean; message?: string; }&gt;</code>

--------------------


### addListener(ChromecastEventType, ...)

```typescript
addListener(eventName: ChromecastEventType, listenerFunc: (event: ChromecastEvent) => void) => PluginListenerHandle
```

Listen to Chromecast events (Android only)

| Param              | Type                                                                            |
| ------------------ | ------------------------------------------------------------------------------- |
| **`eventName`**    | <code><a href="#chromecasteventtype">ChromecastEventType</a></code>             |
| **`listenerFunc`** | <code>(event: <a href="#chromecastevent">ChromecastEvent</a>) =&gt; void</code> |

**Returns:** <code><a href="#pluginlistenerhandle">PluginListenerHandle</a></code>

--------------------


### Interfaces


#### InitializeOptions

| Prop                        | Type                | Description                                                                               |
| --------------------------- | ------------------- | ----------------------------------------------------------------------------------------- |
| **`receiverApplicationId`** | <code>string</code> | The receiver application ID for Google Cast Use "CC1AD845" for the default media receiver |


#### RequestSessionResult

| Prop          | Type                 |
| ------------- | -------------------- |
| **`success`** | <code>boolean</code> |
| **`message`** | <code>string</code>  |


#### SessionStatusResult

| Prop          | Type                 |
| ------------- | -------------------- |
| **`active`**  | <code>boolean</code> |
| **`message`** | <code>string</code>  |


#### DevicesAvailableResult

| Prop            | Type                 |
| --------------- | -------------------- |
| **`available`** | <code>boolean</code> |
| **`message`**   | <code>string</code>  |


#### LoadMediaOptions

| Prop           | Type                                                    |
| -------------- | ------------------------------------------------------- |
| **`url`**      | <code>string</code>                                     |
| **`metadata`** | <code><a href="#mediametadata">MediaMetadata</a></code> |


#### MediaMetadata

| Prop              | Type                  |
| ----------------- | --------------------- |
| **`title`**       | <code>string</code>   |
| **`subtitle`**    | <code>string</code>   |
| **`images`**      | <code>string[]</code> |
| **`studio`**      | <code>string</code>   |
| **`contentType`** | <code>string</code>   |
| **`duration`**    | <code>number</code>   |


#### PluginListenerHandle

| Prop         | Type                                      |
| ------------ | ----------------------------------------- |
| **`remove`** | <code>() =&gt; Promise&lt;void&gt;</code> |


#### ChromecastEvent

| Prop       | Type                                                                |
| ---------- | ------------------------------------------------------------------- |
| **`type`** | <code><a href="#chromecasteventtype">ChromecastEventType</a></code> |
| **`data`** | <code>any</code>                                                    |


### Type Aliases


#### ChromecastEventType

<code>'sessionStarted' | 'sessionEnded' | 'mediaLoaded' | 'mediaError' | 'deviceAvailable' | 'deviceUnavailable' | 'volumeChanged' | 'playbackStatusChanged'</code>

</docgen-api>

## Troubleshooting
- No Cast UI on TV after connecting: use the CastVideos CAF receiver ID `4F8B3483` instead of the default `CC1AD845` when calling `initialize`.
- Media returns success but nothing plays: confirm a session is active (`isSessionActive`), then retry `loadMedia` with a known-good HTTPS MP4 (e.g., BigBuckBunny).
- Devices not found: ensure the phone and Chromecast are on the same WiFi and Google Play Services is up to date.

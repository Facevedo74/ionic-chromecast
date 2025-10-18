# ionic-chromecast

A Capacitor plugin for integrating Google Cast SDK (Chromecast) with Ionic/Capacitor applications.

## Features

- ✅ Initialize Google Cast SDK
- ✅ Android support
- 🚧 iOS support (coming soon)
- 🚧 Session management
- 🚧 Media playback control

## Install

```bash
npm install ionic-chromecast
npx cap sync
```

## Android Configuration

The plugin automatically configures the necessary permissions and Cast options. However, you may want to customize the default receiver application ID.

### Default Receiver App ID

The plugin uses Google's default media receiver (`CC1AD845`) by default. You can override this by calling `initialize()` with your custom receiver application ID.

## Usage

### Initialize the Cast SDK

Before using any Cast functionality, you must initialize the SDK:

```typescript
import { IonicChromecast } from 'ionic-chromecast';

// Initialize with default media receiver
await IonicChromecast.initialize({
  receiverApplicationId: 'CC1AD845' // Default Media Receiver
});

// Or use your custom receiver app ID
await IonicChromecast.initialize({
  receiverApplicationId: 'YOUR_RECEIVER_APP_ID'
});
```

### Best Practices

- Call `initialize()` early in your app lifecycle (e.g., in `app.component.ts`)
- Initialize only once per app session
- Handle initialization errors appropriately

## Example

```typescript
import { Component, OnInit } from '@angular/core';
import { IonicChromecast } from 'ionic-chromecast';

@Component({
  selector: 'app-root',
  templateUrl: 'app.component.html',
})
export class AppComponent implements OnInit {
  
  async ngOnInit() {
    try {
      const result = await IonicChromecast.initialize({
        receiverApplicationId: 'CC1AD845'
      });
      
      if (result.success) {
        console.log('Cast SDK initialized successfully');
      }
    } catch (error) {
      console.error('Failed to initialize Cast SDK:', error);
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

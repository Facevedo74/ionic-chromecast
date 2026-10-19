export interface InitializeOptions {
  /**
   * The receiver application ID for Google Cast
   * Use "CC1AD845" for the default media receiver
   */
  receiverApplicationId: string;
}

export interface RequestSessionResult {
  success: boolean;
  cancelled?: boolean;
  message?: string;
  transport?: PlaybackTransport;
  deviceName?: string;
}

export interface SessionStatusResult {
  active: boolean;
  message?: string;
  transport?: PlaybackTransport;
  deviceName?: string;
}

export type PlaybackTransport = 'google-cast' | 'dlna';

export interface DevicesAvailableResult {
  available: boolean;
  message?: string;
}

export interface MediaMetadata {
  title?: string;
  subtitle?: string;
  images?: string[]; // URLs to images
  studio?: string;
  contentType?: string; // e.g. 'video/mp4'
  duration?: number; // in seconds
  [key: string]: any; // allow extra optional metadata
}

export interface LoadMediaOptions {
  url: string;
  metadata?: MediaMetadata;
}

export interface LoadMediaResult {
  success: boolean;
  message?: string;
  transport?: PlaybackTransport;
}

export interface PluginListenerHandle {
  remove: () => Promise<void>;
}

export type ChromecastEventType =
  | 'sessionStarted'
  | 'sessionEnded'
  | 'mediaLoaded'
  | 'mediaError'
  | 'deviceAvailable'
  | 'deviceUnavailable'
  | 'volumeChanged'
  | 'playbackStatusChanged';

export interface ChromecastEvent {
  type: ChromecastEventType;
  data?: any;
}

export interface IonicChromecastPlugin {
  /**
   * Initialize the Google Cast SDK
   * Must be called before any other Cast operations
   */
  initialize(options: InitializeOptions): Promise<{ success: boolean }>;

  echo(options: { value: string }): Promise<{ value: string }>;

  /**
   * Discover and select a Google Cast or DLNA/UPnP playback device (Android only).
   * Falls back directly to the native Google Cast chooser when no DLNA renderer is found.
   */
  requestSession(): Promise<RequestSessionResult>;

  /**
   * Open the native Google Cast chooser without scanning for DLNA devices (Android only).
   */
  requestCastSession(): Promise<RequestSessionResult>;

  /**
   * Check if there is an active Google Cast or selected DLNA session (Android only)
   */
  isSessionActive(): Promise<SessionStatusResult>;

  /**
   * Check if there are available Google Cast or DLNA/UPnP devices (Android only)
   */
  areDevicesAvailable(): Promise<DevicesAvailableResult>;

  /**
   * Load public HTTP/HTTPS media on the selected Google Cast or DLNA device (Android only)
   */
  loadMedia(options: LoadMediaOptions): Promise<LoadMediaResult>;

  /**
   * End the current Google Cast or DLNA session (Android only)
   */
  endSession(): Promise<{ success: boolean; message?: string }>;

  /**
   * Listen to Chromecast events (Android only)
   */
  addListener(
    eventName: ChromecastEventType,
    listenerFunc: (event: ChromecastEvent) => void
  ): PluginListenerHandle;
}

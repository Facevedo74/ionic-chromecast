export interface InitializeOptions {
  /**
   * The receiver application ID for Google Cast
   * Use "CC1AD845" for the default media receiver
   */
  receiverApplicationId: string;
}

export interface RequestSessionResult {
  success: boolean;
  message?: string;
}

export interface SessionStatusResult {
  active: boolean;
  message?: string;
}

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
   * Request a Cast session (Android only)
   */
  requestSession(): Promise<RequestSessionResult>;

  /**
   * Check if there is an active Cast session (Android only)
   */
  isSessionActive(): Promise<SessionStatusResult>;

  /**
   * Check if there are available Cast devices (Android only)
   */
  areDevicesAvailable(): Promise<DevicesAvailableResult>;

  /**
   * Load media on the Cast device (Android only)
   */
  loadMedia(options: LoadMediaOptions): Promise<{ success: boolean; message?: string }>;

  /**
   * End the current Cast session (Android only)
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

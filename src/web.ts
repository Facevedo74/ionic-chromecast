import { WebPlugin } from '@capacitor/core';

import type {
  InitializeOptions,
  IonicChromecastPlugin,
  ChromecastEventType,
  ChromecastEvent,
  PluginListenerHandle,
} from './definitions';

export class IonicChromecastWeb extends WebPlugin implements IonicChromecastPlugin {
  
  async initialize(options: InitializeOptions): Promise<{ success: boolean }> {
    console.log('Cast SDK initialize called on web with options:', options);
    console.warn('Google Cast SDK is not supported on web. This is a no-op implementation.');
    return { success: false };
  }

  async echo(options: { value: string }): Promise<{ value: string }> {
    console.log('ECHO', options);
    return options;
  }

  async requestSession(): Promise<{ success: boolean; message?: string }> {
    console.warn('requestSession() is not supported on web.');
    return { success: false, message: 'Google Cast session is only available on Android.' };
  }

  async isSessionActive(): Promise<{ active: boolean; message?: string }> {
    console.warn('isSessionActive() is not supported on web.');
    return { active: false, message: 'Session detection only available on Android.' };
  }

  async areDevicesAvailable(): Promise<{ available: boolean; message?: string }> {
    console.warn('areDevicesAvailable() is not supported on web.');
    return { available: false, message: 'Device detection only available on Android.' };
  }

  async loadMedia(_: { url: string; metadata?: any }): Promise<{ success: boolean; message?: string }> {
    console.warn('loadMedia() is not supported on web.');
    return { success: false, message: 'Media casting only available on Android.' };
  }

  async endSession(): Promise<{ success: boolean; message?: string }> {
    console.warn('endSession() is not supported on web.');
    return { success: false, message: 'Session control only available on Android.' };
  }
  addListener(
    _eventName: ChromecastEventType,
    _listenerFunc: (event: ChromecastEvent) => void
  ): Promise<PluginListenerHandle> & PluginListenerHandle {
    console.warn('addListener() is not supported on web.');
    const handle = {
      remove: async () => {
        /* no-op */
      }
    };
    return Object.assign(Promise.resolve(handle), handle);
  }
}

import { WebPlugin } from '@capacitor/core';

import type { IonicChromecastPlugin } from './definitions';

export class IonicChromecastWeb extends WebPlugin implements IonicChromecastPlugin {
  async echo(options: { value: string }): Promise<{ value: string }> {
    console.log('ECHO', options);
    return options;
  }
}

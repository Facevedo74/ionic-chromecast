import { registerPlugin } from '@capacitor/core';

import type { IonicChromecastPlugin } from './definitions';

const IonicChromecast = registerPlugin<IonicChromecastPlugin>('IonicChromecast', {
  web: () => import('./web').then((m) => new m.IonicChromecastWeb()),
});

export * from './definitions';
export { IonicChromecast };

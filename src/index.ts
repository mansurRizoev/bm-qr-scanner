import { registerPlugin } from '@capacitor/core';

import type { BmQrPlugin } from './definitions';

const BmQr = registerPlugin<BmQrPlugin>('BmQr', {
  web: () => import('./web').then((m) => new m.BmQrWeb()),
});

export * from './definitions';
export { BmQr };

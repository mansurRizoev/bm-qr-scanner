import { registerPlugin } from '@capacitor/core';

import type { BmQrPlugin } from './definitions';

const BmQr = registerPlugin<BmQrPlugin>('BmQr');

export * from './definitions';
export { BmQr };


import { registerPlugin } from '@capacitor/core';

import type { BmQrPlugin } from './definitions';

const BmQrScanner = registerPlugin<BmQrPlugin>('BmQrPlugin');

export * from './definitions';
export { BmQrScanner };


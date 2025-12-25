import { WebPlugin } from '@capacitor/core';

import type { BmQrPlugin } from './definitions';

export class BmQrWeb extends WebPlugin implements BmQrPlugin {
  async echo(options: { value: string }): Promise<{ value: string }> {
    console.log('ECHO', options);
    return options;
  }
}

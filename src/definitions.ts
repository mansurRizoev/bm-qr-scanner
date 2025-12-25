export interface BmQrPlugin {
  echo(options: { value: string, fromGallery: string }): Promise<{ result: string }>;
}

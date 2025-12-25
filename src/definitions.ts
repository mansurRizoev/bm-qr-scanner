export interface BmQrPlugin {
  echo(options: { value: string }): Promise<{ value: string }>;
}

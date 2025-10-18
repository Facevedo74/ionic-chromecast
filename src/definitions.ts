export interface IonicChromecastPlugin {
  echo(options: { value: string }): Promise<{ value: string }>;
}

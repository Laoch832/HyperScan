import type { CapacitorConfig } from '@capacitor/cli';
const config: CapacitorConfig = {
  appId: 'app.hyperscan.android', appName: 'HyperScan', webDir: 'dist',
  server: { hostname: 'localhost', androidScheme: 'https' },
};
export default config;

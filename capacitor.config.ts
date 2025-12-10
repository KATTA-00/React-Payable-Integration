import { CapacitorConfig } from '@capacitor/cli';

const config: CapacitorConfig = {
  appId: 'com.attune.pos',
  appName: 'Attune POS',
  webDir: 'build', 
  server: {
    // For local testing on WiFi: http://192.168.1.XX:3000
    // For production: https://app.your-pos-site.com
    url: 'http://192.168.8.147:3000', 
    cleartext: true
  },
  android: {
    // Allows loading HTTP/HTTPS content smoothly
    allowMixedContent: true
  }
};

export default config;
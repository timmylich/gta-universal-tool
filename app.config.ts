/**
 * Expo app config. The version comes from package.json; bump `versionCode` together with it.
 * No trackers. Needs "All files access" to edit game files in shared storage.
 */
import type { ConfigContext, ExpoConfig } from 'expo/config';

const { version } = require('./package.json') as { version: string };
const VERSION_CODE = 1;

export default ({ config }: ConfigContext): ExpoConfig => ({
  ...config,
  name: 'GTA Universal Tool',
  slug: 'gta-universal-tool',
  scheme: 'gtatool',
  version,
  orientation: 'portrait',
  icon: './assets/icon.png',
  userInterfaceStyle: 'automatic',
  android: {
    package: 'io.github.timmylich.gtatool',
    versionCode: VERSION_CODE,
    adaptiveIcon: {
      backgroundColor: '#E8291C',
      backgroundImage: './assets/android-icon-background.png',
      foregroundImage: './assets/android-icon-foreground.png',
      monochromeImage: './assets/android-icon-monochrome.png',
    },
    permissions: [
      'android.permission.MANAGE_EXTERNAL_STORAGE',
      'android.permission.READ_EXTERNAL_STORAGE',
      'android.permission.WRITE_EXTERNAL_STORAGE',
    ],
    blockedPermissions: ['android.permission.RECORD_AUDIO', 'android.permission.SYSTEM_ALERT_WINDOW', 'com.google.android.gms.permission.AD_ID'],
    predictiveBackGestureEnabled: false,
  },
  plugins: [
    'expo-router',
    './plugins/with-android-storage',
    './plugins/with-release-signing',
    'expo-localization',
    ['expo-audio', { microphonePermission: false }],
    ['expo-build-properties', { android: { minSdkVersion: 26, enableMinifyInReleaseBuilds: true, enableShrinkResourcesInReleaseBuilds: true } }],
    ['expo-splash-screen', { image: './assets/splash-icon.png', imageWidth: 160, backgroundColor: '#121212' }],
  ],
  experiments: { typedRoutes: false },
});

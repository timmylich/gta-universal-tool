/**
 * Manifest tweaks Expo has no option for:
 * - largeHeap: decoding big PC textures (2048² × several) needs more than the default heap on some phones;
 * - requestLegacyExternalStorage: Android 10 file access to the game folder.
 */
const { withAndroidManifest } = require('expo/config-plugins');

module.exports = (config) =>
  withAndroidManifest(config, (c) => {
    const app = c.modResults.manifest.application[0];
    app.$['android:largeHeap'] = 'true';
    app.$['android:requestLegacyExternalStorage'] = 'true';
    return c;
  });

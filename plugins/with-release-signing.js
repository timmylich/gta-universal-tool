/**
 * Signs release builds with the key from ../keystore.properties (gitignored) when that file exists;
 * otherwise release builds stay debug-signed as in the Expo template (CI re-signs with repository secrets).
 */
const { withAppBuildGradle } = require('expo/config-plugins');

const RELEASE = `
        release {
            def props = new Properties()
            def file = rootProject.file('../keystore.properties')
            if (file.exists()) {
                file.withInputStream { props.load(it) }
                storeFile rootProject.file('../' + props['storeFile'])
                storePassword props['storePassword']
                keyAlias props['keyAlias']
                keyPassword props['keyPassword']
            }
        }`;

module.exports = (config) =>
  withAppBuildGradle(config, (c) => {
    let g = c.modResults.contents;
    if (g.includes("keystore.properties")) return c;
    g = g.replace(/signingConfigs \{/, `signingConfigs {${RELEASE}`);
    g = g.replace(/(buildTypes \{[\s\S]*?release \{[\s\S]*?)signingConfig signingConfigs\.debug/,
      "$1signingConfig rootProject.file('../keystore.properties').exists() ? signingConfigs.release : signingConfigs.debug");
    c.modResults.contents = g;
    return c;
  });

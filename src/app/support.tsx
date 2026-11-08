/** "Support the author": Tribute links, crypto addresses and other ways to help. */
import { Bug, Heart, Send, Star } from 'lucide-react-native';
import { Linking, ScrollView, View } from 'react-native';

import { DonationOptions } from '../components/Donation';
import { Button, T } from '../components/ui';
import { ISSUES_URL, REPO_URL, TELEGRAM_URL } from '../config';
import { useT } from '../i18n';
import { useTheme } from '../lib/theme';

export default function Support() {
  const c = useTheme();
  const L = useT();
  const open = (url: string) => Linking.openURL(url).catch(() => {});
  return (
    <ScrollView style={{ backgroundColor: c.bg }} contentContainerStyle={{ padding: 16, gap: 18, paddingBottom: 40 }}>
      <View style={{ alignItems: 'center', gap: 8, paddingVertical: 8 }}>
        <View style={{ width: 64, height: 64, borderRadius: 32, backgroundColor: c.accentSoft, alignItems: 'center', justifyContent: 'center' }}>
          <Heart size={30} color={c.accent} fill={c.accent} />
        </View>
        <T semi size={18} center>{L.donation.screenTitle}</T>
        <T dim center size={14}>{L.donation.screenText}</T>
      </View>
      <DonationOptions />
      <View style={{ gap: 8 }}>
        <T semi size={16}>{L.donation.otherWays}</T>
        <Button kind="ghost" icon={Star} title={L.donation.star} onPress={() => open(REPO_URL)} />
        <Button kind="ghost" icon={Send} title={L.donation.telegram} onPress={() => open(TELEGRAM_URL)} />
        <Button kind="ghost" icon={Bug} title={L.donation.report} onPress={() => open(ISSUES_URL)} />
      </View>
    </ScrollView>
  );
}

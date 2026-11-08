/**
 * Support the author: the one-time in-app prompt shown on install-age milestones
 * (lib/donation.ts) and the list of donation channels used on the /support page.
 */
import * as Clipboard from 'expo-clipboard';
import { router } from 'expo-router';
import { Check, Copy, Globe, Heart, Send, Star } from 'lucide-react-native';
import { useState } from 'react';
import { Linking, Modal, Pressable, View } from 'react-native';

import { REPO_URL, TELEGRAM_URL, TRIBUTE_TELEGRAM_URL, TRIBUTE_WEB_URL, WALLETS } from '../config';
import { useT } from '../i18n';
import { daysSinceInstall, pendingMilestone } from '../lib/donation';
import { setSettings, useSettings } from '../lib/store';
import { alpha, useTheme } from '../lib/theme';
import { Button, T } from './ui';

const open = (url: string) => Linking.openURL(url).catch(() => {});

export function DonationPrompt() {
  const c = useTheme();
  const L = useT();
  const createdAt = useSettings((s) => s.installedAt);
  const shown = useSettings((s) => s.donationShown);
  const milestone = pendingMilestone(daysSinceInstall(createdAt), shown);
  if (milestone == null) return null;
  const close = () => setSettings({ donationShown: [...shown, milestone] });
  const go = (fn: () => void) => {
    close();
    fn();
  };
  return (
    <Modal transparent animationType="fade" visible onRequestClose={close} statusBarTranslucent navigationBarTranslucent>
      <Pressable onPress={close} style={{ flex: 1, backgroundColor: '#000a', justifyContent: 'center', padding: 24 }}>
        <Pressable style={{ backgroundColor: c.card, borderRadius: 28, padding: 22, gap: 10 }}>
          <View style={{ alignSelf: 'center', width: 56, height: 56, borderRadius: 28, backgroundColor: c.accentSoft, alignItems: 'center', justifyContent: 'center' }}>
            <Heart size={28} color={c.accent} fill={c.accent} />
          </View>
          <T size={18} semi center>{L.donation.title(milestone)}</T>
          <T center dim size={14}>{L.donation.text}</T>
          <View style={{ height: 6 }} />
          <Button title={L.donation.money} icon={Heart} onPress={() => go(() => router.push('/support'))} />
          <Button title={L.donation.star} icon={Star} kind="soft" onPress={() => go(() => open(REPO_URL))} />
          <Button title={L.donation.telegram} icon={Send} kind="soft" onPress={() => go(() => open(TELEGRAM_URL))} />
          <Button title={L.donation.later} kind="ghost" onPress={close} />
        </Pressable>
      </Pressable>
    </Modal>
  );
}

/** Tribute links and tap-to-copy crypto addresses. */
export function DonationOptions() {
  const c = useTheme();
  const L = useT();
  const [copied, setCopied] = useState<string | null>(null);
  const copy = async (id: string, address: string) => {
    await Clipboard.setStringAsync(address);
    setCopied(id);
  };
  return (
    <View style={{ gap: 10 }}>
      <T semi size={16}>{L.donation.tribute}</T>
      <T dim size={13}>{L.donation.tributeHint}</T>
      <Button title={L.donation.tributeTelegram} icon={Send} onPress={() => open(TRIBUTE_TELEGRAM_URL)} />
      <Button title={L.donation.tributeWeb} icon={Globe} kind="soft" onPress={() => open(TRIBUTE_WEB_URL)} />
      <T semi size={16} style={{ marginTop: 10 }}>{L.donation.crypto}</T>
      {WALLETS.map((w) => {
        const done = copied === w.id;
        return (
          <Pressable key={w.id} onPress={() => copy(w.id, w.address)} android_ripple={{ color: alpha(c.text, 0.08), borderless: false }}
            accessibilityLabel={`${L.donation.wallets[w.id]}: ${L.donation.copy}`}
            style={{ borderRadius: 16, padding: 12, gap: 4, backgroundColor: c.cardAlt, borderWidth: 1, borderColor: done ? alpha(c.accent, 0.5) : 'transparent', overflow: 'hidden' }}>
            <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' }}>
              <T semi>{L.donation.wallets[w.id]}</T>
              {done ? <Check size={18} color={c.accent} /> : <Copy size={18} color={c.textDim} />}
            </View>
            <T dim size={12}>{w.networks}</T>
            <T size={12} style={{ fontFamily: 'monospace', color: done ? c.accent : c.text }}>{done ? L.donation.copied : w.address}</T>
          </Pressable>
        );
      })}
      <T dim size={11}>{L.donation.evmNote}</T>
    </View>
  );
}

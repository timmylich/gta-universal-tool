/**
 * App-wide overlays: dialogs (`alert`, `ask`) and the busy indicator (`run`). Mounted once in the root layout.
 * `run(fn)` blocks the UI with a spinner while native work runs and turns errors into a dialog.
 */
import { useEffect, useState } from 'react';
import { ActivityIndicator, Modal, Pressable, ScrollView, View } from 'react-native';

import { bytes, currentDict, useT } from '../i18n';
import { useTheme } from '../lib/theme';
import { Button, T } from './ui';

export type DialogButton = { text: string; style?: 'default' | 'cancel' | 'destructive'; onPress?: () => void };
type Dialog = { title: string; message?: string; buttons?: DialogButton[] };

let showDialog: ((d: Dialog) => void) | null = null;
let setBusy: ((v: string | null) => void) | null = null;
const queue: Dialog[] = [];

export function alert(title: string, message?: string, buttons?: DialogButton[]) {
  const d = { title, message, buttons };
  if (showDialog) showDialog(d);
  else queue.push(d);
}

/** Yes/no question; resolves true on the confirm button. */
export function ask(title: string, message: string, confirm: string, destructive = false): Promise<boolean> {
  const L = currentDict();
  return new Promise((res) => alert(title, message, [
    { text: L.common.cancel, style: 'cancel', onPress: () => res(false) },
    { text: confirm, style: destructive ? 'destructive' : 'default', onPress: () => res(true) },
  ]));
}

export async function run<T>(fn: () => Promise<T>, label?: string): Promise<T | undefined> {
  setBusy?.(label ?? currentDict().common.working);
  try {
    return await fn();
  } catch (e) {
    const msg = e instanceof Error ? e.message : String(e);
    const space = /NO_SPACE:(\d+)/.exec(msg);
    alert(currentDict().common.error, space ? currentDict().common.noSpace(bytes(Number(space[1]))) : msg);
    return undefined;
  } finally {
    setBusy?.(null);
  }
}

export function Host() {
  const c = useTheme();
  const L = useT();
  const [d, setD] = useState<Dialog | null>(() => queue.shift() ?? null);
  const [busy, setB] = useState<string | null>(null);
  useEffect(() => {
    // A dialog opened while another is shown waits in the queue.
    showDialog = (x) => setD((cur) => {
      if (!cur) return x;
      queue.push(x);
      return cur;
    });
    setBusy = setB;
    return () => {
      showDialog = null;
      setBusy = null;
    };
  }, []);
  const buttons = d?.buttons?.length ? d.buttons : [{ text: L.common.ok }];
  const press = (b?: DialogButton) => {
    const next = queue.shift() ?? null;
    setD(next);
    b?.onPress?.();
  };
  return (
    <>
      <Modal transparent visible={!!d} animationType="fade" onRequestClose={() => press(buttons.find((b) => b.style === 'cancel'))} statusBarTranslucent navigationBarTranslucent>
        <Pressable onPress={() => press(buttons.find((b) => b.style === 'cancel'))} style={{ flex: 1, backgroundColor: '#000a', justifyContent: 'center', padding: 22 }}>
          <Pressable style={{ backgroundColor: c.card, borderRadius: 24, padding: 20, gap: 10, maxHeight: '80%' }}>
            <T semi size={18}>{d?.title}</T>
            {d?.message ? <ScrollView style={{ flexGrow: 0 }}><T dim size={14.5}>{d.message}</T></ScrollView> : null}
            <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: 10, marginTop: 10 }}>
              {buttons.map((b, i) => (
                <Button key={i} flex title={b.text} onPress={() => press(b)} kind={b.style === 'cancel' ? 'ghost' : b.style === 'destructive' ? 'danger' : 'primary'} />
              ))}
            </View>
          </Pressable>
        </Pressable>
      </Modal>
      <Modal transparent visible={!!busy} animationType="fade" onRequestClose={() => {}} statusBarTranslucent navigationBarTranslucent>
        <View style={{ flex: 1, backgroundColor: '#0008', alignItems: 'center', justifyContent: 'center' }}>
          <View style={{ backgroundColor: c.card, borderRadius: 20, padding: 24, alignItems: 'center', gap: 14, minWidth: 180 }}>
            <ActivityIndicator color={c.accent} size="large" />
            <T>{busy}</T>
          </View>
        </View>
      </Modal>
    </>
  );
}

/** Shared UI primitives: theme-aware, native ripple, no JS animations on the hot path. */
import type { LucideIcon } from 'lucide-react-native';
import type { ReactNode } from 'react';
import { ActivityIndicator, Pressable, Switch, Text, TextInput, View, type StyleProp, type TextStyle, type ViewStyle } from 'react-native';

import { alpha, useTheme } from '../lib/theme';

export function T({ children, size = 15, dim, semi, bold, center, mono, color, numberOfLines, style }: {
  children: ReactNode; size?: number; dim?: boolean; semi?: boolean; bold?: boolean; center?: boolean; mono?: boolean; color?: string;
  numberOfLines?: number; style?: StyleProp<TextStyle>;
}) {
  const c = useTheme();
  return (
    <Text numberOfLines={numberOfLines} style={[{
      fontSize: size, color: color ?? (dim ? c.textDim : c.text), fontWeight: bold ? '700' : semi ? '600' : '400',
      textAlign: center ? 'center' : undefined, fontFamily: mono ? 'monospace' : undefined,
    }, style]}>{children}</Text>
  );
}

export function Tap({ children, onPress, onLongPress, style, disabled, round, label }: {
  children: ReactNode; onPress?: () => void; onLongPress?: () => void; style?: StyleProp<ViewStyle>; disabled?: boolean; round?: boolean; label?: string;
}) {
  const c = useTheme();
  return (
    <Pressable onPress={onPress} onLongPress={onLongPress} disabled={disabled} accessibilityRole="button" accessibilityLabel={label}
      android_ripple={{ color: alpha(c.text, 0.12), borderless: !!round, foreground: true }}
      style={[style, disabled ? { opacity: 0.4 } : null, round ? null : { overflow: 'hidden' }]}>
      {children}
    </Pressable>
  );
}

export function Button({ title, onPress, icon: Icon, kind = 'primary', disabled, flex, small }: {
  title: string; onPress?: () => void; icon?: LucideIcon; kind?: 'primary' | 'soft' | 'ghost' | 'danger'; disabled?: boolean; flex?: boolean; small?: boolean;
}) {
  const c = useTheme();
  const bg = kind === 'primary' ? c.accent : kind === 'soft' ? c.accentSoft : kind === 'danger' ? alpha(c.danger, 0.15) : 'transparent';
  const fg = kind === 'primary' ? c.onAccent : kind === 'danger' ? c.danger : kind === 'soft' ? c.accent : c.text;
  return (
    <Tap onPress={onPress} disabled={disabled} label={title} style={[{
      backgroundColor: bg, borderRadius: 14, paddingVertical: small ? 8 : 13, paddingHorizontal: 16, flexDirection: 'row',
      alignItems: 'center', justifyContent: 'center', gap: 8, borderWidth: kind === 'ghost' ? 1 : 0, borderColor: c.border,
    }, flex ? { flex: 1 } : null]}>
      {Icon ? <Icon size={small ? 16 : 18} color={fg} /> : null}
      <T semi size={small ? 13.5 : 15} color={fg} numberOfLines={1}>{title}</T>
    </Tap>
  );
}

export function IconButton({ icon: Icon, onPress, label, color }: { icon: LucideIcon; onPress: () => void; label: string; color?: string }) {
  const c = useTheme();
  return (
    <Tap round onPress={onPress} label={label} style={{ padding: 9 }}>
      <Icon size={22} color={color ?? c.text} />
    </Tap>
  );
}

export function Card({ children, style }: { children: ReactNode; style?: StyleProp<ViewStyle> }) {
  const c = useTheme();
  return <View style={[{ backgroundColor: c.card, borderRadius: 18, overflow: 'hidden' }, style]}>{children}</View>;
}

export function Row({ icon: Icon, title, sub, right, onPress, onLongPress, active, iconColor }: {
  icon?: LucideIcon; title: string; sub?: string; right?: ReactNode; onPress?: () => void; onLongPress?: () => void; active?: boolean; iconColor?: string;
}) {
  const c = useTheme();
  return (
    <Tap onPress={onPress} onLongPress={onLongPress} style={{ flexDirection: 'row', alignItems: 'center', gap: 12, paddingHorizontal: 16, paddingVertical: 12, backgroundColor: active ? c.accentSoft : undefined }}>
      {Icon ? (
        <View style={{ width: 38, height: 38, borderRadius: 12, backgroundColor: c.accentSoft, alignItems: 'center', justifyContent: 'center' }}>
          <Icon size={20} color={iconColor ?? c.accent} />
        </View>
      ) : null}
      <View style={{ flex: 1, gap: 2 }}>
        <T semi numberOfLines={1}>{title}</T>
        {sub ? <T dim size={12.5} numberOfLines={2}>{sub}</T> : null}
      </View>
      {right}
    </Tap>
  );
}

export function Toggle({ title, hint, value, onChange }: { title: string; hint?: string; value: boolean; onChange: (v: boolean) => void }) {
  const c = useTheme();
  return (
    <Tap onPress={() => onChange(!value)} style={{ flexDirection: 'row', alignItems: 'center', paddingHorizontal: 16, paddingVertical: 10, gap: 12 }}>
      <View style={{ flex: 1, gap: 2 }}>
        <T>{title}</T>
        {hint ? <T dim size={12.5}>{hint}</T> : null}
      </View>
      <Switch value={value} onValueChange={onChange} thumbColor={value ? c.accent : undefined} trackColor={{ true: alpha(c.accent, 0.45), false: c.cardAlt }} />
    </Tap>
  );
}

export function Segmented<V extends string | number>({ value, options, onChange }: { value: V; options: { value: V; label: string }[]; onChange: (v: V) => void }) {
  const c = useTheme();
  return (
    <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: 6 }}>
      {options.map((o) => {
        const on = o.value === value;
        return (
          <Tap key={String(o.value)} onPress={() => onChange(o.value)} style={{ paddingHorizontal: 12, paddingVertical: 7, borderRadius: 10, backgroundColor: on ? c.accent : c.cardAlt }}>
            <T size={13.5} semi color={on ? c.onAccent : c.text}>{o.label}</T>
          </Tap>
        );
      })}
    </View>
  );
}

export function Field({ label, hint, children }: { label: string; hint?: string; children: ReactNode }) {
  return (
    <View style={{ paddingHorizontal: 16, paddingVertical: 10, gap: 8 }}>
      <T semi size={14}>{label}</T>
      {children}
      {hint ? <T dim size={12.5}>{hint}</T> : null}
    </View>
  );
}

export function Input({ value, onChange, placeholder, mono, autoFocus }: { value: string; onChange: (v: string) => void; placeholder?: string; mono?: boolean; autoFocus?: boolean }) {
  const c = useTheme();
  return (
    <TextInput value={value} onChangeText={onChange} placeholder={placeholder} placeholderTextColor={c.textDim} autoFocus={autoFocus}
      autoCapitalize="none" autoCorrect={false}
      style={{ backgroundColor: c.cardAlt, color: c.text, borderRadius: 12, paddingHorizontal: 14, paddingVertical: 10, fontSize: 15, fontFamily: mono ? 'monospace' : undefined }} />
  );
}

export function Section({ title, right }: { title: string; right?: ReactNode }) {
  return (
    <View style={{ flexDirection: 'row', alignItems: 'center', paddingHorizontal: 4, paddingTop: 8 }}>
      <T dim semi size={13} style={{ flex: 1, textTransform: 'uppercase', letterSpacing: 0.5 }}>{title}</T>
      {right}
    </View>
  );
}

export function Divider() {
  const c = useTheme();
  return <View style={{ height: 1, backgroundColor: c.border, marginLeft: 16 }} />;
}

export function Loading() {
  const c = useTheme();
  return <View style={{ flex: 1, alignItems: 'center', justifyContent: 'center', padding: 40 }}><ActivityIndicator color={c.accent} size="large" /></View>;
}

export function Empty({ text }: { text: string }) {
  return <View style={{ padding: 40, alignItems: 'center' }}><T dim center>{text}</T></View>;
}

/** Audio packs (.osw + .osw.idx) in audio/. */
import { router } from 'expo-router';
import { AudioLines } from 'lucide-react-native';
import { useEffect, useState } from 'react';
import { ScrollView, View } from 'react-native';

import { G, type OswInfo } from '../../modules/gta-native';
import { Card, Divider, Empty, Loading, Row, T } from '../components/ui';
import { bytes, useT } from '../i18n';
import { useSettings } from '../lib/store';

export default function Audio() {
  const L = useT();
  const gameDir = useSettings((s) => s.gameDir);
  const [list, setList] = useState<OswInfo[] | null>(null);
  useEffect(() => {
    if (gameDir) G.oswList(gameDir).then(setList).catch(() => setList([]));
  }, [gameDir]);
  if (!list) return <Loading />;
  return (
    <ScrollView contentContainerStyle={{ padding: 16, gap: 12 }}>
      <T dim size={13}>{L.audio.hint}</T>
      <Card>
        {list.length === 0 ? <Empty text={L.common.none} /> : list.map((d, i) => (
          <View key={d.path}>
            {i ? <Divider /> : null}
            <Row icon={AudioLines} title={d.name.replace(/\.osw$/i, '')} sub={`${d.dir} · ${bytes(d.size)}`} onPress={() => router.push({ pathname: '/osw', params: { path: d.path } })} />
          </View>
        ))}
      </Card>
    </ScrollView>
  );
}

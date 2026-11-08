/** Texture databases of the game folder (texdb/<name>/). */
import { router } from 'expo-router';
import { Database } from 'lucide-react-native';
import { useEffect, useState } from 'react';
import { ScrollView, View } from 'react-native';

import { G, type TexDbInfo } from '../../modules/gta-native';
import { Card, Divider, Empty, Loading, Row } from '../components/ui';
import { bytes, useT } from '../i18n';
import { useSettings } from '../lib/store';

export default function Textures() {
  const L = useT();
  const gameDir = useSettings((s) => s.gameDir);
  const [dbs, setDbs] = useState<TexDbInfo[] | null>(null);
  useEffect(() => {
    if (gameDir) G.texdbList(gameDir).then(setDbs).catch(() => setDbs([]));
  }, [gameDir]);
  if (!dbs) return <Loading />;
  return (
    <ScrollView contentContainerStyle={{ padding: 16 }}>
      <Card>
        {dbs.length === 0 ? <Empty text={L.common.none} /> : dbs.map((d, i) => (
          <View key={d.path}>
            {i ? <Divider /> : null}
            <Row icon={Database} title={d.name} sub={bytes(d.size)} onPress={() => router.push({ pathname: '/texdb', params: { path: d.path } })} />
          </View>
        ))}
      </Card>
    </ScrollView>
  );
}

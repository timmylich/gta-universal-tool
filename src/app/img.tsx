/** IMG archives found in the game folder. */
import { router } from 'expo-router';
import { Box } from 'lucide-react-native';
import { useEffect, useState } from 'react';
import { ScrollView, View } from 'react-native';

import { G, type ImgInfo } from '../../modules/gta-native';
import { Card, Divider, Empty, Loading, Row } from '../components/ui';
import { bytes, useT } from '../i18n';
import { useSettings } from '../lib/store';

export default function Img() {
  const L = useT();
  const gameDir = useSettings((s) => s.gameDir);
  const [list, setList] = useState<ImgInfo[] | null>(null);
  useEffect(() => {
    if (gameDir) G.imgList(gameDir).then(setList).catch(() => setList([]));
  }, [gameDir]);
  if (!list) return <Loading />;
  return (
    <ScrollView contentContainerStyle={{ padding: 16 }}>
      <Card>
        {list.length === 0 ? <Empty text={L.common.none} /> : list.map((d, i) => (
          <View key={d.path}>
            {i ? <Divider /> : null}
            <Row icon={Box} title={d.name} sub={`${bytes(d.size)} · ${d.path.replace(gameDir ?? '', '')}`} onPress={() => router.push({ pathname: '/imgview', params: { path: d.path } })} />
          </View>
        ))}
      </Card>
    </ScrollView>
  );
}

import { PageContainer } from '@ant-design/pro-components';
import { Card, Select, Space } from 'antd';
import { history, useAccess, useSearchParams } from '@umijs/max';
import { useEffect, useState } from 'react';
import { listApps } from '@/services/app';
import { listTopologies } from '@/services/topology';
import MemoryPanel from '@/pages/app/detail/components/MemoryPanel';

function syncMemoryUrl(appId?: number, topologyId?: number) {
  const params = new URLSearchParams();
  if (appId) params.set('applicationId', String(appId));
  if (topologyId) params.set('topologyId', String(topologyId));
  const qs = params.toString();
  history.replace(qs ? `/app/memory?${qs}` : '/app/memory');
}

export default () => {
  const access = useAccess();
  const [searchParams] = useSearchParams();
  const [apps, setApps] = useState<any[]>([]);
  const [topologies, setTopologies] = useState<any[]>([]);
  const [filterAppId, setFilterAppId] = useState<number | undefined>();
  const [filterTopologyId, setFilterTopologyId] = useState<number | undefined>();

  useEffect(() => {
    listApps().then(setApps);
    listTopologies().then(setTopologies);
    const appId = searchParams.get('applicationId');
    const topologyId = searchParams.get('topologyId');
    setFilterAppId(appId ? Number(appId) : undefined);
    setFilterTopologyId(topologyId ? Number(topologyId) : undefined);
  }, [searchParams]);

  return (
    <PageContainer title="共享记忆" subTitle="跨 Agent 向量/关键词检索（pgvector）">
      <Card style={{ marginBottom: 16 }}>
        <Space wrap>
          <Select
            style={{ width: 220 }}
            placeholder="按应用筛选"
            allowClear
            value={filterAppId}
            onChange={(v) => {
              setFilterAppId(v);
              syncMemoryUrl(v, filterTopologyId);
            }}
            options={apps.map((a) => ({ label: a.name, value: a.id }))}
          />
          <Select
            style={{ width: 220 }}
            placeholder="按拓扑筛选"
            allowClear
            value={filterTopologyId}
            onChange={(v) => {
              setFilterTopologyId(v);
              syncMemoryUrl(filterAppId, v);
            }}
            options={topologies.map((t) => ({ label: t.name, value: t.id }))}
          />
        </Space>
      </Card>
      <MemoryPanel
        applicationId={filterAppId}
        topologyId={filterTopologyId}
        canWrite={access.canWriteMemory}
      />
    </PageContainer>
  );
};

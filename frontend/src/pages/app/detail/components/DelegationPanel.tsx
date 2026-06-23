import { Button, Card, Select, Space, Tag, Timeline, Typography, message } from 'antd';
import { history } from '@umijs/max';
import { useEffect, useState } from 'react';
import { listDelegations, updateDelegation } from '@/services/delegation';
import DelegationRecordModal from './DelegationRecordModal';

const delegationStatusColor: Record<string, string> = {
  running: 'blue',
  completed: 'green',
  failed: 'red',
  cancelled: 'default',
};

type Props = {
  applicationId?: number;
  topologyId?: number;
  canWrite?: boolean;
  compact?: boolean;
  showViewAllLink?: boolean;
};

export default function DelegationPanel({
  applicationId,
  topologyId,
  canWrite = false,
  compact = false,
  showViewAllLink = false,
}: Props) {
  const [delegations, setDelegations] = useState<any[]>([]);
  const [loading, setLoading] = useState(false);
  const [recordOpen, setRecordOpen] = useState(false);

  const load = async () => {
    setLoading(true);
    try {
      setDelegations(await listDelegations({ topologyId, applicationId }));
    } catch {
      setDelegations([]);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    load();
  }, [applicationId, topologyId]);

  const handleStatusUpdate = async (id: number, status: string) => {
    try {
      await updateDelegation(id, { status, completedAt: new Date().toISOString() });
      message.success('委派状态已更新');
      load();
    } catch {
      message.error('更新失败');
    }
  };

  return (
    <>
      <Card
        size={compact ? 'small' : 'default'}
        title="委派追踪"
        loading={loading}
        extra={(
          <Space>
            {canWrite && topologyId && (
              <Button size="small" type="primary" onClick={() => setRecordOpen(true)}>记录委派</Button>
            )}
            {showViewAllLink && topologyId && (
              <Button
                type="link"
                size="small"
                onClick={() => history.push(`/app/topology?topologyId=${topologyId}&tab=delegations`)}
              >
                在拓扑页打开
              </Button>
            )}
          </Space>
        )}
      >
        {!topologyId && (
          <Typography.Paragraph type="secondary">
            该应用未加入协同拓扑，暂无委派记录。
            <Button type="link" onClick={() => history.push('/app/topology')}>前往协同拓扑</Button>
          </Typography.Paragraph>
        )}
        <Timeline
          items={(delegations || []).map((d) => ({
            color: delegationStatusColor[d.status] || 'gray',
            children: (
              <div>
                <Space wrap>
                  <Tag color={delegationStatusColor[d.status]}>{d.status}</Tag>
                  <Typography.Text strong>
                    {d.parentAppName || d.parentAppId} → {d.childAppName || d.childAppId}
                  </Typography.Text>
                  {canWrite && d.status === 'running' && (
                    <Select
                      size="small"
                      placeholder="更新状态"
                      style={{ width: 100 }}
                      options={[
                        { label: '完成', value: 'completed' },
                        { label: '失败', value: 'failed' },
                      ]}
                      onChange={(v) => handleStatusUpdate(d.id, v)}
                    />
                  )}
                </Space>
                <div style={{ marginTop: 4 }}>{d.taskSummary}</div>
                <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                  {d.startedAt ? new Date(d.startedAt).toLocaleString() : ''}
                  {d.completedAt ? ` · 完成 ${new Date(d.completedAt).toLocaleString()}` : ''}
                  {d.parentAppId && (
                    <>
                      {' · '}
                      <a onClick={() => history.push(`/app/detail/${d.parentAppId}?tab=delegation`)}>父应用</a>
                    </>
                  )}
                  {d.childAppId && (
                    <>
                      {' · '}
                      <a onClick={() => history.push(`/app/detail/${d.childAppId}?tab=delegation`)}>子应用</a>
                    </>
                  )}
                </Typography.Text>
              </div>
            ),
          }))}
        />
        {topologyId && (delegations || []).length === 0 && (
          <Typography.Text type="secondary">暂无委派记录</Typography.Text>
        )}
      </Card>

      <DelegationRecordModal
        open={recordOpen}
        topologyId={topologyId}
        defaultParentAppId={applicationId}
        onOpenChange={setRecordOpen}
        onSuccess={load}
      />
    </>
  );
}

import { PageContainer, ProTable } from '@ant-design/pro-components';
import { Alert, Tag } from 'antd';
import { history } from '@umijs/max';
import { useState } from 'react';
import { listPortUsage } from '@/services/dashboard';
import type { PortUsageDto } from '@/services/types/app-management';

function markConflicts(rows: PortUsageDto[]): PortUsageDto[] {
  const hostPortCounts = new Map<number, number>();
  rows.forEach((row) => {
    if (row.hostPort != null) {
      hostPortCounts.set(row.hostPort, (hostPortCounts.get(row.hostPort) || 0) + 1);
    }
  });
  return rows.map((row) => ({
    ...row,
    conflict: row.hostPort != null && (hostPortCounts.get(row.hostPort) || 0) > 1,
  }));
}

export default () => {
  const [loadError, setLoadError] = useState<string>();

  return (
    <PageContainer title="端口占用全景" subTitle="所有应用端口映射与冲突检测">
      {loadError && (
        <Alert
          type="error"
          showIcon
          message="加载失败"
          description={loadError}
          style={{ marginBottom: 16 }}
        />
      )}
      <ProTable<PortUsageDto>
        rowKey={(r) => `${r.appId}-${r.portName}`}
        search={false}
        request={async () => {
          try {
            setLoadError(undefined);
            const data = markConflicts(await listPortUsage());
            return { data, success: true };
          } catch (e: unknown) {
            const message = e instanceof Error ? e.message : '加载端口数据失败';
            setLoadError(message);
            return { data: [], success: false };
          }
        }}
        columns={[
          { title: '应用', dataIndex: 'appName', render: (_, r) => <a onClick={() => history.push(`/app/detail/${r.appId}`)}>{r.appName}</a> },
          { title: '模板', dataIndex: 'templateCode', render: (v) => <Tag>{v}</Tag> },
          { title: '端口名', dataIndex: 'portName' },
          { title: '容器端口', dataIndex: 'containerPort' },
          { title: 'Host 端口', dataIndex: 'hostPort', render: (v) => v || '-' },
          {
            title: '冲突',
            dataIndex: 'conflict',
            render: (v) => (v ? <Tag color="red">冲突</Tag> : <Tag>正常</Tag>),
          },
          {
            title: '对外暴露',
            dataIndex: 'expose',
            render: (v) => <Tag color={v ? 'green' : 'default'}>{v ? '是' : '否'}</Tag>,
          },
          { title: '访问 URL', dataIndex: 'accessUrl', ellipsis: true, render: (v) => v || '-' },
        ]}
      />
    </PageContainer>
  );
};

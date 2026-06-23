import { ProDescriptions } from '@ant-design/pro-components';
import { Alert, Button, Space, Tag, Typography } from 'antd';
import { history } from '@umijs/max';
import type { AppHealthDto } from '@/services/types/app-management';

type Props = {
  app: any;
  runtimeStatus?: any;
  health?: AppHealthDto;
  healthLoadFailed?: boolean;
  onOpenConsole?: (consoleKey: string) => void;
};

export default function AppOverviewPanel({ app, runtimeStatus, health, healthLoadFailed, onOpenConsole }: Props) {
  const phaseColor: Record<string, string> = {
    RUNNING: 'green',
    STOPPED: 'default',
    CREATED: 'blue',
    ERROR: 'red',
    UNKNOWN: 'orange',
  };

  return (
    <>
      {healthLoadFailed && (
        <Alert
          type="warning"
          showIcon
          message="健康检查数据不可用"
          description="无法获取 Agent 健康状态，请稍后刷新或检查应用权限。"
          style={{ marginBottom: 16 }}
        />
      )}
      <ProDescriptions column={2} dataSource={app} columns={[        {
          title: '模板',
          render: () => (
            <Space>
              <Tag>{app?.templateCode || app?.templateName}</Tag>
              <Typography.Text type="secondary">{app?.templateName}</Typography.Text>
            </Space>
          ),
        },
        {
          title: '状态',
          dataIndex: 'status',
          render: (_, record) => (
            <Space>
              <Tag>{record.status}</Tag>
              {runtimeStatus?.phase && (
                <Tag color={phaseColor[runtimeStatus.phase] || 'default'}>{runtimeStatus.phase}</Tag>
              )}
              {runtimeStatus?.healthy != null && (
                <Tag color={runtimeStatus.healthy ? 'green' : 'red'}>
                  {runtimeStatus.healthy ? '健康' : '不健康'}
                </Tag>
              )}
              {health && (
                <Tag color={health.healthy ? 'green' : 'orange'}>
                  Agent {health.healthy ? '健康' : '异常'}
                </Tag>
              )}
            </Space>
          ),
        },
        { title: '运行消息', render: () => runtimeStatus?.message || health?.message || '-' },
        { title: '镜像', dataIndex: 'image' },
        { title: '标签', dataIndex: 'tag' },
        { title: '运行时', dataIndex: 'runtimeProvider' },
        { title: '容器引用', dataIndex: 'runtimeRef' },
        { title: 'CPU 限制', render: (_, record) => record.resources?.cpu || '-' },
        { title: '内存限制', render: (_, record) => record.resources?.memory || '-' },
        { title: '副本数', dataIndex: 'replicas' },
        {
          title: '访问地址',
          dataIndex: 'accessUrls',
          render: (_, record) => (
            (record.accessUrls || []).length > 0
              ? (record.accessUrls || []).map((u: any) => <div key={u.name}>{u.name}: {u.url}</div>)
              : '-'
          ),
        },
      ]} />
      {(health?.webConsoles || []).length > 0 && (
        <div style={{ marginTop: 16 }}>
          <Typography.Text strong>内置控制台</Typography.Text>
          <div style={{ marginTop: 8 }}>
            <Space wrap>
              {(health.webConsoles || []).map((c: any) => (
                <Button key={c.key} onClick={() => onOpenConsole?.(c.key)}>
                  打开 {c.title || c.key}
                </Button>
              ))}
            </Space>
          </div>
        </div>
      )}
      {health?.checkedUrl && (
        <Typography.Paragraph type="secondary" style={{ marginTop: 12 }}>
          健康检查: {health.checkedUrl} ({health.statusCode || '-'})
        </Typography.Paragraph>
      )}
      <div style={{ marginTop: 12 }}>
        <Typography.Text strong>快捷入口</Typography.Text>
        <div style={{ marginTop: 8 }}>
          <Space wrap>
            <Button type="link" onClick={() => history.push(`/app/detail/${app.id}?tab=files`)}>数据卷</Button>
            <Button type="link" onClick={() => history.push(`/files?appId=${app.id}`)}>文件中心</Button>
            <Button type="link" onClick={() => history.push(`/app/detail/${app.id}?tab=memory`)}>记忆</Button>
            <Button type="link" onClick={() => history.push(`/app/detail/${app.id}?tab=delegation`)}>委派</Button>
            <Button type="link" onClick={() => history.push(`/app/detail/${app.id}?tab=skills`)}>Skills</Button>
            <Button type="link" onClick={() => history.push('/app/ports')}>端口全景</Button>
          </Space>
        </div>
      </div>
    </>
  );
}

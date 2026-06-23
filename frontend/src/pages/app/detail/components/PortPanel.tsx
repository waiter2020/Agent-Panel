import { updateApp } from '@/services/app';
import { Button, Card, InputNumber, message, Switch, Table } from 'antd';
import { useEffect, useState } from 'react';

type Props = {
  appId: number;
  app: any;
  onSaved: () => void;
};

export default function PortPanel({ appId, app, onSaved }: Props) {
  const [ports, setPorts] = useState<any[]>([]);

  useEffect(() => {
    setPorts((app?.ports || []).map((p: any) => ({ ...p })));
  }, [app]);

  const columns = [
    { title: '名称', dataIndex: 'name' },
    { title: '容器端口', dataIndex: 'containerPort' },
    {
      title: 'Host 端口',
      dataIndex: 'hostPort',
      render: (_: any, record: any, index: number) => (
        <InputNumber
          min={1}
          max={65535}
          value={record.hostPort}
          placeholder="自动分配"
          onChange={(v) => {
            const next = [...ports];
            next[index] = { ...next[index], hostPort: v ?? undefined };
            setPorts(next);
          }}
        />
      ),
    },
    {
      title: '对外暴露',
      dataIndex: 'expose',
      render: (_: any, record: any, index: number) => (
        <Switch
          checked={!!record.expose}
          onChange={(checked) => {
            const next = [...ports];
            next[index] = { ...next[index], expose: checked };
            setPorts(next);
          }}
        />
      ),
    },
    { title: '协议', dataIndex: 'protocol', render: (v: string) => v || 'TCP' },
  ];

  return (
    <Card
      title="端口与网络"
      extra={(
        <Button
          type="primary"
          onClick={async () => {
            await updateApp(appId, { ports });
            message.success('端口配置已保存，请重新部署以生效');
            onSaved();
          }}
        >
          保存
        </Button>
      )}
    >
      <Table rowKey="name" pagination={false} size="small" columns={columns} dataSource={ports} />
    </Card>
  );
}

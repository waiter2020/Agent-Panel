import { Button, Card, Table, Tag, Typography } from 'antd';
import { history } from '@umijs/max';

type Props = {
  context: any;
  canWriteSkill: boolean;
};

export default function SkillsPanel({ context, canWriteSkill }: Props) {
  const skills = context?.skills || [];
  const injectedEnv = context?.injectedEnv || [];

  return (
    <Card>
      {context?.topologyId ? (
        <Typography.Paragraph type="secondary">
          所属拓扑：{context.topologyName || context.topologyId}
          {canWriteSkill && (
            <Button
              type="link"
              onClick={() => history.push('/app/topology')}
            >
              前往拓扑管理 Skills
            </Button>
          )}
        </Typography.Paragraph>
      ) : (
        <Typography.Paragraph type="secondary">
          该应用未加入任何拓扑，暂无共享 Skills。
        </Typography.Paragraph>
      )}

      <Table
        rowKey="id"
        size="small"
        pagination={false}
        dataSource={skills}
        locale={{ emptyText: '暂无共享 Skills' }}
        columns={[
          { title: '名称', dataIndex: 'name' },
          { title: '文件路径', dataIndex: 'filePath', ellipsis: true },
          {
            title: '更新时间',
            dataIndex: 'updatedAt',
            render: (v) => (v ? new Date(v).toLocaleString() : '-'),
          },
        ]}
      />

      {injectedEnv.length > 0 && (
        <div style={{ marginTop: 24 }}>
          <Typography.Text strong>部署时注入的 Skills 环境变量</Typography.Text>
          <Table
            style={{ marginTop: 8 }}
            rowKey={(r) => `${r.applicationId}-${r.envKey}`}
            size="small"
            pagination={false}
            dataSource={injectedEnv}
            columns={[
              { title: 'Key', dataIndex: 'envKey' },
              {
                title: 'Value',
                dataIndex: 'envValue',
                ellipsis: true,
                render: (v, r) => (
                  r.envKey?.includes('KEY') || r.envKey?.includes('JSON')
                    ? <Tag>已注入</Tag>
                    : v
                ),
              },
              { title: '来源', dataIndex: 'source' },
            ]}
          />
        </div>
      )}
    </Card>
  );
}

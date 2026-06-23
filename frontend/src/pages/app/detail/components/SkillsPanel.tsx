import { ModalForm, ProForm, ProFormText, ProFormTextArea } from '@ant-design/pro-components';
import { Button, Card, Popconfirm, Space, Table, Tag, Typography, Upload, message } from 'antd';
import { history } from '@umijs/max';
import { useEffect, useState } from 'react';
import {
  createSkill,
  deleteSkill,
  downloadSkill,
  listSkills,
  notifySkillReload,
} from '@/services/skill';

type Props = {
  context: any;
  appId: number;
  canWriteSkill: boolean;
  onRefresh?: () => void;
};

export default function SkillsPanel({ context, appId, canWriteSkill, onRefresh }: Props) {
  const topologyId = context?.topologyId;
  const [skills, setSkills] = useState<any[]>(context?.skills || []);
  const [skillOpen, setSkillOpen] = useState(false);
  const injectedEnv = context?.injectedEnv || [];

  const loadSkills = async () => {
    if (!topologyId) {
      setSkills([]);
      return;
    }
    try {
      setSkills(await listSkills({ topologyId, applicationId: appId }));
    } catch {
      setSkills(context?.skills || []);
    }
  };

  useEffect(() => {
    loadSkills();
  }, [topologyId, appId]);

  return (
    <Card>
      {topologyId ? (
        <Typography.Paragraph type="secondary">
          所属拓扑：{context.topologyName || topologyId}
          <Space style={{ marginLeft: 8 }}>
            {canWriteSkill && (
              <>
                <Button size="small" type="primary" onClick={() => setSkillOpen(true)}>上传技能</Button>
                <Button
                  type="link"
                  onClick={() => history.push('/app/topology')}
                >
                  在拓扑页管理全部 Skills
                </Button>
              </>
            )}
          </Space>
        </Typography.Paragraph>
      ) : (
        <Typography.Paragraph type="secondary">
          该应用未加入任何拓扑，暂无共享 Skills。
          <Button type="link" onClick={() => history.push('/app/topology')}>前往协同拓扑</Button>
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
          { title: '描述', dataIndex: 'description', ellipsis: true },
          { title: '文件路径', dataIndex: 'filePath', ellipsis: true },
          {
            title: '更新时间',
            dataIndex: 'updatedAt',
            render: (v) => (v ? new Date(v).toLocaleString() : '-'),
          },
          {
            title: '操作',
            render: (_, record) => [
              record.filePath && (
                <a key="down" onClick={async () => {
                  const { url } = await downloadSkill(record.id);
                  window.open(url);
                }}>下载</a>
              ),
              canWriteSkill && (
                <a key="reload" style={{ marginLeft: 8 }} onClick={async () => {
                  await notifySkillReload(record.id);
                  message.success('已通知 Agent 重新加载');
                }}>通知重载</a>
              ),
              canWriteSkill && (
                <Popconfirm key="del" title="确认删除？" onConfirm={async () => {
                  await deleteSkill(record.id);
                  message.success('已删除');
                  await loadSkills();
                  onRefresh?.();
                }}>
                  <a style={{ color: 'red', marginLeft: 8 }}>删除</a>
                </Popconfirm>
              ),
            ].filter(Boolean),
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

      <ModalForm
        title="上传共享技能"
        open={skillOpen}
        onOpenChange={setSkillOpen}
        onFinish={async (values) => {
          if (!topologyId) return false;
          const fileList = values.skillFile as any[];
          const file = fileList?.[0]?.originFileObj as File | undefined;
          await createSkill({
            topologyId,
            applicationId: appId,
            name: values.name,
            description: values.description,
            content: values.content,
          }, file);
          message.success('技能已创建');
          await loadSkills();
          onRefresh?.();
          return true;
        }}
      >
        <ProFormText name="name" label="技能名称" rules={[{ required: true }]} />
        <ProFormTextArea name="description" label="描述" />
        <ProFormTextArea name="content" label="技能内容" fieldProps={{ rows: 4 }} />
        <ProForm.Item name="skillFile" label="技能文件（可选）" valuePropName="fileList" getValueFromEvent={(e) => (Array.isArray(e) ? e : e?.fileList)}>
          <Upload beforeUpload={() => false} maxCount={1}>
            <Button>选择文件</Button>
          </Upload>
        </ProForm.Item>
      </ModalForm>
    </Card>
  );
}

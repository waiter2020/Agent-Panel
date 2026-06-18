import { PageContainer, ProForm, ProFormSelect, ProFormText, ProFormTextArea } from '@ant-design/pro-components';
import { Button, Card, Input, List, Popconfirm, Space, Tag, Typography, message } from 'antd';
import { useEffect, useState } from 'react';
import { useAccess } from '@umijs/max';
import { deleteMemory, listMemory, searchMemory, storeMemory } from '@/services/memory';

const scopeOptions = [
  { label: '全局', value: 'global' },
  { label: '拓扑', value: 'topology' },
  { label: '应用', value: 'app' },
];

const scopeColor: Record<string, string> = {
  global: 'blue',
  topology: 'purple',
  app: 'green',
};

export default () => {
  const access = useAccess();
  const [query, setQuery] = useState('');
  const [results, setResults] = useState<any[]>([]);
  const [loading, setLoading] = useState(false);
  const [formOpen, setFormOpen] = useState(false);

  const loadRecent = async () => {
    setLoading(true);
    try {
      setResults(await listMemory());
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadRecent();
  }, []);

  const handleSearch = async (value?: string) => {
    const q = (value ?? query).trim();
    setQuery(q);
    setLoading(true);
    try {
      if (q) {
        setResults(await searchMemory({ q, limit: 20 }));
      } else {
        await loadRecent();
      }
    } finally {
      setLoading(false);
    }
  };

  return (
    <PageContainer title="共享记忆" subTitle="跨 Agent 向量/关键词检索（pgvector）">
      <Card style={{ marginBottom: 16 }}>
        <Input.Search
          placeholder="语义或关键词搜索记忆..."
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          onSearch={handleSearch}
          enterButton="搜索"
          loading={loading}
          style={{ maxWidth: 560 }}
        />
        {access.canWriteMemory && (
          <Button type="primary" style={{ marginLeft: 12 }} onClick={() => setFormOpen(!formOpen)}>
            {formOpen ? '收起表单' : '添加记忆'}
          </Button>
        )}
      </Card>

      {formOpen && access.canWriteMemory && (
        <Card title="添加记忆" style={{ marginBottom: 16 }}>
          <ProForm
            layout="vertical"
            onFinish={async (values) => {
              await storeMemory({
                ...values,
                topologyId: values.topologyId ? Number(values.topologyId) : undefined,
                applicationId: values.applicationId ? Number(values.applicationId) : undefined,
              } as any);
              message.success('记忆已保存');
              setFormOpen(false);
              await loadRecent();
            }}
            submitter={{ searchConfig: { submitText: '保存' } }}
          >
            <ProFormText name="key" label="键" rules={[{ required: true, message: '请输入键' }]} />
            <ProFormTextArea name="content" label="内容" rules={[{ required: true, message: '请输入内容' }]} fieldProps={{ rows: 4 }} />
            <ProFormSelect name="scope" label="范围" initialValue="global" options={scopeOptions} />
            <ProFormText name="topologyId" label="拓扑 ID" tooltip="scope=topology 时必填" />
            <ProFormText name="applicationId" label="应用 ID" tooltip="scope=app 时必填" />
          </ProForm>
        </Card>
      )}

      <Card title="记忆列表">
        <List
          loading={loading}
          dataSource={results}
          locale={{ emptyText: '暂无记忆，请搜索或添加' }}
          renderItem={(item) => (
            <List.Item
              actions={access.canWriteMemory ? [
                <Popconfirm key="del" title="确认删除？" onConfirm={async () => {
                  await deleteMemory(item.id);
                  message.success('已删除');
                  await handleSearch();
                }}>
                  <a style={{ color: 'red' }}>删除</a>
                </Popconfirm>,
              ] : []}
            >
              <List.Item.Meta
                title={(
                  <Space>
                    <Typography.Text strong>{item.key}</Typography.Text>
                    <Tag color={scopeColor[item.scope] || 'default'}>{item.scope}</Tag>
                    {item.score != null && <Tag color="gold">相似度 {(item.score * 100).toFixed(1)}%</Tag>}
                  </Space>
                )}
                description={(
                  <>
                    <Typography.Paragraph ellipsis={{ rows: 2 }} style={{ marginBottom: 4 }}>{item.content}</Typography.Paragraph>
                    <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                      {item.createdAt ? new Date(item.createdAt).toLocaleString() : ''}
                    </Typography.Text>
                  </>
                )}
              />
            </List.Item>
          )}
        />
      </Card>
    </PageContainer>
  );
};

import { ProForm, ProFormSelect, ProFormText, ProFormTextArea } from '@ant-design/pro-components';
import { Button, Card, Input, List, Popconfirm, Space, Tag, Typography, message } from 'antd';
import { history } from '@umijs/max';
import { useCallback, useEffect, useState } from 'react';
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

type Props = {
  applicationId?: number;
  topologyId?: number;
  defaultScope?: string;
  canWrite?: boolean;
  compact?: boolean;
  showViewAllLink?: boolean;
};

export default function MemoryPanel({
  applicationId,
  topologyId,
  defaultScope,
  canWrite = false,
  compact = false,
  showViewAllLink = false,
}: Props) {
  const [query, setQuery] = useState('');
  const [results, setResults] = useState<any[]>([]);
  const [loading, setLoading] = useState(false);
  const [formOpen, setFormOpen] = useState(false);

  const listFilters = applicationId
    ? { applicationId, scope: 'app' as const }
    : topologyId
      ? { topologyId, scope: 'topology' as const }
      : {};

  const searchFilters = applicationId
    ? { applicationId }
    : topologyId
      ? { topologyId }
      : {};

  const loadRecent = useCallback(async () => {
    setLoading(true);
    try {
      const filters = applicationId
        ? { applicationId, scope: 'app' as const }
        : topologyId
          ? { topologyId, scope: 'topology' as const }
          : {};
      setResults(await listMemory(filters));
    } finally {
      setLoading(false);
    }
  }, [applicationId, topologyId]);

  useEffect(() => {
    loadRecent();
  }, [loadRecent]);

  const handleSearch = async (value?: string) => {
    const q = (value ?? query).trim();
    setQuery(q);
    setLoading(true);
    try {
      if (q) {
        setResults(await searchMemory({
          q,
          ...searchFilters,
          limit: 20,
        }));
      } else {
        await loadRecent();
      }
    } finally {
      setLoading(false);
    }
  };

  const lockedScope = applicationId ? 'app' : topologyId ? 'topology' : defaultScope;

  return (
    <Card
      size={compact ? 'small' : 'default'}
      title={compact ? undefined : '共享记忆'}
      extra={showViewAllLink && (
        <Button type="link" onClick={() => {
          const params = new URLSearchParams();
          if (applicationId) params.set('applicationId', String(applicationId));
          if (topologyId) params.set('topologyId', String(topologyId));
          history.push(`/app/memory?${params.toString()}`);
        }}>
          在共享记忆页打开
        </Button>
      )}
    >
      <Input.Search
        placeholder="语义或关键词搜索记忆..."
        value={query}
        onChange={(e) => setQuery(e.target.value)}
        onSearch={handleSearch}
        enterButton="搜索"
        loading={loading}
        style={{ maxWidth: 560, marginBottom: 16 }}
      />
      {canWrite && (
        <Button type="primary" style={{ marginBottom: 16, marginLeft: compact ? 0 : 12 }} onClick={() => setFormOpen(!formOpen)}>
          {formOpen ? '收起表单' : '添加记忆'}
        </Button>
      )}

      {formOpen && canWrite && (
        <Card size="small" title="添加记忆" style={{ marginBottom: 16 }}>
          <ProForm
            layout="vertical"
            initialValues={{
              scope: lockedScope || 'global',
              topologyId,
              applicationId,
            }}
            onFinish={async (values) => {
              await storeMemory({
                ...values,
                topologyId: values.topologyId ? Number(values.topologyId) : topologyId,
                applicationId: values.applicationId ? Number(values.applicationId) : applicationId,
              } as any);
              message.success('记忆已保存');
              setFormOpen(false);
              await loadRecent();
            }}
            submitter={{ searchConfig: { submitText: '保存' } }}
          >
            <ProFormText name="key" label="键" rules={[{ required: true, message: '请输入键' }]} />
            <ProFormTextArea name="content" label="内容" rules={[{ required: true, message: '请输入内容' }]} fieldProps={{ rows: 4 }} />
            {!lockedScope && (
              <ProFormSelect name="scope" label="范围" options={scopeOptions} />
            )}
            {applicationId != null && <ProFormText name="applicationId" hidden />}
            {topologyId != null && <ProFormText name="topologyId" hidden />}
          </ProForm>
        </Card>
      )}

      <List
        loading={loading}
        dataSource={results}
        locale={{ emptyText: '暂无记忆，请搜索或添加' }}
        renderItem={(item) => (
          <List.Item
            actions={canWrite ? [
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
                    {item.applicationId && (
                      <>
                        {' · '}
                        <a onClick={() => history.push(`/app/detail/${item.applicationId}?tab=memory`)}>
                          应用 #{item.applicationId}
                        </a>
                      </>
                    )}
                  </Typography.Text>
                </>
              )}
            />
          </List.Item>
        )}
      />
    </Card>
  );
}

import { PageContainer, ProTable } from '@ant-design/pro-components';
import { Button, Popconfirm, Tag, message, Typography, Space, Modal } from 'antd';
import { CopyOutlined } from '@ant-design/icons';
import { listApiKeys, createApiKey, deleteApiKey, updateApiKey, rotateApiKey } from '@/services/system';
import { useRef, useState } from 'react';
import { ModalForm, ProFormDateTimePicker, ProFormSelect, ProFormText } from '@ant-design/pro-components';
import { useAccess } from '@umijs/max';

const SCOPE_OPTIONS = [
  { label: '推理 (ai:chat)', value: 'ai:chat' },
  { label: '记忆读取 (memory:read)', value: 'memory:read' },
  { label: '记忆写入 (memory:write)', value: 'memory:write' },
  { label: '技能读取 (skill:read)', value: 'skill:read' },
  { label: '技能写入 (skill:write)', value: 'skill:write' },
  { label: '委派读取 (delegation:read)', value: 'delegation:read' },
  { label: '委派写入 (delegation:write)', value: 'delegation:write' },
];

export default () => {
  const actionRef = useRef<any>();
  const access = useAccess();
  const [open, setOpen] = useState(false);
  const [createdKey, setCreatedKey] = useState<string | null>(null);
  const [rotateKey, setRotateKey] = useState<string | null>(null);

  const copyKey = async (key: string) => {
    await navigator.clipboard.writeText(key);
    message.success('已复制到剪贴板');
  };

  return (
    <PageContainer title="API 密钥">
      <ProTable
        actionRef={actionRef}
        rowKey="id"
        search={false}
        request={async () => {
          const data = await listApiKeys();
          return { data, success: true };
        }}
        toolBarRender={() => access.canManageApiKey ? [
          <Button key="add" type="primary" onClick={() => { setCreatedKey(null); setOpen(true); }}>新建密钥</Button>,
        ] : []}
        columns={[
          { title: '名称', dataIndex: 'name' },
          { title: '密钥前缀', dataIndex: 'keyPrefix', render: (v) => <Typography.Text code>{v}</Typography.Text> },
          {
            title: '权限范围',
            dataIndex: 'scopes',
            render: (_, record) => (record.scopes || []).map((s: string) => <Tag key={s}>{s}</Tag>),
          },
          {
            title: '状态',
            dataIndex: 'enabled',
            render: (v, record) => (
              <Tag color={record.deprecated ? 'orange' : v ? 'green' : 'default'}>
                {record.deprecated ? '已轮换(宽限期)' : v ? '启用' : '禁用'}
              </Tag>
            ),
          },
          { title: '过期时间', dataIndex: 'expiresAt', valueType: 'dateTime' },
          { title: '创建时间', dataIndex: 'createdAt', valueType: 'dateTime' },
          access.canManageApiKey ? {
            title: '操作',
            valueType: 'option',
            render: (_, record) => [
              <a
                key="toggle"
                onClick={async () => {
                  await updateApiKey(record.id, { enabled: !record.enabled, scopes: record.scopes, name: record.name, expiresAt: record.expiresAt });
                  message.success(record.enabled ? '已禁用' : '已启用');
                  actionRef.current?.reload();
                }}
              >
                {record.enabled ? '禁用' : '启用'}
              </a>,
              <a
                key="rotate"
                onClick={async () => {
                  const result = await rotateApiKey(record.id);
                  setRotateKey(result.rawKey);
                  message.success('密钥已轮换，旧密钥进入宽限期');
                  actionRef.current?.reload();
                }}
              >
                轮换
              </a>,
              <Popconfirm
                key="del"
                title="确认删除此密钥？删除后无法恢复。"
                onConfirm={async () => {
                  await deleteApiKey(record.id);
                  message.success('已删除');
                  actionRef.current?.reload();
                }}
              >
                <a style={{ color: 'red' }}>删除</a>
              </Popconfirm>,
            ],
          } : {},
        ].filter((c) => Object.keys(c).length > 0)}
      />
      <ModalForm
        title="新建 API 密钥"
        open={open}
        onOpenChange={(v) => { setOpen(v); if (!v) setCreatedKey(null); }}
        modalProps={{ destroyOnClose: true, footer: createdKey ? null : undefined }}
        submitter={createdKey ? false : undefined}
        onFinish={async (values) => {
          const result = await createApiKey(values);
          setCreatedKey(result.rawKey);
          message.success('密钥已创建，请立即复制保存');
          actionRef.current?.reload();
          return false;
        }}
      >
        {createdKey ? (
          <Space direction="vertical" style={{ width: '100%' }}>
            <Typography.Text type="warning">密钥仅显示一次，请立即复制保存。关闭后将无法再次查看完整密钥。</Typography.Text>
            <Typography.Paragraph copyable={{ text: createdKey }} code style={{ wordBreak: 'break-all' }}>
              {createdKey}
            </Typography.Paragraph>
            <Button icon={<CopyOutlined />} onClick={() => copyKey(createdKey)}>复制密钥</Button>
            <Button type="primary" onClick={() => { setOpen(false); setCreatedKey(null); }}>我已保存，关闭</Button>
          </Space>
        ) : (
          <>
            <ProFormText name="name" label="名称" rules={[{ required: true, message: '请输入名称' }]} />
            <ProFormSelect
              name="scopes"
              label="权限范围"
              mode="multiple"
              initialValue={['ai:chat']}
              options={SCOPE_OPTIONS}
              rules={[{ required: true, message: '请选择权限范围' }]}
            />
            <ProFormDateTimePicker name="expiresAt" label="过期时间" tooltip="留空表示永不过期" />
          </>
        )}
      </ModalForm>
      <Modal
        title="轮换后的新密钥"
        open={!!rotateKey}
        onCancel={() => setRotateKey(null)}
        footer={[
          <Button key="close" type="primary" onClick={() => setRotateKey(null)}>我已保存，关闭</Button>,
        ]}
      >
        <Typography.Text type="warning">新密钥仅显示一次。旧密钥在宽限期内仍可使用，到期后自动失效。</Typography.Text>
        <Typography.Paragraph copyable={{ text: rotateKey || '' }} code style={{ marginTop: 16, wordBreak: 'break-all' }}>
          {rotateKey}
        </Typography.Paragraph>
      </Modal>
    </PageContainer>
  );
};

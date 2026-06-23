import { PageContainer, ProTable, ModalForm, ProFormText, ProFormSelect, ProFormSwitch } from '@ant-design/pro-components';
import { Button, message, Table } from 'antd';
import { Link } from '@umijs/max';
import { createProvider, deleteProvider, listProviders, listModels, createModel, updateModel, deleteModel } from '@/services/ai';
import { useRef, useState } from 'react';

export default () => {
  const actionRef = useRef<any>();
  const [open, setOpen] = useState(false);
  const [modelOpen, setModelOpen] = useState(false);
  const [currentProvider, setCurrentProvider] = useState<any>();
  const [currentModel, setCurrentModel] = useState<any>();
  const [models, setModels] = useState<any[]>([]);

  const loadModels = async (providerId: number) => {
    const data = await listModels(providerId);
    setModels(data || []);
  };

  return (
    <PageContainer
      title="模型配置"
      extra={<Link to="/ai/playground">前往对话调试</Link>}
    >
      <ProTable
        actionRef={actionRef}
        rowKey="id"
        search={false}
        request={async () => {
          const data = await listProviders();
          return { data, success: true };
        }}
        expandable={{
          onExpand: async (expanded, record) => {
            if (expanded) {
              setCurrentProvider(record);
              await loadModels(record.id);
            }
          },
          expandedRowRender: () => (
            <Table
              rowKey="id"
              size="small"
              dataSource={models}
              pagination={false}
              columns={[
                { title: '模型 ID', dataIndex: 'model' },
                { title: '显示名', dataIndex: 'label' },
                { title: '启用', dataIndex: 'enabled', render: (v) => (v ? '是' : '否') },
                {
                  title: '操作',
                  render: (_, record) => [
                    <a key="edit" onClick={() => { setCurrentModel(record); setModelOpen(true); }}>编辑</a>,
                    <Link
                      key="play"
                      to={`/ai/playground?provider=${currentProvider?.id}&model=${encodeURIComponent(record.model)}`}
                      style={{ marginLeft: 8 }}
                    >
                      去调试
                    </Link>,
                    <a key="del" style={{ marginLeft: 8 }} onClick={async () => {
                      await deleteModel(record.id);
                      message.success('已删除');
                      if (currentProvider) loadModels(currentProvider.id);
                    }}>删除</a>,
                  ],
                },
              ]}
            />
          ),
        }}
        toolBarRender={() => [
          <Button key="add" type="primary" onClick={() => setOpen(true)}>添加 Provider</Button>,
        ]}
        columns={[
          { title: '编码', dataIndex: 'code' },
          { title: '名称', dataIndex: 'name' },
          { title: '类型', dataIndex: 'type' },
          { title: 'Base URL', dataIndex: 'baseUrl', ellipsis: true },
          { title: '启用', dataIndex: 'enabled', valueType: 'switch' },
          {
            title: '操作',
            valueType: 'option',
            render: (_, record) => [
              <a key="model" onClick={async () => {
                setCurrentProvider(record);
                setCurrentModel(undefined);
                await loadModels(record.id);
                setModelOpen(true);
              }}>添加模型</a>,
              <a key="del" onClick={async () => { await deleteProvider(record.id); actionRef.current?.reload(); }}>删除</a>,
            ],
          },
        ]}
      />
      <ModalForm
        title="添加 Provider"
        open={open}
        onOpenChange={setOpen}
        onFinish={async (values) => {
          await createProvider(values);
          message.success('已添加');
          actionRef.current?.reload();
          return true;
        }}
      >
        <ProFormText name="code" label="编码" rules={[{ required: true }]} />
        <ProFormText name="name" label="名称" rules={[{ required: true }]} />
        <ProFormSelect name="type" label="类型" rules={[{ required: true }]} options={[
          { label: 'OpenAI 兼容', value: 'openai' },
          { label: 'Ollama', value: 'ollama' },
          { label: 'Azure', value: 'azure' },
          { label: 'Anthropic', value: 'anthropic' },
          { label: '自定义', value: 'custom' },
        ]} />
        <ProFormText name="baseUrl" label="Base URL" placeholder="https://api.openai.com/v1" />
        <ProFormText.Password name="apiKey" label="API Key" />
      </ModalForm>
      <ModalForm
        title={currentModel ? '编辑模型' : '添加模型'}
        open={modelOpen}
        onOpenChange={setModelOpen}
        initialValues={currentModel || { enabled: true, capabilities: ['chat'] }}
        onFinish={async (values) => {
          if (!currentProvider) return false;
          const payload = { ...values, providerId: currentProvider.id, capabilities: values.capabilities || ['chat'] };
          if (currentModel) {
            await updateModel(currentModel.id, payload);
          } else {
            await createModel(payload);
          }
          message.success('已保存');
          loadModels(currentProvider.id);
          return true;
        }}
      >
        <ProFormText name="model" label="模型 ID" rules={[{ required: true }]} disabled={!!currentModel} />
        <ProFormText name="label" label="显示名" />
        <ProFormSwitch name="enabled" label="启用" />
      </ModalForm>
    </PageContainer>
  );
};

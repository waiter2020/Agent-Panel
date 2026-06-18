import { PageContainer, ProTable, ModalForm, ProFormText } from '@ant-design/pro-components';
import { Button, Popconfirm, message } from 'antd';
import { listTenants, createTenant, updateTenant, deleteTenant } from '@/services/system';
import { useRef, useState } from 'react';

export default () => {
  const actionRef = useRef<any>();
  const [open, setOpen] = useState(false);
  const [current, setCurrent] = useState<any>();

  return (
    <PageContainer title="租户管理">
      <ProTable
        actionRef={actionRef}
        rowKey="id"
        search={false}
        request={async () => {
          const data = await listTenants();
          return { data, success: true };
        }}
        toolBarRender={() => [
          <Button
            key="add"
            type="primary"
            onClick={() => {
              setCurrent(undefined);
              setOpen(true);
            }}
          >
            新建租户
          </Button>,
        ]}
        columns={[
          { title: '名称', dataIndex: 'name' },
          { title: '编码', dataIndex: 'code' },
          { title: '创建时间', dataIndex: 'createdAt', valueType: 'dateTime' },
          {
            title: '操作',
            valueType: 'option',
            render: (_, record) => [
              <a
                key="edit"
                onClick={() => {
                  setCurrent(record);
                  setOpen(true);
                }}
              >
                编辑
              </a>,
              record.id !== 1 ? (
                <Popconfirm
                  key="del"
                  title="确认删除该租户？"
                  onConfirm={async () => {
                    await deleteTenant(record.id);
                    message.success('已删除');
                    actionRef.current?.reload();
                  }}
                >
                  <a>删除</a>
                </Popconfirm>
              ) : null,
            ],
          },
        ]}
      />
      <ModalForm
        title={current ? '编辑租户' : '新建租户'}
        open={open}
        initialValues={current}
        onOpenChange={setOpen}
        modalProps={{ destroyOnClose: true }}
        onFinish={async (values) => {
          if (current) await updateTenant(current.id, values);
          else await createTenant(values);
          message.success('保存成功');
          actionRef.current?.reload();
          return true;
        }}
      >
        <ProFormText name="name" label="名称" rules={[{ required: true }]} />
        <ProFormText
          name="code"
          label="编码"
          rules={[{ required: true }]}
          disabled={current?.id === 1}
          extra="租户唯一标识，创建后请谨慎修改"
        />
      </ModalForm>
    </PageContainer>
  );
};

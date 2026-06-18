import { PageContainer, ProTable } from '@ant-design/pro-components';
import { Button, Popconfirm, message } from 'antd';
import { listUsers, createUser, updateUser, deleteUser, listRoles, listTenants } from '@/services/system';
import { useRef, useState } from 'react';
import { ModalForm, ProFormSelect, ProFormText } from '@ant-design/pro-components';
import { useAccess } from '@umijs/max';

export default () => {
  const actionRef = useRef<any>();
  const access = useAccess();
  const [roles, setRoles] = useState<any[]>([]);
  const [tenants, setTenants] = useState<any[]>([]);
  const [open, setOpen] = useState(false);
  const [current, setCurrent] = useState<any>();

  const loadRoles = async () => {
    const data = await listRoles();
    setRoles(data.map((r: any) => ({ label: r.name, value: r.id })));
  };

  const loadTenants = async () => {
    if (!access.canManageTenant) return;
    try {
      const data = await listTenants();
      setTenants(data.map((t: any) => ({ label: `${t.name} (${t.code})`, value: t.id })));
    } catch {
      setTenants([]);
    }
  };

  return (
    <PageContainer title="用户管理">
      <ProTable
        actionRef={actionRef}
        rowKey="id"
        request={async (params) => {
          await loadRoles();
          await loadTenants();
          const data = await listUsers({ page: params.current, pageSize: params.pageSize });
          return { data: data.list, total: data.total, success: true };
        }}
        toolBarRender={() => [
          <Button key="add" type="primary" onClick={() => { setCurrent(undefined); setOpen(true); }}>新建用户</Button>,
        ]}
        columns={[
          { title: '用户名', dataIndex: 'username' },
          { title: '昵称', dataIndex: 'nickname' },
          { title: '租户', dataIndex: 'tenantName', hideInTable: !access.canManageTenant },
          { title: '邮箱', dataIndex: 'email' },
          { title: '状态', dataIndex: 'status' },
          {
            title: '操作',
            valueType: 'option',
            render: (_, record) => [
              <a key="edit" onClick={() => { setCurrent(record); setOpen(true); }}>编辑</a>,
              <Popconfirm key="del" title="确认删除?" onConfirm={async () => { await deleteUser(record.id); message.success('已删除'); actionRef.current?.reload(); }}>
                <a>删除</a>
              </Popconfirm>,
            ],
          },
        ]}
      />
      <ModalForm
        title={current ? '编辑用户' : '新建用户'}
        open={open}
        initialValues={current}
        onOpenChange={setOpen}
        onFinish={async (values) => {
          if (current) await updateUser(current.id, values);
          else await createUser(values);
          message.success('保存成功');
          actionRef.current?.reload();
          return true;
        }}
      >
        {!current && <ProFormText name="username" label="用户名" rules={[{ required: true }]} />}
        {!current && <ProFormText.Password name="password" label="密码" />}
        <ProFormText name="nickname" label="昵称" />
        <ProFormText name="email" label="邮箱" />
        {access.canManageTenant && (
          <ProFormSelect name="tenantId" label="租户" options={tenants} rules={[{ required: true }]} />
        )}
        <ProFormSelect name="roleIds" label="角色" mode="multiple" options={roles} />
      </ModalForm>
    </PageContainer>
  );
};

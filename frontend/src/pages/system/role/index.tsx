import { PageContainer, ProTable, ModalForm, ProFormText, ProFormSelect } from '@ant-design/pro-components';

import { listRoles, listPermissions, updateRole } from '@/services/system';

import { Button, message } from 'antd';

import { useRef, useState } from 'react';



export default () => {

  const actionRef = useRef<any>();

  const [permissions, setPermissions] = useState<any[]>([]);

  const [editing, setEditing] = useState<any>();



  const loadPermissions = async () => {

    if (!permissions.length) {

      setPermissions(await listPermissions());

    }

  };



  return (

    <PageContainer title="角色管理">

      <ProTable

        actionRef={actionRef}

        rowKey="id"

        search={false}

        request={async () => {

          await loadPermissions();

          const data = await listRoles();

          return { data, success: true };

        }}

        columns={[

          { title: '编码', dataIndex: 'code' },

          { title: '名称', dataIndex: 'name' },

          { title: '描述', dataIndex: 'description' },

          { title: '状态', dataIndex: 'status' },

          {

            title: '操作',

            valueType: 'option',

            render: (_, record) => [

              <Button

                key="edit"

                type="link"

                onClick={() => setEditing(record)}

              >

                编辑权限

              </Button>,

            ],

          },

        ]}

      />

      <ModalForm

        title={`编辑角色权限 - ${editing?.name || ''}`}

        open={!!editing}

        modalProps={{ destroyOnClose: true, onCancel: () => setEditing(undefined) }}

        initialValues={{ name: editing?.name, description: editing?.description, permissionIds: editing?.permissionIds }}

        onFinish={async (values) => {

          await updateRole(editing.id, {

            name: values.name,

            description: values.description,

            permissionIds: values.permissionIds,

          });

          message.success('角色已更新');

          setEditing(undefined);

          actionRef.current?.reload();

          return true;

        }}

      >

        <ProFormText name="name" label="名称" rules={[{ required: true }]} />

        <ProFormText name="description" label="描述" />

        <ProFormSelect

          name="permissionIds"

          label="权限"

          mode="multiple"

          options={permissions.map((p) => ({ label: `${p.name} (${p.code})`, value: p.id }))}

        />

      </ModalForm>

    </PageContainer>

  );

};


import { PageContainer, ProTable } from '@ant-design/pro-components';
import { Button, Tag, message } from 'antd';
import { history, useAccess } from '@umijs/max';
import { listApps, deleteApp, deployApp, startApp, stopApp } from '@/services/app';
import { useRef } from 'react';

const statusMap: Record<string, string> = {
  created: '已创建',
  deploying: '部署中',
  running: '运行中',
  stopped: '已停止',
  error: '错误',
};

export default () => {
  const actionRef = useRef<any>();
  const access = useAccess();
  return (
    <PageContainer title="应用列表">
      <ProTable
        actionRef={actionRef}
        rowKey="id"
        search={false}
        request={async () => {
          const data = await listApps();
          return { data, success: true };
        }}
        toolBarRender={() => [
          access.canWriteApp && (
            <Button key="add" type="primary" onClick={() => history.push('/app/detail/new')}>新建应用</Button>
          ),
        ].filter(Boolean)}
        columns={[
          { title: '名称', dataIndex: 'name' },
          { title: '模板', dataIndex: 'templateName' },
          { title: '镜像', dataIndex: 'image' },
          { title: '标签', dataIndex: 'tag' },
          {
            title: '状态',
            dataIndex: 'status',
            render: (_, r) => <Tag color={r.status === 'running' ? 'green' : 'default'}>{statusMap[r.status] || r.status}</Tag>,
          },
          {
            title: '操作',
            valueType: 'option',
            render: (_, record) => [
              <a key="detail" onClick={() => history.push(`/app/detail/${record.id}`)}>详情</a>,
              access.canDeployApp && (
                <a key="deploy" onClick={async () => { await deployApp(record.id); message.success('部署已触发'); actionRef.current?.reload(); }}>部署</a>
              ),
              access.canOperateApp && (
                record.status === 'running'
                  ? <a key="stop" onClick={async () => { await stopApp(record.id); actionRef.current?.reload(); }}>停止</a>
                  : <a key="start" onClick={async () => { await startApp(record.id); actionRef.current?.reload(); }}>启动</a>
              ),
              access.canDeleteApp && (
                <a key="del" onClick={async () => { await deleteApp(record.id); actionRef.current?.reload(); }}>删除</a>
              ),
            ].filter(Boolean),
          },
        ]}
      />
    </PageContainer>
  );
};

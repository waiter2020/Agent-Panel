import { PageContainer, ProTable } from '@ant-design/pro-components';
import { listMenus } from '@/services/system';
import { useRef } from 'react';

export default () => {
  const actionRef = useRef<any>();
  return (
    <PageContainer title="菜单管理">
      <ProTable
        actionRef={actionRef}
        rowKey="id"
        search={false}
        request={async () => {
          const data = await listMenus();
          return { data, success: true };
        }}
        columns={[
          { title: '名称', dataIndex: 'name' },
          { title: '路径', dataIndex: 'path' },
          { title: '图标', dataIndex: 'icon' },
          { title: '组件', dataIndex: 'component' },
          { title: '排序', dataIndex: 'orderNo' },
        ]}
      />
    </PageContainer>
  );
};

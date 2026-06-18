import { PageContainer, ProTable } from '@ant-design/pro-components';
import { listAuditLogs } from '@/services/system';
import { useRef } from 'react';

export default () => {
  const actionRef = useRef<any>();

  return (
    <PageContainer title="审计日志">
      <ProTable
        actionRef={actionRef}
        rowKey="id"
        search={false}
        request={async (params) => {
          const data = await listAuditLogs({ page: params.current, pageSize: params.pageSize });
          return { data: data.list, total: data.total, success: true };
        }}
        columns={[
          { title: '时间', dataIndex: 'at', valueType: 'dateTime', width: 180 },
          { title: '用户', dataIndex: 'username', width: 120 },
          { title: '操作', dataIndex: 'action', width: 100 },
          { title: '资源类型', dataIndex: 'resourceType', width: 120 },
          { title: '资源 ID', dataIndex: 'resourceId', width: 100 },
          { title: 'IP', dataIndex: 'ip', width: 140 },
          { title: '结果', dataIndex: 'result', width: 80 },
        ]}
      />
    </PageContainer>
  );
};

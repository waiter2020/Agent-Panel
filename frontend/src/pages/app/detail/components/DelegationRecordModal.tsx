import { ModalForm, ProFormSelect, ProFormTextArea } from '@ant-design/pro-components';
import { message } from 'antd';
import { useEffect, useState } from 'react';
import { recordDelegation } from '@/services/delegation';
import { getTopology } from '@/services/topology';

type Props = {
  open: boolean;
  topologyId?: number;
  defaultParentAppId?: number;
  onOpenChange: (open: boolean) => void;
  onSuccess?: () => void;
};

export default function DelegationRecordModal({
  open,
  topologyId,
  defaultParentAppId,
  onOpenChange,
  onSuccess,
}: Props) {
  const [nodes, setNodes] = useState<any[]>([]);

  useEffect(() => {
    if (!open || !topologyId) {
      setNodes([]);
      return;
    }
    getTopology(topologyId)
      .then((topology) => setNodes(topology?.nodes || []))
      .catch(() => setNodes([]));
  }, [open, topologyId]);

  return (
    <ModalForm
      title="记录委派"
      open={open}
      onOpenChange={onOpenChange}
      modalProps={{ destroyOnClose: true }}
      initialValues={{
        parentAppId: defaultParentAppId,
        status: 'completed',
      }}
      onFinish={async (values) => {
        if (!topologyId) {
          message.error('该应用未加入拓扑，无法记录委派');
          return false;
        }
        await recordDelegation({
          topologyId,
          parentAppId: values.parentAppId,
          childAppId: values.childAppId,
          taskSummary: values.taskSummary,
          status: values.status || 'completed',
          result: values.result ? { note: values.result } : {},
        });
        message.success('委派已记录');
        onSuccess?.();
        return true;
      }}
    >
      <ProFormSelect
        name="parentAppId"
        label="父应用（委派方）"
        rules={[{ required: true }]}
        options={nodes.map((n) => ({
          label: `${n.applicationName} (${n.role})`,
          value: n.applicationId,
        }))}
      />
      <ProFormSelect
        name="childAppId"
        label="子应用（被委派方）"
        rules={[{ required: true }]}
        options={nodes.map((n) => ({
          label: `${n.applicationName} (${n.role})`,
          value: n.applicationId,
        }))}
      />
      <ProFormTextArea name="taskSummary" label="任务摘要" rules={[{ required: true }]} />
      <ProFormSelect
        name="status"
        label="状态"
        options={[
          { label: '进行中', value: 'running' },
          { label: '已完成', value: 'completed' },
          { label: '失败', value: 'failed' },
          { label: '已取消', value: 'cancelled' },
        ]}
      />
      <ProFormTextArea name="result" label="结果备注（可选）" />
    </ModalForm>
  );
}

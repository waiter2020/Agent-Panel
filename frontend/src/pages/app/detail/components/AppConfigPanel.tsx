import { ProForm, ProFormDigit, ProFormSelect, ProFormText, ProFormTextArea } from '@ant-design/pro-components';
import { Card, message } from 'antd';
import { useEffect } from 'react';
import { updateApp } from '@/services/app';

type Props = {
  appId: number;
  app: any;
  onSaved: () => void;
};

function buildInitialValues(app: any) {
  return {
    image: app?.image,
    tag: app?.tag,
    remark: app?.remark,
    replicas: app?.replicas || 1,
    runtimeProvider: app?.runtimeProvider || 'docker',
    cpu: app?.resources?.cpu || '1',
    memory: app?.resources?.memory || '1Gi',
  };
}

export default function AppConfigPanel({ appId, app, onSaved }: Props) {
  const [form] = ProForm.useForm();

  useEffect(() => {
    if (app) {
      form.setFieldsValue(buildInitialValues(app));
    }
  }, [app, appId, form]);

  return (
    <Card title="Agent 配置">
      <ProForm
        form={form}
        key={appId}
        initialValues={buildInitialValues(app)}
        onFinish={async (values) => {
          await updateApp(appId, {
            image: values.image,
            tag: values.tag,
            remark: values.remark,
            replicas: values.replicas,
            runtimeProvider: values.runtimeProvider,
            resources: {
              cpu: values.cpu,
              memory: values.memory,
            },
          });
          message.success('配置已保存');
          message.warning('资源配置变更后需要重新部署才能生效');
          onSaved();
        }}
        submitter={{ searchConfig: { submitText: '保存配置' } }}
      >
        <ProFormText name="image" label="镜像" rules={[{ required: true }]} />
        <ProFormText name="tag" label="标签" rules={[{ required: true }]} />
        <ProFormSelect
          name="runtimeProvider"
          label="运行时"
          options={[
            { label: 'Docker', value: 'docker' },
            { label: 'Kubernetes', value: 'k8s' },
          ]}
        />
        <ProFormDigit name="replicas" label="副本数" min={1} fieldProps={{ precision: 0 }} />
        <ProFormText name="cpu" label="CPU 限制" tooltip="如 0.5、1、2" rules={[{ required: true }]} />
        <ProFormText name="memory" label="内存限制" tooltip="如 512Mi、1Gi" rules={[{ required: true }]} />
        <ProFormTextArea name="remark" label="备注" />
      </ProForm>
    </Card>
  );
}

import { PageContainer } from '@ant-design/pro-components';
import { Alert, Card, Form, Input, Button, message, Spin, Typography, Divider } from 'antd';
import { getSettings, updateSettings } from '@/services/system';
import { useAccess } from '@umijs/max';
import { useEffect, useState } from 'react';

const READONLY_PREFIXES = ['storage.', 'runtime.'];

export default () => {
  const [form] = Form.useForm();
  const access = useAccess();
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [settings, setSettings] = useState<Record<string, string>>({});

  const load = async () => {
    setLoading(true);
    try {
      const data = await getSettings();
      setSettings(data);
      form.setFieldsValue(data);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { load(); }, []);

  const isReadOnly = (key: string) => READONLY_PREFIXES.some((p) => key.startsWith(p));

  const onSave = async () => {
    const values = await form.validateFields();
    const editable: Record<string, string> = {};
    Object.entries(values).forEach(([key, value]) => {
      if (!isReadOnly(key)) {
        editable[key] = String(value ?? '');
      }
    });
    setSaving(true);
    try {
      const updated = await updateSettings(editable);
      setSettings(updated);
      form.setFieldsValue(updated);
      message.success('保存成功');
    } finally {
      setSaving(false);
    }
  };

  const dbKeys = Object.keys(settings).filter((k) => !isReadOnly(k));
  const readonlyKeys = Object.keys(settings).filter((k) => isReadOnly(k));

  return (
    <PageContainer title="系统设置">
      <Spin spinning={loading}>
        <Alert
          type="info"
          showIcon
          style={{ marginBottom: 16 }}
          message="存储配置（storage.*）与运行时 Provider（runtime.*）由环境变量注入，此处只读展示。修改后需重启服务生效。"
        />
        <Form form={form} layout="vertical" onFinish={onSave}>
          {dbKeys.length > 0 && (
            <Card title="面板配置" style={{ marginBottom: 16 }}>
              {dbKeys.map((key) => (
                <Form.Item key={key} name={key} label={key}>
                  <Input disabled={!access.canWriteSettings} />
                </Form.Item>
              ))}
            </Card>
          )}
          {readonlyKeys.length > 0 && (
            <Card title="运行时与存储（只读）">
              {readonlyKeys.map((key) => (
                <Form.Item key={key} name={key} label={key}>
                  <Input disabled />
                </Form.Item>
              ))}
              <Typography.Text type="secondary">
                K8s 模式下请设置环境变量 <Typography.Text code>K8S_ACCESS_HOST</Typography.Text> 为节点 IP 或负载均衡地址，以便应用详情页展示 NodePort 访问地址。
              </Typography.Text>
            </Card>
          )}
          {access.canWriteSettings && (
            <>
              <Divider />
              <Button type="primary" htmlType="submit" loading={saving}>保存设置</Button>
            </>
          )}
        </Form>
      </Spin>
    </PageContainer>
  );
};

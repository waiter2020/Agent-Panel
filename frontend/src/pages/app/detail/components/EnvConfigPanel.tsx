import { Button, Card, Form, Input, Space, Switch, Table, message } from 'antd';
import { PlusOutlined } from '@ant-design/icons';
import { useEffect, useMemo, useState } from 'react';
import { updateApp } from '@/services/app';

type EnvItem = { key: string; value: string; secret: boolean };

type Props = {
  appId: number;
  envSchema: any[];
  env: EnvItem[];
  onSaved: () => void;
};

function isMaskedValue(value?: string) {
  return !value || value.includes('****');
}

export default function EnvConfigPanel({ appId, envSchema, env, onSaved }: Props) {
  const [form] = Form.useForm();
  const schemaKeys = useMemo(
    () => new Set((envSchema || []).map((s) => s.key)),
    [envSchema],
  );
  const [customEnv, setCustomEnv] = useState<EnvItem[]>([]);

  useEffect(() => {
    const values: Record<string, string> = {};
    (env || []).forEach((item) => {
      if (schemaKeys.has(item.key)) {
        values[item.key] = item.value;
      }
    });
    form.setFieldsValue(values);
    setCustomEnv((env || []).filter((item) => !schemaKeys.has(item.key)));
  }, [env, envSchema, form, schemaKeys]);

  const handleSave = async () => {
    const schemaValues = await form.validateFields();
    const schemaEnv = (envSchema || []).map((schema) => {
      const raw = String(schemaValues[schema.key] ?? '');
      return {
        key: schema.key,
        value: schema.secret && isMaskedValue(raw) ? '' : raw,
        secret: schema.secret ?? false,
      };
    });
    const mergedCustom = customEnv
      .filter((item) => item.key?.trim())
      .map((item) => ({
        key: item.key.trim(),
        value: item.secret && isMaskedValue(item.value) ? '' : String(item.value ?? ''),
        secret: item.secret,
      }));
    await updateApp(appId, { env: [...schemaEnv, ...mergedCustom] });
    message.success('环境变量已保存');
    message.warning('环境变量已更新，需要重新部署才能生效');
    onSaved();
  };

  return (
    <Card extra={<Button type="primary" onClick={handleSave}>保存</Button>}>
      <Form form={form} layout="vertical">
        {(envSchema || []).length === 0 && <div style={{ marginBottom: 16 }}>该模板无预定义环境变量</div>}
        {(envSchema || []).map((schema) => (
          <Form.Item
            key={schema.key}
            name={schema.key}
            label={schema.label || schema.key}
            tooltip={schema.description}
            rules={schema.required ? [{ required: true, message: `请填写${schema.label || schema.key}` }] : undefined}
          >
            {schema.secret ? (
              <Input.Password placeholder="留空表示不修改" />
            ) : (
              <Input placeholder={schema.description} />
            )}
          </Form.Item>
        ))}
      </Form>

      <div style={{ marginTop: 8, marginBottom: 8, fontWeight: 600 }}>自定义环境变量</div>
      <Table
        size="small"
        pagination={false}
        rowKey={(record, index) => `${record.key}-${index}`}
        dataSource={customEnv}
        columns={[
          {
            title: 'Key',
            dataIndex: 'key',
            render: (_, record, index) => (
              <Input
                value={record.key}
                placeholder="MY_ENV_KEY"
                onChange={(e) => {
                  const next = [...customEnv];
                  next[index] = { ...next[index], key: e.target.value };
                  setCustomEnv(next);
                }}
              />
            ),
          },
          {
            title: 'Value',
            dataIndex: 'value',
            render: (_, record, index) => (
              record.secret ? (
                <Input.Password
                  value={record.value}
                  placeholder="留空表示不修改"
                  onChange={(e) => {
                    const next = [...customEnv];
                    next[index] = { ...next[index], value: e.target.value };
                    setCustomEnv(next);
                  }}
                />
              ) : (
                <Input
                  value={record.value}
                  onChange={(e) => {
                    const next = [...customEnv];
                    next[index] = { ...next[index], value: e.target.value };
                    setCustomEnv(next);
                  }}
                />
              )
            ),
          },
          {
            title: 'Secret',
            dataIndex: 'secret',
            width: 90,
            render: (_, record, index) => (
              <Switch
                checked={record.secret}
                onChange={(checked) => {
                  const next = [...customEnv];
                  next[index] = { ...next[index], secret: checked };
                  setCustomEnv(next);
                }}
              />
            ),
          },
          {
            title: '操作',
            width: 80,
            render: (_, __, index) => (
              <a style={{ color: 'red' }} onClick={() => setCustomEnv(customEnv.filter((_, i) => i !== index))}>
                删除
              </a>
            ),
          },
        ]}
      />
      <Button
        type="dashed"
        icon={<PlusOutlined />}
        style={{ marginTop: 12 }}
        onClick={() => setCustomEnv([...customEnv, { key: '', value: '', secret: false }])}
      >
        添加变量
      </Button>
    </Card>
  );
}

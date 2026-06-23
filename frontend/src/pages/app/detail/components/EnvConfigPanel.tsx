import { Button, Card, Form, Input, message } from 'antd';
import { useEffect, useMemo, useState } from 'react';
import { updateApp } from '@/services/app';
import CustomEnvTable, { EnvItem, hasDuplicateEnvKeys } from './CustomEnvTable';

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
    if (hasDuplicateEnvKeys(customEnv)) {
      message.error('自定义环境变量存在重复 Key');
      return;
    }
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
      <CustomEnvTable value={customEnv} onChange={setCustomEnv} />
    </Card>
  );
}

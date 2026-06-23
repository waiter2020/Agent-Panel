import { Button, Input, Switch, Table } from 'antd';
import { PlusOutlined } from '@ant-design/icons';
import { useMemo } from 'react';

export type EnvItem = { key: string; value: string; secret: boolean };

type Props = {
  value: EnvItem[];
  onChange: (items: EnvItem[]) => void;
  secretPlaceholder?: string;
};

export function hasDuplicateEnvKeys(items: EnvItem[]): boolean {
  const seen = new Set<string>();
  for (const item of items) {
    const key = item.key?.trim();
    if (!key) continue;
    if (seen.has(key)) return true;
    seen.add(key);
  }
  return false;
}

export default function CustomEnvTable({ value, onChange, secretPlaceholder = '留空表示不修改' }: Props) {
  const duplicateKeys = useMemo(() => hasDuplicateEnvKeys(value), [value]);

  return (
    <>
      {duplicateKeys && (
        <div style={{ color: '#faad14', marginBottom: 8 }}>存在重复的环境变量 Key，请修改后再保存</div>
      )}
      <Table
        size="small"
        pagination={false}
        rowKey={(record, index) => `${record.key}-${index}`}
        dataSource={value}
        columns={[
          {
            title: 'Key',
            dataIndex: 'key',
            render: (_, record, index) => (
              <Input
                value={record.key}
                placeholder="MY_ENV_KEY"
                onChange={(e) => {
                  const next = [...value];
                  next[index] = { ...next[index], key: e.target.value };
                  onChange(next);
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
                  placeholder={secretPlaceholder}
                  onChange={(e) => {
                    const next = [...value];
                    next[index] = { ...next[index], value: e.target.value };
                    onChange(next);
                  }}
                />
              ) : (
                <Input
                  value={record.value}
                  onChange={(e) => {
                    const next = [...value];
                    next[index] = { ...next[index], value: e.target.value };
                    onChange(next);
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
                  const next = [...value];
                  next[index] = { ...next[index], secret: checked };
                  onChange(next);
                }}
              />
            ),
          },
          {
            title: '操作',
            width: 80,
            render: (_, __, index) => (
              <a style={{ color: 'red' }} onClick={() => onChange(value.filter((_, i) => i !== index))}>
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
        onClick={() => onChange([...value, { key: '', value: '', secret: false }])}
      >
        添加变量
      </Button>
    </>
  );
}

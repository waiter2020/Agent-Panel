import { Table, Typography } from 'antd';
import { useEffect, useState } from 'react';
import { downloadSkill, listSkills } from '@/services/skill';

type Props = {
  appId: number;
  topologyId?: number;
};

export default function AppSkillFilesPanel({ appId, topologyId }: Props) {
  const [skills, setSkills] = useState<any[]>([]);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    if (!topologyId) {
      setSkills([]);
      return;
    }
    setLoading(true);
    listSkills({ topologyId, applicationId: appId })
      .then((data) => setSkills((data || []).filter((s: any) => s.filePath)))
      .catch(() => setSkills([]))
      .finally(() => setLoading(false));
  }, [appId, topologyId]);

  if (!topologyId) {
    return null;
  }

  return (
    <div>
      <Typography.Text strong style={{ display: 'block', marginBottom: 8 }}>关联技能附件</Typography.Text>
      <Table
      rowKey="id"
      size="small"
      loading={loading}
      pagination={false}
      dataSource={skills}
      locale={{ emptyText: '暂无技能附件' }}
      columns={[
        { title: '技能名称', dataIndex: 'name' },
        { title: '文件路径', dataIndex: 'filePath', ellipsis: true },
        {
          title: '操作',
          render: (_, record) => (
            <a onClick={async () => {
              const { url } = await downloadSkill(record.id);
              window.open(url);
            }}>下载</a>
          ),
        },
      ]}
    />
    </div>
  );
}

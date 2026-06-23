import { Alert, Card, Descriptions, Space, Tag, Typography } from 'antd';
import TypePanelQuickLinks from './TypePanelQuickLinks';

export default function HermesPanel({ app, health, skillsContext }: { app: any; health?: any; skillsContext?: any }) {
  const skillCount = skillsContext?.skills?.length ?? skillsContext?.localSkills?.length ?? 0;
  return (
    <Card title="Hermes Agent 推理/进化">
      <Descriptions column={1} size="small">
        <Descriptions.Item label="模板类型">Hermes 自我进化型推理 Agent</Descriptions.Item>
        <Descriptions.Item label="Dashboard 端口">
          {(app?.ports || []).find((p: any) => p.name === 'dashboard')?.containerPort || 9119}
          <Typography.Text type="secondary">（通过面板代理访问，无需暴露 NodePort）</Typography.Text>
        </Descriptions.Item>
        <Descriptions.Item label="Gateway 端口">
          {(app?.ports || []).find((p: any) => p.name === 'gateway')?.containerPort || 3000}
        </Descriptions.Item>
        <Descriptions.Item label="Skills 数量">{skillCount}</Descriptions.Item>
        <Descriptions.Item label="Agent 健康">
          {health ? (
            <Tag color={health.healthy ? 'green' : 'orange'}>{health.message || (health.healthy ? '正常' : '异常')}</Tag>
          ) : '-'}
        </Descriptions.Item>
      </Descriptions>
      <Space direction="vertical" style={{ width: '100%', marginTop: 16 }}>
        <Alert type="info" showIcon message="Hermes 内置 Dashboard 可通过 Dashboard Tab 内嵌访问" />
        <Alert type="success" showIcon message="Skills 与 MCP 端点可在对应 Tab 中管理，支持拓扑共享技能注入" />
      </Space>
      <TypePanelQuickLinks appId={app?.id} />
    </Card>
  );
}

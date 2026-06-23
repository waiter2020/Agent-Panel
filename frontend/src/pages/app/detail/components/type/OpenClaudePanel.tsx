import { Alert, Card, Descriptions, Tag, Typography } from 'antd';
import TypePanelQuickLinks from './TypePanelQuickLinks';

export default function OpenClaudePanel({ app, health }: { app: any; health?: any }) {
  const apiPort = (app?.ports || []).find((p: any) => p.name === 'api')?.containerPort || 8080;
  return (
    <Card title="openclaude API 运行时">
      <Descriptions column={1} size="small">
        <Descriptions.Item label="模板类型">Claude 系列 Agent 运行时</Descriptions.Item>
        <Descriptions.Item label="API 端口">{apiPort}</Descriptions.Item>
        <Descriptions.Item label="数据目录">/data</Descriptions.Item>
        <Descriptions.Item label="Agent 健康">
          {health ? (
            <Tag color={health.healthy ? 'green' : 'orange'}>{health.message || (health.healthy ? '正常' : '异常')}</Tag>
          ) : (
            <Tag>未检测</Tag>
          )}
        </Descriptions.Item>
      </Descriptions>
      <Alert
        style={{ marginTop: 16 }}
        type="warning"
        showIcon
        message="占位镜像说明"
        description="当前 openclaude 模板使用占位镜像。部署真实 Claude 运行时后，可通过 API Tab 代理访问健康检查端点，并在环境变量 Tab 配置 ANTHROPIC_API_KEY。"
      />
      {health?.checkedUrl && (
        <Typography.Paragraph type="secondary" style={{ marginTop: 12 }}>
          健康检查 URL: {health.checkedUrl}
        </Typography.Paragraph>
      )}
      <TypePanelQuickLinks appId={app?.id} />
    </Card>
  );
}

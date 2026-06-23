import { Alert, Card, Descriptions, Tag, Typography } from 'antd';
import TypePanelQuickLinks from './TypePanelQuickLinks';

export default function OpenClawPanel({ app, health }: { app: any; health?: any }) {
  return (
    <Card title="OpenClaw 网关编排">
      <Descriptions column={1} size="small">
        <Descriptions.Item label="模板类型">OpenClaw 网关型多通道 Agent 编排器</Descriptions.Item>
        <Descriptions.Item label="Gateway 端口">
          {(app?.ports || []).find((p: any) => p.name === 'gateway')?.containerPort || 18789}
        </Descriptions.Item>
        <Descriptions.Item label="数据目录">/home/node/.openclaw</Descriptions.Item>
        <Descriptions.Item label="Agent 健康">
          {health ? (
            <Tag color={health.healthy ? 'green' : 'orange'}>{health.message || (health.healthy ? '正常' : '异常')}</Tag>
          ) : '-'}
        </Descriptions.Item>
      </Descriptions>
      <Alert
        style={{ marginTop: 16 }}
        type="info"
        showIcon
        message="OpenClaw 管理提示"
        description="通过 Gateway Tab 可内嵌访问 OpenClaw Web UI（Panel 代理自动认证，无需手动输入令牌）；配置文件位于数据卷中的 openclaw.json 等路径，可通过数据卷 Tab 在线编辑。"
      />
      <TypePanelQuickLinks appId={app?.id} />
    </Card>
  );
}

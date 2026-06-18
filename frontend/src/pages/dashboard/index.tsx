import {
  AppstoreOutlined,
  CloudServerOutlined,
  FolderOutlined,
  PauseCircleOutlined,
  RobotOutlined,
  ThunderboltOutlined,
} from '@ant-design/icons';
import { PageContainer, StatisticCard } from '@ant-design/pro-components';
import { Card, Col, Row, Typography } from 'antd';
import { history, useRequest } from '@umijs/max';
import { listApps } from '@/services/app';
import './index.less';

const quickLinks = [
  {
    title: '应用中心',
    desc: '部署与管理 AI Agent 应用',
    icon: <AppstoreOutlined className="quick-link-icon" style={{ color: '#667eea' }} />,
    path: '/app/list',
  },
  {
    title: '模型网关',
    desc: '配置模型提供商与对话调试',
    icon: <RobotOutlined className="quick-link-icon" style={{ color: '#52c41a' }} />,
    path: '/ai/models',
  },
  {
    title: '文件管理',
    desc: '对象存储文件浏览与管理',
    icon: <FolderOutlined className="quick-link-icon" style={{ color: '#fa8c16' }} />,
    path: '/files',
  },
];

export default () => {
  const { data: apps = [] } = useRequest(listApps);
  const running = apps.filter((a: { status?: string }) => a.status === 'running').length;
  const stopped = apps.length - running;

  return (
    <PageContainer title="仪表盘" subTitle="Agent Panel 概览">
      <Row gutter={[16, 16]} className="dashboard-stats">
        <Col xs={24} sm={8}>
          <StatisticCard
            className="stat-card"
            statistic={{
              title: '应用总数',
              value: apps.length,
              icon: <CloudServerOutlined className="stat-icon" style={{ color: '#667eea' }} />,
            }}
          />
        </Col>
        <Col xs={24} sm={8}>
          <StatisticCard
            className="stat-card"
            statistic={{
              title: '运行中',
              value: running,
              status: 'success',
              icon: <ThunderboltOutlined className="stat-icon" style={{ color: '#52c41a' }} />,
            }}
          />
        </Col>
        <Col xs={24} sm={8}>
          <StatisticCard
            className="stat-card"
            statistic={{
              title: '已停止',
              value: stopped,
              icon: <PauseCircleOutlined className="stat-icon" style={{ color: '#8c8c8c' }} />,
            }}
          />
        </Col>
      </Row>

      <Row gutter={[16, 16]} className="quick-links">
        {quickLinks.map((link) => (
          <Col xs={24} sm={8} key={link.path}>
            <Card
              className="quick-link-card"
              hoverable
              onClick={() => history.push(link.path)}
            >
              {link.icon}
              <div className="quick-link-title">{link.title}</div>
              <div className="quick-link-desc">{link.desc}</div>
            </Card>
          </Col>
        ))}
      </Row>

      <Card className="welcome-card">
        <Typography.Title level={4} className="welcome-title">
          欢迎使用 Agent Panel
        </Typography.Title>
        <Typography.Paragraph className="welcome-desc">
          统一管理 OpenClaw、Hermes、openclaude 等 AI Agent 的部署、监控与模型网关。
          通过左侧导航可访问应用中心、文件管理、模型网关和系统管理等功能模块。
        </Typography.Paragraph>
      </Card>
    </PageContainer>
  );
};

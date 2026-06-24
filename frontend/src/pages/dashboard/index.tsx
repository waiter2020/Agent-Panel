import {
  AppstoreOutlined,
  CloudServerOutlined,
  FolderOutlined,
  LoadingOutlined,
  PauseCircleOutlined,
  RobotOutlined,
  ShareAltOutlined,
  ThunderboltOutlined,
  ExperimentOutlined,
} from '@ant-design/icons';
import { PageContainer, StatisticCard } from '@ant-design/pro-components';
import { Button, Card, Col, Result, Row, Space, Spin, Tag, Typography } from 'antd';
import { history, useAccess } from '@umijs/max';
import { useCallback, useEffect, useState } from 'react';
import { defaultDashboardStats, getDashboardStats } from '@/services/dashboard';
import type { DashboardStatsDto, RecentAppDto } from '@/services/types/app-management';
import './index.less';

const quickLinks = [
  { title: '应用中心', desc: '部署与管理 AI Agent 应用', icon: <AppstoreOutlined className="quick-link-icon" style={{ color: '#667eea' }} />, path: '/app/list' },
  { title: '运维 Kanban', desc: '应用状态看板与列间流转', icon: <ThunderboltOutlined className="quick-link-icon" style={{ color: '#722ed1' }} />, path: '/app/kanban' },
  { title: '端口全景', desc: '端口占用与冲突检测', icon: <CloudServerOutlined className="quick-link-icon" style={{ color: '#13c2c2' }} />, path: '/app/ports' },
  { title: '协同拓扑', desc: '多 Agent 协同部署与链路', icon: <ShareAltOutlined className="quick-link-icon" style={{ color: '#eb2f96' }} />, path: '/app/topology' },
  { title: '共享记忆', desc: '跨 Agent 向量/关键词检索', icon: <ExperimentOutlined className="quick-link-icon" style={{ color: '#2f54eb' }} />, path: '/app/memory' },
  { title: '模型网关', desc: '配置模型提供商', icon: <RobotOutlined className="quick-link-icon" style={{ color: '#52c41a' }} />, path: '/ai/models' },
  { title: '对话调试', desc: 'Playground 流式对话测试', icon: <RobotOutlined className="quick-link-icon" style={{ color: '#389e0d' }} />, path: '/ai/playground' },
  { title: '文件管理', desc: '对象存储与应用数据卷', icon: <FolderOutlined className="quick-link-icon" style={{ color: '#fa8c16' }} />, path: '/files' },
];

export default () => {
  const access = useAccess();
  const [stats, setStats] = useState<DashboardStatsDto>(defaultDashboardStats);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<string>();

  const load = useCallback(async () => {
    setLoading(true);
    setLoadError(undefined);
    try {
      const data = await getDashboardStats();
      setStats({ ...defaultDashboardStats, ...data });
    } catch (e: unknown) {
      const message = e instanceof Error ? e.message : '加载失败';
      setLoadError(message);
      setStats(defaultDashboardStats);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  if (loading && !loadError) {
    return (
      <PageContainer title="运营驾驶舱">
        <div style={{ textAlign: 'center', padding: 48 }}><Spin size="large" /></div>
      </PageContainer>
    );
  }

  if (loadError) {
    return (
      <PageContainer title="运营驾驶舱">
        <Result status="error" title="加载失败" subTitle={loadError} extra={<Button onClick={load}>重试</Button>} />
      </PageContainer>
    );
  }

  return (
    <PageContainer title="运营驾驶舱" subTitle="多维托管管理概览">
      <Row gutter={[16, 16]} className="dashboard-stats">
        <Col xs={24} sm={12} lg={6}>
          <StatisticCard className="stat-card" statistic={{ title: '应用总数', value: stats.totalApps, icon: <CloudServerOutlined className="stat-icon" style={{ color: '#667eea' }} /> }} />
        </Col>
        <Col xs={24} sm={12} lg={6}>
          <StatisticCard className="stat-card" statistic={{ title: '运行中', value: stats.runningApps, status: 'success', icon: <ThunderboltOutlined className="stat-icon" style={{ color: '#52c41a' }} /> }} />
        </Col>
        <Col xs={24} sm={12} lg={6}>
          <StatisticCard className="stat-card" statistic={{ title: '部署中', value: stats.deployingApps, status: 'processing', icon: <LoadingOutlined className="stat-icon" style={{ color: '#1677ff' }} /> }} />
        </Col>
        <Col xs={24} sm={12} lg={6}>
          <StatisticCard className="stat-card" statistic={{ title: '已停止', value: stats.stoppedApps, icon: <PauseCircleOutlined className="stat-icon" style={{ color: '#8c8c8c' }} /> }} />
        </Col>
      </Row>

      <Row gutter={[16, 16]} style={{ marginTop: 16 }}>
        <Col xs={24} sm={12} lg={6}>
          <StatisticCard className="stat-card" statistic={{ title: '待部署', value: stats.createdApps ?? 0, icon: <AppstoreOutlined className="stat-icon" style={{ color: '#1677ff' }} /> }} />
        </Col>
        <Col xs={24} sm={12} lg={6}>
          <StatisticCard className="stat-card" statistic={{ title: '异常', value: stats.errorApps ?? 0, status: 'error', icon: <CloudServerOutlined className="stat-icon" style={{ color: '#ff4d4f' }} /> }} />
        </Col>
      </Row>

      <Row gutter={[16, 16]} style={{ marginTop: 16 }}>
        <Col xs={24} md={12}>
          <Card title="按模板类型">
            {Object.entries(stats?.byTemplate || {}).map(([code, count]) => (
              <Tag key={code} style={{ marginBottom: 8 }}>{code}: {count as number}</Tag>
            ))}
          </Card>
        </Col>
        <Col xs={24} md={12}>
          <Card title="按运行时">
            {Object.entries(stats?.byRuntime || {}).map(([provider, count]) => (
              <Tag key={provider} color="blue" style={{ marginBottom: 8 }}>{provider}: {count as number}</Tag>
            ))}
          </Card>
        </Col>
      </Row>

      <Row gutter={[16, 16]} style={{ marginTop: 16 }}>
        <Col xs={24} md={8}>
          <StatisticCard statistic={{ title: '已暴露端口', value: stats?.exposedPorts ?? 0 }} />
        </Col>
        <Col xs={24} md={8}>
          <StatisticCard statistic={{ title: '内部端口', value: stats?.internalPorts ?? 0 }} />
        </Col>
        <Col xs={24} md={8}>
          <StatisticCard statistic={{ title: '端口冲突', value: stats?.portConflicts ?? 0, status: (stats?.portConflicts ?? 0) > 0 ? 'error' : 'default' }} />
        </Col>
      </Row>

      <Row gutter={[16, 16]} style={{ marginTop: 16 }}>
        <Col xs={24} md={12}>
          <Card title="拓扑概览">
            <Typography.Text>拓扑总数: {stats?.topologyCount ?? 0}</Typography.Text>
            <br />
            <Typography.Text>已部署: {stats?.deployedTopologies ?? 0}</Typography.Text>
            <br />
            <a onClick={() => history.push('/app/topology')}>前往协同拓扑</a>
          </Card>
        </Col>
        <Col xs={24} md={12}>
          <Card title="最近应用">
            {(stats?.recentApps || []).map((app: RecentAppDto) => (
              <div key={app.id} style={{ marginBottom: 8 }}>
                <a onClick={() => history.push(`/app/detail/${app.id}`)}>{app.name}</a>
                {' '}
                <Tag>{app.templateCode}</Tag>
                <Tag color={app.status === 'running' ? 'green' : 'default'}>{app.status}</Tag>
                <Space size={4} style={{ marginLeft: 8 }}>
                  {access.canAccessFiles && (
                    <Button
                      size="small"
                      type="link"
                      onClick={() => history.push(`/files?appId=${app.id}`)}
                    >
                      文件
                    </Button>
                  )}
                  {access.canViewMemory && (
                    <Button
                      size="small"
                      type="link"
                      onClick={() => history.push(`/app/detail/${app.id}?tab=memory`)}
                    >
                      记忆
                    </Button>
                  )}
                </Space>
              </div>
            ))}
          </Card>
        </Col>
      </Row>

      <Row gutter={[16, 16]} className="quick-links" style={{ marginTop: 16 }}>
        {quickLinks.map((link) => (
          <Col xs={24} sm={8} md={8} lg={8} xl={4} key={link.path}>
            <Card className="quick-link-card" hoverable onClick={() => history.push(link.path)}>
              {link.icon}
              <div className="quick-link-title">{link.title}</div>
              <div className="quick-link-desc">{link.desc}</div>
            </Card>
          </Col>
        ))}
      </Row>
    </PageContainer>
  );
};

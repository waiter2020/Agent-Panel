import AppTerminal from '@/components/AppTerminal';
import { Button, Card, Form, Input, Popconfirm, Space, Table, Tag, Typography, message } from 'antd';
import type { TabsProps } from 'antd';
import { history } from '@umijs/max';
import AppConfigPanel from '../components/AppConfigPanel';
import AppOverviewPanel from '../components/AppOverviewPanel';
import AppWebConsolePanel from '../components/AppWebConsolePanel';
import OpenClawGatewayPanel from '../components/OpenClawGatewayPanel';
import DelegationPanel from '../components/DelegationPanel';
import EnvConfigPanel from '../components/EnvConfigPanel';
import MemoryPanel from '../components/MemoryPanel';
import MonitorPanel from '../components/MonitorPanel';
import PortPanel from '../components/PortPanel';
import SkillsPanel from '../components/SkillsPanel';
import VolumeFilesPanel from '../components/VolumeFilesPanel';
import OpenClawPanel from '../components/type/OpenClawPanel';
import HermesPanel from '../components/type/HermesPanel';
import OpenClaudePanel from '../components/type/OpenClaudePanel';

import type { RefObject } from 'react';
import type { AppHealthDto, AppTabDef, KnownTabKey, ManagementSchema } from '@/services/types/app-management';
import { isKnownTabKey } from '@/services/types/app-management';

type BuildTabsContext = {
  app: any;
  appId: number;
  access: Record<string, boolean>;
  runtimeStatus?: any;
  skillsContext?: any;
  health?: AppHealthDto;
  healthLoadFailed?: boolean;
  envSchema: any[];
  volumes: any[];
  fileProfiles: any[];
  stats: any[];
  monitorStatus: string;
  monitorMessage?: string;
  networkAvailable: boolean;
  logs: string[];
  logRef: RefObject<HTMLDivElement>;
  mcpEndpoints: any[];
  mcpForm: any;
  onSaved: () => void;
  onOpenConsole?: (consoleKey: string) => void;
  onDownloadLogs: () => void;
  onMcpCreate: (values: any) => Promise<void>;
  onMcpDiscover: (id: number) => Promise<void>;
  onMcpDelete: (id: number) => Promise<void>;
  onRefreshMcp: () => Promise<void>;
  activeConsoleKey?: string;
};

const DEFAULT_TABS = [
  { key: 'overview', label: '概览' },
  { key: 'config', label: '配置' },
  { key: 'env', label: '环境变量' },
  { key: 'skills', label: 'Skills', permission: 'skill:read' },
  { key: 'memory', label: '记忆', permission: 'memory:read' },
  { key: 'delegation', label: '委派', permission: 'delegation:read' },
  { key: 'console', label: '终端', permission: 'app:terminal' },
  { key: 'files', label: '数据卷' },
  { key: 'ports', label: '端口' },
  { key: 'mcp', label: 'MCP 端点' },
  { key: 'monitor', label: '监控' },
  { key: 'logs', label: '日志' },
];

function hasPermission(access: Record<string, boolean>, permission?: string) {
  if (!permission) return true;
  const map: Record<string, string> = {
    'app:terminal': 'canUseAppTerminal',
    'skill:read': 'canViewSkill',
    'memory:read': 'canViewMemory',
    'delegation:read': 'canViewDelegation',
    'app:write': 'canWriteApp',
  };
  const accessKey = map[permission] || permission;
  return !!access[accessKey];
}

function resolveTabs(schema: ManagementSchema | undefined): AppTabDef[] {
  const tabs = schema?.tabs;
  if (Array.isArray(tabs) && tabs.length > 0) {
    const keys = new Set(tabs.map((tab) => tab.key));
    const merged = [...tabs];
    for (const def of DEFAULT_TABS) {
      if (['memory', 'delegation', 'skills', 'files'].includes(def.key) && !keys.has(def.key)) {
        merged.push(def);
      }
    }
    return merged;
  }
  return DEFAULT_TABS;
}

export function buildAppTabs(ctx: BuildTabsContext): TabsProps['items'] {
  const schema = ctx.app?.managementSchema || {};
  const tabDefs = resolveTabs(schema);
  const items: NonNullable<TabsProps['items']> = [];

  tabDefs.forEach((tab: AppTabDef, index: number) => {
    if (!hasPermission(ctx.access, tab.permission)) {
      return;
    }
    if (!isKnownTabKey(tab.key)) {
      return;
    }
    const consoleKey = tab.key === 'webConsole'
      ? String(tab.portRef || tab.consoleKey || '')
      : undefined;
    if (tab.key === 'webConsole' && !consoleKey) {
      return;
    }

    const tabKey = tab.key === 'webConsole'
      ? `webConsole:${consoleKey}`
      : tab.key === 'typePanel'
        ? `typePanel:${tab.panelKey || ctx.app?.templateCode || index}`
        : tab.key;

    const label = tab.label || tab.key;

    switch (tab.key as KnownTabKey) {
      case 'overview':
        items.push({
          key: tabKey,
          label,
          children: (
            <AppOverviewPanel
              app={ctx.app}
              runtimeStatus={ctx.runtimeStatus}
              health={ctx.health}
              healthLoadFailed={ctx.healthLoadFailed}
              onOpenConsole={ctx.onOpenConsole}
            />
          ),
        });
        break;
      case 'config':
        items.push({
          key: tabKey,
          label,
          children: <AppConfigPanel appId={ctx.appId} app={ctx.app} onSaved={ctx.onSaved} />,
        });
        break;
      case 'env':
        items.push({
          key: tabKey,
          label,
          children: (
            <EnvConfigPanel
              appId={ctx.appId}
              envSchema={ctx.envSchema}
              env={ctx.app?.env || []}
              onSaved={ctx.onSaved}
            />
          ),
        });
        break;
      case 'webConsole': {
        const webConsoleKey = consoleKey!;
        const isOpenClawGateway = ctx.app?.templateCode === 'openclaw' && webConsoleKey === 'gateway';
        const ConsolePanel = isOpenClawGateway ? OpenClawGatewayPanel : AppWebConsolePanel;
        items.push({
          key: tabKey,
          label,
          children: (
            <ConsolePanel
              appId={ctx.appId}
              consoleKey={webConsoleKey}
              title={tab.label}
              disabled={!ctx.app?.runtimeRef || ctx.app?.status !== 'running'}
            />
          ),
        });
        break;
      }
      case 'console':
        items.push({
          key: tabKey,
          label,
          children: (
            <Card>
              {ctx.access.canUseAppTerminal ? (
                <AppTerminal
                  appId={ctx.appId}
                  disabled={!ctx.app?.runtimeRef || ctx.app?.status !== 'running'}
                />
              ) : (
                <Typography.Text type="secondary">无终端访问权限（需要 app:terminal）</Typography.Text>
              )}
            </Card>
          ),
        });
        break;
      case 'files':
        items.push({
          key: tabKey,
          label,
          children: (
            <Card
              extra={(
                <Button type="link" onClick={() => history.push(`/files?appId=${ctx.appId}`)}>
                  在文件中心打开
                </Button>
              )}
            >
              <VolumeFilesPanel
                appId={ctx.appId}
                volumes={ctx.volumes}
                fileProfiles={ctx.fileProfiles}
                canWrite={ctx.access.canWriteApp}
              />
            </Card>
          ),
        });
        break;
      case 'ports':
        items.push({
          key: tabKey,
          label,
          children: <PortPanel appId={ctx.appId} app={ctx.app} onSaved={ctx.onSaved} />,
        });
        break;
      case 'skills':
        items.push({
          key: tabKey,
          label,
          children: ctx.access.canViewSkill ? (
            <SkillsPanel
              context={ctx.skillsContext}
              appId={ctx.appId}
              canWriteSkill={ctx.access.canWriteSkill}
              onRefresh={ctx.onSaved}
            />
          ) : (
            <Card><Typography.Text type="secondary">无 Skills 查看权限</Typography.Text></Card>
          ),
        });
        break;
      case 'memory':
        items.push({
          key: tabKey,
          label,
          children: ctx.access.canViewMemory ? (
            <MemoryPanel
              applicationId={ctx.appId}
              canWrite={ctx.access.canWriteMemory}
              compact
              showViewAllLink
            />
          ) : (
            <Card><Typography.Text type="secondary">无记忆查看权限</Typography.Text></Card>
          ),
        });
        break;
      case 'delegation':
        items.push({
          key: tabKey,
          label,
          children: ctx.access.canViewDelegation ? (
            <DelegationPanel
              applicationId={ctx.appId}
              topologyId={ctx.skillsContext?.topologyId}
              canWrite={ctx.access.canWriteDelegation}
              compact
              showViewAllLink
            />
          ) : (
            <Card><Typography.Text type="secondary">无委派查看权限</Typography.Text></Card>
          ),
        });
        break;
      case 'mcp':
        items.push({
          key: tabKey,
          label,
          children: (
            <Card>
              {ctx.access.canWriteApp && (
                <Form form={ctx.mcpForm} layout="inline" style={{ marginBottom: 16 }} onFinish={ctx.onMcpCreate}>
                  <Form.Item name="url" rules={[{ required: true, message: '请输入 MCP URL' }]}>
                    <Input placeholder="http://app-id:3000/mcp" style={{ width: 360 }} />
                  </Form.Item>
                  <Form.Item>
                    <Button type="primary" htmlType="submit">注册</Button>
                  </Form.Item>
                </Form>
              )}
              <Table
                rowKey="id"
                size="small"
                pagination={false}
                dataSource={ctx.mcpEndpoints}
                columns={[
                  { title: 'URL', dataIndex: 'url', ellipsis: true },
                  { title: '状态', dataIndex: 'enabled', render: (v) => <Tag color={v ? 'green' : 'default'}>{v ? '启用' : '禁用'}</Tag> },
                  { title: '工具数', render: (_, r) => (r.tools || []).length },
                  {
                    title: '操作',
                    render: (_, record) => [
                      ctx.access.canWriteApp && (
                        <a key="discover" onClick={() => ctx.onMcpDiscover(record.id)}>发现工具</a>
                      ),
                      ctx.access.canWriteApp && (
                        <Popconfirm key="del" title="确认删除？" onConfirm={() => ctx.onMcpDelete(record.id)}>
                          <a style={{ marginLeft: 8, color: 'red' }}>删除</a>
                        </Popconfirm>
                      ),
                    ].filter(Boolean),
                  },
                ]}
              />
            </Card>
          ),
        });
        break;
      case 'monitor':
        items.push({
          key: tabKey,
          label,
          children: (
            <MonitorPanel
              stats={ctx.stats}
              monitorStatus={ctx.monitorStatus}
              monitorMessage={ctx.monitorMessage}
              networkAvailable={ctx.networkAvailable}
            />
          ),
        });
        break;
      case 'logs':
        items.push({
          key: tabKey,
          label,
          children: (
            <>
              <div style={{ marginBottom: 8 }}>
                <Button onClick={ctx.onDownloadLogs}>下载日志</Button>
              </div>
              <div ref={ctx.logRef} style={{ background: '#111', color: '#0f0', padding: 12, height: 400, overflow: 'auto', fontFamily: 'monospace' }}>
                {ctx.logs.map((l, i) => <div key={i}>{l}</div>)}
              </div>
            </>
          ),
        });
        break;
      case 'typePanel': {
        const panelKey = tab.panelKey || ctx.app?.templateCode;
        let panel = null;
        if (panelKey === 'openclaw') panel = <OpenClawPanel app={ctx.app} health={ctx.health} />;
        else if (panelKey === 'hermes') panel = <HermesPanel app={ctx.app} health={ctx.health} skillsContext={ctx.skillsContext} />;
        else if (panelKey === 'openclaude') panel = <OpenClaudePanel app={ctx.app} health={ctx.health} />;
        else panel = <Card>暂无类型专属面板</Card>;
        items.push({ key: tabKey, label, children: panel });
        break;
      }
      default: {
        const _exhaustive: never = tab.key;
        return _exhaustive;
      }
    }
  });

  if (!items.some((t) => t.key.startsWith('ports'))) {
    items.push({
      key: 'ports',
      label: '端口',
      children: <PortPanel appId={ctx.appId} app={ctx.app} onSaved={ctx.onSaved} />,
    });
  }

  return items;
}

export function getDefaultActiveTab(schema: ManagementSchema | undefined, consoleKey?: string) {
  if (consoleKey) {
    return `webConsole:${consoleKey}`;
  }
  const tabs = resolveTabs(schema);
  return tabs[0]?.key === 'webConsole'
    ? `webConsole:${tabs[0].consoleKey || tabs[0].portRef || 0}`
    : tabs[0]?.key || 'overview';
}

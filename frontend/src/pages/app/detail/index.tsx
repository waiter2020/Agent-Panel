import { Button, Card, Form, Result, Spin, Tabs, message } from 'antd';
import { PageContainer } from '@ant-design/pro-components';
import { history, useAccess, useParams, useSearchParams } from '@umijs/max';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
  deployApp,
  getApp,
  getAppSkills,
  getAppStatus,
  getLogDownloadUrl,
  listTemplates,
  restartApp,
  startApp,
  stopApp,
} from '@/services/app';
import { getAppHealth } from '@/services/appHealth';
import { getSseTicket } from '@/services/auth';
import {
  createMcpEndpoint,
  deleteMcpEndpoint,
  discoverMcpEndpoint,
  listMcpEndpoints,
} from '@/services/mcpEndpoint';
import { buildAppTabs, getDefaultActiveTab } from './hooks/useAppTabs';
import NewAppForm from './components/NewAppForm';

export default () => {
  const { id } = useParams<{ id: string }>();
  const access = useAccess();
  const [searchParams] = useSearchParams();
  const isNew = id === 'new';

  const [app, setApp] = useState<any>();
  const [runtimeStatus, setRuntimeStatus] = useState<any>();
  const [skillsContext, setSkillsContext] = useState<any>();
  const [health, setHealth] = useState<any>();
  const [healthLoadFailed, setHealthLoadFailed] = useState(false);
  const [templates, setTemplates] = useState<any[]>([]);
  const [logs, setLogs] = useState<string[]>([]);
  const [stats, setStats] = useState<any[]>([]);
  const [monitorStatus, setMonitorStatus] = useState<'connecting' | 'ok' | 'error' | 'unavailable'>('connecting');
  const [monitorMessage, setMonitorMessage] = useState<string>();
  const [networkAvailable, setNetworkAvailable] = useState(true);
  const [mcpEndpoints, setMcpEndpoints] = useState<any[]>([]);
  const [activeTab, setActiveTab] = useState<string>('overview');
  const [pageLoading, setPageLoading] = useState(false);
  const [loadError, setLoadError] = useState<string>();
  const [mcpForm] = Form.useForm();
  const logRef = useRef<HTMLDivElement>(null);

  const load = async () => {
    setPageLoading(true);
    setLoadError(undefined);
    try {
      if (!isNew) {
        const data = await getApp(Number(id));
        setApp(data);
        try { setRuntimeStatus(await getAppStatus(Number(id))); } catch { setRuntimeStatus(undefined); }
        try { setSkillsContext(await getAppSkills(Number(id))); } catch { setSkillsContext(undefined); }
        try {
          setHealth(await getAppHealth(Number(id)));
          setHealthLoadFailed(false);
        } catch {
          setHealth(undefined);
          setHealthLoadFailed(true);
        }
        setMcpEndpoints(await listMcpEndpoints(Number(id)));
        const tabParam = searchParams.get('tab');
        if (tabParam) {
          setActiveTab(tabParam);
        } else {
          setActiveTab(getDefaultActiveTab(data?.managementSchema));
        }
      }
      setTemplates(await listTemplates());
    } catch (e: any) {
      setLoadError(e?.message || '加载失败');
      setApp(undefined);
    } finally {
      setPageLoading(false);
    }
  };

  useEffect(() => { load(); }, [id]);

  useEffect(() => {
    if (!app || isNew) return;
    const tabParam = searchParams.get('tab');
    if (tabParam) {
      setActiveTab(tabParam);
    }
  }, [searchParams, app, isNew]);

  useEffect(() => {
    if (isNew || !app?.runtimeRef) return undefined;
    const timer = setInterval(async () => {
      try { setRuntimeStatus(await getAppStatus(Number(id))); } catch { /* ignore */ }
    }, 15000);
    return () => clearInterval(timer);
  }, [app, id, isNew]);

  useEffect(() => {
    if (isNew || !app?.runtimeRef) return;
    let es: EventSource | null = null;
    let cancelled = false;
    setMonitorStatus('connecting');
    (async () => {
      try {
        const { token } = await getSseTicket();
        if (cancelled) return;
        es = new EventSource(`/api/apps/${id}/stats/stream?token=${encodeURIComponent(token)}`);
        es.onmessage = (e) => {
          try {
            const data = JSON.parse(e.data);
            if (data.available === false) {
              setMonitorStatus('unavailable');
              setMonitorMessage(data.message || '监控数据不可用');
              return;
            }
            setMonitorStatus('ok');
            setMonitorMessage(undefined);
            setNetworkAvailable(data.networkAvailable !== false);
            setStats((prev) => [...prev.slice(-20), {
              time: new Date().toLocaleTimeString(),
              mem: data.memUsedBytes || 0,
              cpu: data.cpuPercent || 0,
              netRx: data.netRxBytesPerSec || 0,
              netTx: data.netTxBytesPerSec || 0,
            }]);
          } catch { /* ignore */ }
        };
        es.onerror = () => {
          setMonitorStatus('error');
          setMonitorMessage('无法连接监控流');
        };
      } catch {
        setMonitorStatus('error');
        setMonitorMessage('无法获取监控凭证');
      }
    })();
    return () => { cancelled = true; es?.close(); };
  }, [app, id, isNew]);

  useEffect(() => {
    if (isNew || !app?.runtimeRef) return;
    let es: EventSource | null = null;
    let cancelled = false;
    (async () => {
      try {
        const { token } = await getSseTicket();
        if (cancelled) return;
        es = new EventSource(`/api/apps/${id}/logs/stream?follow=true&tail=100&token=${encodeURIComponent(token)}`);
        es.onmessage = (e) => {
          setLogs((prev) => [...prev.slice(-500), e.data]);
          logRef.current?.scrollTo(0, logRef.current.scrollHeight);
        };
      } catch {
        message.error('无法连接日志流');
      }
    })();
    return () => { cancelled = true; es?.close(); };
  }, [app, id, isNew]);

  const handleDownloadLogs = async () => {
    const { token } = await getSseTicket();
    window.open(getLogDownloadUrl(Number(id), token), '_blank');
  };

  const onOpenConsole = useCallback((consoleKey: string) => {
    const tabKey = `webConsole:${consoleKey}`;
    setActiveTab(tabKey);
    history.replace(`/app/detail/${id}?tab=${encodeURIComponent(tabKey)}`);
  }, [id]);

  const handleTabChange = useCallback((key: string) => {
    setActiveTab(key);
    if (key === 'overview') {
      history.replace(`/app/detail/${id}`);
      return;
    }
    history.replace(`/app/detail/${id}?tab=${encodeURIComponent(key)}`);
  }, [id]);

  const fileProfiles = app?.managementSchema?.fileProfiles || [];
  const volumes = app?.volumes || [];
  const envSchema = app?.envSchema || [];

  const tabItems = useMemo(() => {
    if (!app || isNew) return [];
    return buildAppTabs({
      app,
      appId: Number(id),
      access,
      runtimeStatus,
      skillsContext,
      health,
      healthLoadFailed,
      envSchema,
      volumes,
      fileProfiles,
      stats,
      monitorStatus,
      monitorMessage,
      networkAvailable,
      logs,
      logRef,
      mcpEndpoints,
      mcpForm,
      onSaved: load,
      onOpenConsole,
      onDownloadLogs: handleDownloadLogs,
      onMcpCreate: async (values) => {
        await createMcpEndpoint({ applicationId: Number(id), url: values.url });
        message.success('MCP 端点已注册');
        mcpForm.resetFields();
        setMcpEndpoints(await listMcpEndpoints(Number(id)));
      },
      onMcpDiscover: async (endpointId) => {
        await discoverMcpEndpoint(endpointId);
        message.success('工具列表已更新');
        setMcpEndpoints(await listMcpEndpoints(Number(id)));
      },
      onMcpDelete: async (endpointId) => {
        await deleteMcpEndpoint(endpointId);
        message.success('已删除');
        setMcpEndpoints(await listMcpEndpoints(Number(id)));
      },
      onRefreshMcp: async () => setMcpEndpoints(await listMcpEndpoints(Number(id))),
    });
  }, [app, access, runtimeStatus, skillsContext, health, healthLoadFailed, stats, monitorStatus, monitorMessage, networkAvailable, logs, mcpEndpoints, id, isNew, onOpenConsole]);

  useEffect(() => {
    if (isNew || !app || !tabItems.length) return;
    const validKeys = tabItems.map((t) => t?.key).filter(Boolean) as string[];
    if (!validKeys.includes(activeTab)) {
      const fallback = getDefaultActiveTab(app.managementSchema);
      setActiveTab(fallback);
      if (fallback === 'overview') {
        history.replace(`/app/detail/${id}`);
      } else {
        history.replace(`/app/detail/${id}?tab=${encodeURIComponent(fallback)}`);
      }
    }
  }, [tabItems, activeTab, app, isNew, id]);

  if (isNew) {
    if (pageLoading && !templates.length) {
      return (
        <PageContainer title="新建应用">
          <div style={{ textAlign: 'center', padding: 48 }}><Spin size="large" /></div>
        </PageContainer>
      );
    }
    return <NewAppForm templates={templates} />;
  }

  if (pageLoading && !app) {
    return (
      <PageContainer title="应用详情">
        <div style={{ textAlign: 'center', padding: 48 }}><Spin size="large" /></div>
      </PageContainer>
    );
  }

  if (loadError) {
    return (
      <PageContainer title="应用详情">
        <Result status="error" title="加载失败" subTitle={loadError} extra={<Button onClick={load}>重试</Button>} />
      </PageContainer>
    );
  }

  const extraButtons = [
    access.canDeployApp && (
      <Button key="deploy" type="primary" onClick={async () => { await deployApp(Number(id)); message.success('部署完成'); load(); }}>部署</Button>
    ),
    access.canOperateApp && (
      <Button key="restart" onClick={async () => { await restartApp(Number(id)); load(); }}>重启</Button>
    ),
    access.canOperateApp && (
      app?.status === 'running'
        ? <Button key="stop" onClick={async () => { await stopApp(Number(id)); load(); }}>停止</Button>
        : <Button key="start" onClick={async () => { await startApp(Number(id)); load(); }}>启动</Button>
    ),
  ].filter(Boolean);

  return (
    <PageContainer title={app?.name || '应用详情'} extra={extraButtons}>
      <Tabs activeKey={activeTab} onChange={handleTabChange} items={tabItems} />
    </PageContainer>
  );
};

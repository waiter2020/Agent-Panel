import AppWebConsolePanel from '@/pages/app/detail/components/AppWebConsolePanel';
import OpenClawGatewayPanel from '@/pages/app/detail/components/OpenClawGatewayPanel';
import { getApp } from '@/services/app';
import type { ApplicationDto } from '@/services/types/app-management';
import { ArrowLeftOutlined } from '@ant-design/icons';
import { history, useParams } from '@umijs/max';
import { Alert, Button, Spin, Typography } from 'antd';
import { useEffect, useState } from 'react';

export default function AppConsolePage() {
  const { id, consoleKey } = useParams<{ id: string; consoleKey: string }>();
  const appId = Number(id);
  const [app, setApp] = useState<ApplicationDto>();
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string>();

  useEffect(() => {
    if (!appId || !consoleKey) {
      setLoading(false);
      return;
    }
    let cancelled = false;
    (async () => {
      try {
        setError(undefined);
        const data = await getApp(appId);
        if (!cancelled) setApp(data);
      } catch (e: any) {
        if (!cancelled) {
          setError(e?.message || '加载应用信息失败');
        }
      } finally {
        if (!cancelled) setLoading(false);
      }
    })();
    return () => { cancelled = true; };
  }, [appId, consoleKey]);

  if (!appId || !consoleKey) {
    return <Typography.Text type="danger">无效的控制台地址</Typography.Text>;
  }

  const isOpenClawGateway = app?.templateCode === 'openclaw' && consoleKey === 'gateway';
  const ConsolePanel = isOpenClawGateway ? OpenClawGatewayPanel : AppWebConsolePanel;
  const disabled = !app?.runtimeRef || app?.status !== 'running';
  const consoleTitle = app?.managementSchema?.webConsoles
    ?.find((item) => item.key === consoleKey || item.portRef === consoleKey)?.title;
  const title = typeof consoleTitle === 'string' && consoleTitle ? consoleTitle : consoleKey;

  return (
    <div style={{ display: 'flex', flexDirection: 'column', height: '100vh', background: '#fff' }}>
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          gap: 12,
          padding: '8px 16px',
          borderBottom: '1px solid #f0f0f0',
          flexShrink: 0,
        }}
      >
        <Button
          type="text"
          icon={<ArrowLeftOutlined />}
          onClick={() => history.push(`/app/detail/${appId}?tab=${encodeURIComponent(`webConsole:${consoleKey}`)}`)}
        >
          返回应用详情
        </Button>
        <Typography.Text strong>
          {app?.name ? `${app.name} / ${title}` : title}
        </Typography.Text>
      </div>
      <div style={{ flex: 1, minHeight: 0, padding: '0 16px 16px' }}>
        {loading ? (
          <Spin style={{ marginTop: 48 }} />
        ) : error ? (
          <Alert type="error" showIcon message={error} style={{ marginTop: 16 }} />
        ) : (
          <ConsolePanel
            appId={appId}
            consoleKey={consoleKey}
            title={title}
            disabled={disabled}
            standalone
          />
        )}
      </div>
    </div>
  );
}

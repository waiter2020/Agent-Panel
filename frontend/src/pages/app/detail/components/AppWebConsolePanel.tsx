import { getAppConsolePageUrl, getAppProxyUrl } from '@/services/appHealth';
import { establishProxySession } from '@/services/auth';
import { Alert, Button, Space, Spin } from 'antd';
import { useEffect, useState } from 'react';

type Props = {
  appId: number;
  consoleKey: string;
  title?: string;
  disabled?: boolean;
  trustedProxyHint?: boolean;
  failureHint?: string;
  standalone?: boolean;
};

export default function AppWebConsolePanel({
  appId,
  consoleKey,
  title,
  disabled,
  trustedProxyHint,
  failureHint,
  standalone,
}: Props) {
  const [src, setSrc] = useState<string>();
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string>();

  function parseProbeError(text: string, status: number): string {
    const htmlMatch = text.match(/<p>([^<]+)<\/p>/);
    if (htmlMatch?.[1]) {
      return htmlMatch[1];
    }
    try {
      const json = JSON.parse(text) as { message?: string };
      if (json.message) {
        return json.message;
      }
    } catch {
      // not JSON
    }
    return `代理返回 ${status}`;
  }

  useEffect(() => {
    if (disabled) {
      setLoading(false);
      setSrc(undefined);
      return;
    }
    let cancelled = false;
    (async () => {
      try {
        await establishProxySession(appId, consoleKey);
        if (cancelled) return;
        const url = getAppProxyUrl(appId, consoleKey);
        const accessToken = localStorage.getItem('accessToken') || '';
        const probe = await fetch(url, {
          credentials: 'include',
          headers: accessToken ? { Authorization: `Bearer ${accessToken}` } : {},
        });
        if (!probe.ok) {
          const text = await probe.text();
          throw new Error(parseProbeError(text, probe.status));
        }
        setSrc(url);
        setError(undefined);
      } catch (e: any) {
        if (!cancelled) setError(e?.message || '无法连接 Web 控制台');
      } finally {
        if (!cancelled) setLoading(false);
      }
    })();
    return () => { cancelled = true; };
  }, [appId, consoleKey, disabled]);

  const iframeHeight = standalone ? 'calc(100vh - 64px)' : '72vh';

  if (disabled) {
    return <Alert type="info" showIcon message="应用未运行，无法打开 Web 控制台" />;
  }
  if (loading) {
    return (
      <Spin tip={trustedProxyHint
        ? `正在通过 Panel 代理连接 ${title || consoleKey}（免令牌）...`
        : `正在连接 ${title || consoleKey}...`}
      />
    );
  }
  if (error) {
    return (
      <Alert
        type="error"
        showIcon
        message="Web 控制台连接失败"
        description={(
          <Space direction="vertical">
            <span>{error}</span>
            {failureHint && <span style={{ color: '#666' }}>{failureHint}</span>}
          </Space>
        )}
      />
    );
  }
  return (
    <>
      {!standalone && (
        <div style={{ marginBottom: 8 }}>
          <Button
            type="link"
            onClick={() => window.open(getAppConsolePageUrl(appId, consoleKey), '_blank', 'noopener,noreferrer')}
          >
            在新窗口打开 {title || consoleKey}
          </Button>
        </div>
      )}
      <iframe
        title={title || consoleKey}
        src={src}
        style={{
          width: '100%',
          height: iframeHeight,
          border: '1px solid #f0f0f0',
          borderRadius: 8,
        }}
      />
    </>
  );
}

import { getAppProxyUrl } from '@/services/appHealth';
import { establishProxySession } from '@/services/auth';
import { Alert, Button, Space, Spin } from 'antd';
import { useEffect, useState } from 'react';

type Props = {
  appId: number;
  consoleKey: string;
  title?: string;
  disabled?: boolean;
  accessUrls?: { name: string; url: string }[];
  trustedProxyHint?: boolean;
  failureHint?: string;
};

export default function AppWebConsolePanel({
  appId,
  consoleKey,
  title,
  disabled,
  accessUrls = [],
  trustedProxyHint,
  failureHint,
}: Props) {
  const [src, setSrc] = useState<string>();
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string>();

  const externalUrl = accessUrls.find((u) => u.name === consoleKey)?.url;

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
            {externalUrl && (
              <Button type="link" href={`http://${externalUrl}`} target="_blank" rel="noreferrer">
                在新窗口打开外部地址 ({externalUrl})
              </Button>
            )}
          </Space>
        )}
      />
    );
  }
  return (
    <>
      <div style={{ marginBottom: 8 }}>
        {src && (
          <Button type="link" href={src} target="_blank" rel="noreferrer">
            在新窗口打开 {title || consoleKey}
          </Button>
        )}
        {externalUrl && (
          <Button type="link" href={`http://${externalUrl}`} target="_blank" rel="noreferrer">
            直接访问 {externalUrl}
          </Button>
        )}
      </div>
      <iframe
        title={title || consoleKey}
        src={src}
        style={{ width: '100%', height: '72vh', border: '1px solid #f0f0f0', borderRadius: 8 }}
      />
    </>
  );
}

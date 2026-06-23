import { getSseTicket } from '@/services/auth';
import { FitAddon } from '@xterm/addon-fit';
import { Terminal } from '@xterm/xterm';
import { Button, Space, message } from 'antd';
import { useCallback, useEffect, useRef, useState } from 'react';
import '@xterm/xterm/css/xterm.css';

type Props = {
  appId: number;
  disabled?: boolean;
};

function wsBaseUrl() {
  const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
  return `${protocol}//${window.location.host}`;
}

function decodeBase64(data: string): Uint8Array {
  const binary = atob(data);
  const bytes = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i += 1) {
    bytes[i] = binary.charCodeAt(i);
  }
  return bytes;
}

function sendResize(ws: WebSocket, term: Terminal) {
  if (ws.readyState !== WebSocket.OPEN) {
    return;
  }
  ws.send(JSON.stringify({
    type: 'resize',
    cols: term.cols,
    rows: term.rows,
  }));
}

export default function AppTerminal({ appId, disabled }: Props) {
  const containerRef = useRef<HTMLDivElement>(null);
  const termRef = useRef<Terminal | null>(null);
  const fitRef = useRef<FitAddon | null>(null);
  const wsRef = useRef<WebSocket | null>(null);
  const connectedRef = useRef(false);
  const resizeTimerRef = useRef<ReturnType<typeof setTimeout>>();
  const [connected, setConnected] = useState(false);
  const [connecting, setConnecting] = useState(false);

  const scheduleResize = useCallback(() => {
    const term = termRef.current;
    const ws = wsRef.current;
    const fit = fitRef.current;
    if (!term || !fit || !connectedRef.current || !ws) {
      return;
    }
    fit.fit();
    if (resizeTimerRef.current) {
      clearTimeout(resizeTimerRef.current);
    }
    resizeTimerRef.current = setTimeout(() => {
      sendResize(ws, term);
    }, 150);
  }, []);

  useEffect(() => {
    connectedRef.current = connected;
  }, [connected]);

  useEffect(() => {
    const term = new Terminal({
      cursorBlink: true,
      fontFamily: 'Consolas, "Courier New", monospace',
      fontSize: 14,
      theme: { background: '#111111', foreground: '#f0f0f0' },
    });
    const fitAddon = new FitAddon();
    term.loadAddon(fitAddon);
    termRef.current = term;
    fitRef.current = fitAddon;
    if (containerRef.current) {
      term.open(containerRef.current);
      fitAddon.fit();
    }
    const onWindowResize = () => scheduleResize();
    window.addEventListener('resize', onWindowResize);
    const observer = new ResizeObserver(() => scheduleResize());
    if (containerRef.current) {
      observer.observe(containerRef.current);
    }
    return () => {
      window.removeEventListener('resize', onWindowResize);
      observer.disconnect();
      if (resizeTimerRef.current) {
        clearTimeout(resizeTimerRef.current);
      }
      wsRef.current?.close();
      term.dispose();
      termRef.current = null;
      fitRef.current = null;
    };
  }, [scheduleResize]);

  useEffect(() => {
    const term = termRef.current;
    const ws = wsRef.current;
    if (!term || !connected || !ws) {
      return undefined;
    }
    const disposable = term.onData((data) => {
      if (ws.readyState === WebSocket.OPEN) {
        ws.send(JSON.stringify({ type: 'input', data }));
      }
    });
    return () => disposable.dispose();
  }, [connected]);

  const disconnect = () => {
    wsRef.current?.close();
    wsRef.current = null;
    setConnected(false);
  };

  const connect = async () => {
    if (disabled || connecting || connected) {
      return;
    }
    setConnecting(true);
    try {
      const { token } = await getSseTicket();
      const ws = new WebSocket(`${wsBaseUrl()}/api/apps/${appId}/terminal/ws?token=${encodeURIComponent(token)}`);
      wsRef.current = ws;
      ws.onopen = () => {
        setConnected(true);
        setConnecting(false);
        const term = termRef.current;
        const fit = fitRef.current;
        if (term && fit) {
          fit.fit();
          sendResize(ws, term);
          term.focus();
        }
      };
      ws.onmessage = (event) => {
        try {
          const payload = JSON.parse(event.data);
          const term = termRef.current;
          if (!term) return;
          if (payload.type === 'output' && payload.data) {
            if (typeof payload.data === 'string' && payload.encoding === 'base64') {
              term.write(decodeBase64(payload.data));
            } else {
              term.write(String(payload.data));
            }
          } else if (payload.type === 'error') {
            term.writeln(`\r\n\x1b[31m${payload.message || '终端错误'}\x1b[0m`);
          } else if (payload.type === 'connected') {
            term.writeln(`\r\n\x1b[32m${payload.message || '已连接'}\x1b[0m\r\n`);
          }
        } catch {
          termRef.current?.write(event.data);
        }
      };
      ws.onerror = () => {
        message.error('终端连接失败');
        setConnecting(false);
        setConnected(false);
      };
      ws.onclose = () => {
        setConnected(false);
        setConnecting(false);
        termRef.current?.writeln('\r\n\x1b[33m终端连接已关闭\x1b[0m');
      };
    } catch {
      message.error('无法获取终端凭证');
      setConnecting(false);
    }
  };

  return (
    <div>
      <Space style={{ marginBottom: 12 }}>
        {!connected ? (
          <Button type="primary" loading={connecting} disabled={disabled} onClick={connect}>
            连接终端
          </Button>
        ) : (
          <Button danger onClick={disconnect}>断开连接</Button>
        )}
        <Button onClick={() => termRef.current?.clear()}>清屏</Button>
      </Space>
      <div
        ref={containerRef}
        style={{
          height: 420,
          background: '#111',
          borderRadius: 6,
          padding: 8,
        }}
      />
    </div>
  );
}

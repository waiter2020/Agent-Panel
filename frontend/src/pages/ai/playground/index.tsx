import { PageContainer } from '@ant-design/pro-components';
import { Button, Card, Input, Select, Space, message } from 'antd';
import { Link, history, useSearchParams } from '@umijs/max';
import { listProviders } from '@/services/ai';
import { useEffect, useRef, useState } from 'react';

function syncPlaygroundUrl(providerId?: number, model?: string) {
  const params = new URLSearchParams();
  if (providerId) params.set('provider', String(providerId));
  if (model) params.set('model', model);
  const qs = params.toString();
  history.replace(qs ? `/ai/playground?${qs}` : '/ai/playground');
}

export default () => {
  const [searchParams] = useSearchParams();
  const [providers, setProviders] = useState<any[]>([]);
  const [providerId, setProviderId] = useState<number>();
  const [model, setModel] = useState('gpt-4o-mini');
  const [input, setInput] = useState('');
  const [messages, setMessages] = useState<{ role: string; content: string }[]>([]);
  const [streaming, setStreaming] = useState('');
  const abortRef = useRef<AbortController>();

  useEffect(() => {
    listProviders().then((data) => {
      setProviders(data);
      const providerParam = searchParams.get('provider');
      const modelParam = searchParams.get('model');
      if (providerParam) {
        const matched = data.find((p: any) => String(p.id) === providerParam || p.code === providerParam);
        if (matched) setProviderId(matched.id);
      } else if (data[0]) {
        setProviderId(data[0].id);
      }
      if (modelParam) setModel(modelParam);
    });
  }, [searchParams]);

  const send = async () => {
    if (!providerId || !input.trim()) return;
    const nextMessages = [...messages, { role: 'user', content: input }];
    setMessages(nextMessages);
    setInput('');
    setStreaming('');
    abortRef.current?.abort();
    abortRef.current = new AbortController();
    const token = localStorage.getItem('accessToken');
    const res = await fetch('/api/ai/chat', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
      body: JSON.stringify({ providerId, model, messages: nextMessages }),
      signal: abortRef.current.signal,
    });
    const reader = res.body?.getReader();
    const decoder = new TextDecoder();
    let content = '';
    while (reader) {
      const { done, value } = await reader.read();
      if (done) break;
      content += decoder.decode(value);
      setStreaming(content);
    }
    setMessages([...nextMessages, { role: 'assistant', content }]);
    setStreaming('');
  };

  return (
    <PageContainer
      title="对话调试"
      extra={<Link to="/ai/models">返回模型配置</Link>}
    >
      <Card>
        <Space style={{ marginBottom: 16 }}>
          <Select
            style={{ width: 200 }}
            placeholder="选择 Provider"
            value={providerId}
            onChange={(v) => {
              setProviderId(v);
              syncPlaygroundUrl(v, model);
            }}
            options={providers.map((p) => ({ label: p.name, value: p.id }))}
          />
          <Input
            style={{ width: 200 }}
            value={model}
            onChange={(e) => {
              setModel(e.target.value);
              syncPlaygroundUrl(providerId, e.target.value);
            }}
            placeholder="模型名称"
          />
        </Space>
        <div style={{ minHeight: 300, border: '1px solid #eee', padding: 12, marginBottom: 12 }}>
          {messages.map((m, i) => (
            <div key={i} style={{ marginBottom: 8 }}>
              <strong>{m.role === 'user' ? '用户' : '助手'}：</strong>{m.content}
            </div>
          ))}
          {streaming && <div><strong>助手：</strong>{streaming}</div>}
        </div>
        <Space.Compact style={{ width: '100%' }}>
          <Input value={input} onChange={(e) => setInput(e.target.value)} onPressEnter={send} placeholder="输入消息..." />
          <Button type="primary" onClick={send}>发送</Button>
        </Space.Compact>
      </Card>
    </PageContainer>
  );
};

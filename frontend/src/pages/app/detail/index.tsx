import { PageContainer, ProDescriptions, ProForm, ProFormDependency, ProFormDigit, ProFormSelect, ProFormText, ProFormTextArea } from '@ant-design/pro-components';

import { Breadcrumb, Button, Card, Form, Input, Modal, Popconfirm, Select, Space, Table, Tabs, Tag, Typography, Upload, message } from 'antd';

import { useParams } from '@umijs/max';

import { useCallback, useEffect, useRef, useState } from 'react';

import {
  createApp,
  deleteAppFile,
  deployApp,
  getApp,
  getAppFileDownloadUrl,
  getAppSkills,
  getAppStatus,
  getLogDownloadUrl,
  listAppFiles,
  listRegistrySources,
  listRegistryTags,
  listTemplates,
  restartApp,
  startApp,
  stopApp,
  updateApp,
  uploadAppFile,
} from '@/services/app';

import { getSseTicket } from '@/services/auth';
import {
  createMcpEndpoint,
  deleteMcpEndpoint,
  discoverMcpEndpoint,
  listMcpEndpoints,
} from '@/services/mcpEndpoint';

import AppTerminal from '@/components/AppTerminal';
import AppConfigPanel from './components/AppConfigPanel';
import EnvConfigPanel from './components/EnvConfigPanel';
import MonitorPanel from './components/MonitorPanel';
import SkillsPanel from './components/SkillsPanel';

import { history } from '@umijs/max';
import { useAccess } from '@umijs/max';

import { DeleteOutlined, DownloadOutlined, FolderOpenOutlined, ReloadOutlined, UploadOutlined } from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import {
  applyRegistrySource,
  detectRegistrySource,
  extractRepositoryPath,
  type RegistrySourceId,
} from '@/utils/imageRegistry';

type FileEntry = { name: string; directory: boolean; size: number; modifiedAt: number };

function joinPath(base: string, name: string) {
  if (!base) return name;
  return base.endsWith('/') ? `${base}${name}` : `${base}/${name}`;
}

function VolumeFilesPanel({ appId, volumes }: { appId: number; volumes: any[] }) {
  const [volume, setVolume] = useState<string>(volumes[0]?.name || 'data');
  const [currentPath, setCurrentPath] = useState('');
  const [files, setFiles] = useState<FileEntry[]>([]);
  const [loading, setLoading] = useState(false);

  const loadFiles = async (vol = volume, path = currentPath) => {
    if (!vol) return;
    setLoading(true);
    try {
      const data = await listAppFiles(appId, vol, path || undefined);
      setFiles(data || []);
    } catch {
      message.error('加载文件列表失败');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (volumes.length && !volumes.find((v) => v.name === volume)) {
      setVolume(volumes[0].name);
    }
  }, [volumes]);

  useEffect(() => {
    loadFiles(volume, currentPath);
  }, [appId, volume, currentPath]);

  const pathParts = currentPath ? currentPath.split('/').filter(Boolean) : [];

  const columns: ColumnsType<FileEntry> = [
    {
      title: '名称',
      dataIndex: 'name',
      render: (name, record) => (
        record.directory ? (
          <a onClick={() => setCurrentPath(joinPath(currentPath, name))}>
            <FolderOpenOutlined /> {name}
          </a>
        ) : name
      ),
    },
    {
      title: '大小',
      dataIndex: 'size',
      width: 120,
      render: (size, record) => (record.directory ? '-' : `${size} B`),
    },
    {
      title: '修改时间',
      dataIndex: 'modifiedAt',
      width: 180,
      render: (ts) => (ts ? new Date(ts).toLocaleString() : '-'),
    },
    {
      title: '操作',
      width: 160,
      render: (_, record) => {
        const filePath = joinPath(currentPath, record.name);
        return [
          !record.directory && (
            <a
              key="download"
              onClick={async () => {
                const token = localStorage.getItem('accessToken') || '';
                const res = await fetch(getAppFileDownloadUrl(appId, volume, filePath), {
                  headers: { Authorization: `Bearer ${token}` },
                });
                if (!res.ok) {
                  message.error('下载失败');
                  return;
                }
                const blob = await res.blob();
                const url = URL.createObjectURL(blob);
                const a = document.createElement('a');
                a.href = url;
                a.download = record.name;
                a.click();
                URL.revokeObjectURL(url);
              }}
            >
              <DownloadOutlined /> 下载
            </a>
          ),
          <a
            key="delete"
            style={{ marginLeft: 8, color: '#ff4d4f' }}
            onClick={() => {
              Modal.confirm({
                title: `确认删除 ${record.name}？`,
                onOk: async () => {
                  await deleteAppFile(appId, volume, filePath);
                  message.success('已删除');
                  loadFiles();
                },
              });
            }}
          >
            <DeleteOutlined /> 删除
          </a>,
        ].filter(Boolean);
      },
    },
  ];

  if (!volumes.length) {
    return <div>该应用未配置数据卷</div>;
  }

  return (
    <>
      <div style={{ marginBottom: 16, display: 'flex', gap: 12, alignItems: 'center' }}>
        <span>数据卷：</span>
        <Select
          style={{ width: 200 }}
          value={volume}
          onChange={(v) => { setVolume(v); setCurrentPath(''); }}
          options={volumes.map((v) => ({ label: v.description || v.name, value: v.name }))}
        />
        <Upload
          showUploadList={false}
          beforeUpload={(file) => {
            uploadAppFile(appId, volume, file, currentPath || undefined)
              .then(() => { message.success('上传成功'); loadFiles(); })
              .catch(() => message.error('上传失败'));
            return false;
          }}
        >
          <Button icon={<UploadOutlined />}>上传文件</Button>
        </Upload>
      </div>
      <Breadcrumb style={{ marginBottom: 12 }}>
        <Breadcrumb.Item>
          <a onClick={() => setCurrentPath('')}>根目录</a>
        </Breadcrumb.Item>
        {pathParts.map((part, idx) => {
          const subPath = pathParts.slice(0, idx + 1).join('/');
          const isLast = idx === pathParts.length - 1;
          return (
            <Breadcrumb.Item key={subPath}>
              {isLast ? part : <a onClick={() => setCurrentPath(subPath)}>{part}</a>}
            </Breadcrumb.Item>
          );
        })}
      </Breadcrumb>
      <Table rowKey="name" loading={loading} columns={columns} dataSource={files} pagination={false} size="small" />
    </>
  );
}

function NewAppForm({ templates }: { templates: any[] }) {
  const [form] = Form.useForm();
  const [registrySources, setRegistrySources] = useState<{ label: string; value: RegistrySourceId }[]>([]);
  const [repositoryPath, setRepositoryPath] = useState('');
  const [tagOptions, setTagOptions] = useState<{ label: string; value: string }[]>([]);
  const [tagLoading, setTagLoading] = useState(false);
  const [tagFallback, setTagFallback] = useState(false);
  const [tagMessage, setTagMessage] = useState<string>();
  const debounceRef = useRef<ReturnType<typeof setTimeout>>();

  useEffect(() => {
    listRegistrySources()
      .then((sources) => {
        setRegistrySources(sources.map((s: { id: RegistrySourceId; name: string; host: string }) => ({
          label: s.host ? `${s.name} (${s.host})` : s.name,
          value: s.id,
        })));
      })
      .catch(() => {
        setRegistrySources([
          { label: 'GHCR (ghcr.io)', value: 'ghcr' },
          { label: 'Docker Hub (docker.io)', value: 'dockerhub' },
          { label: '自定义', value: 'custom' },
        ]);
      });
  }, []);

  const fetchTags = useCallback(async (image?: string, templateId?: number) => {
    if (!image?.trim()) {
      setTagOptions([]);
      setTagFallback(false);
      setTagMessage(undefined);
      return;
    }
    setTagLoading(true);
    try {
      const result = await listRegistryTags(image.trim(), templateId);
      const options = (result.tags || []).map((tag: string) => ({ label: tag, value: tag }));
      setTagOptions(options);
      setTagFallback(result.fallback);
      setTagMessage(result.fallback ? result.message : undefined);
      if (result.defaultTag) {
        form.setFieldValue('tag', result.defaultTag);
      }
    } catch {
      const template = templates.find((t) => t.id === templateId);
      setTagOptions([]);
      setTagFallback(true);
      setTagMessage('无法连接镜像仓库，请手动输入标签');
      if (template?.defaultTag) {
        form.setFieldValue('tag', template.defaultTag);
      }
    } finally {
      setTagLoading(false);
    }
  }, [form, templates]);

  const scheduleFetchTags = useCallback((image?: string, templateId?: number) => {
    if (debounceRef.current) {
      clearTimeout(debounceRef.current);
    }
    debounceRef.current = setTimeout(() => {
      fetchTags(image, templateId);
    }, 500);
  }, [fetchTags]);

  useEffect(() => () => {
    if (debounceRef.current) {
      clearTimeout(debounceRef.current);
    }
  }, []);

  const applyImageFromSource = useCallback((
    source: RegistrySourceId,
    repoPath: string,
    templateId?: number,
  ) => {
    const image = applyRegistrySource(source, repoPath);
    form.setFieldsValue({ imageRegistrySource: source, image });
    setTagFallback(false);
    setTagMessage(undefined);
    fetchTags(image, templateId);
  }, [form, fetchTags]);

  const handleTemplateChange = useCallback((templateId: number) => {
    const selectedTemplate = templates.find((t) => t.id === templateId);
    if (!selectedTemplate) return;
    const repoPath = extractRepositoryPath(selectedTemplate.image);
    const source = detectRegistrySource(selectedTemplate.image);
    setRepositoryPath(repoPath);
    form.setFieldsValue({
      tag: selectedTemplate.defaultTag || 'v1.0.0',
    });
    applyImageFromSource(source, repoPath, templateId);
  }, [templates, form, applyImageFromSource]);

  const handleRegistrySourceChange = useCallback((source: RegistrySourceId) => {
    const templateId = form.getFieldValue('templateId');
    if (source === 'custom') {
      form.setFieldsValue({ imageRegistrySource: source });
      return;
    }
    const repoPath = repositoryPath || extractRepositoryPath(form.getFieldValue('image') || '');
    setRepositoryPath(repoPath);
    applyImageFromSource(source, repoPath, templateId);
  }, [form, repositoryPath, applyImageFromSource]);

  const handleImageChange = useCallback((image: string) => {
    const templateId = form.getFieldValue('templateId');
    const source = form.getFieldValue('imageRegistrySource') as RegistrySourceId;
    const repoPath = extractRepositoryPath(image);
    setRepositoryPath(repoPath);
    if (source !== 'custom') {
      const expected = applyRegistrySource(source, repoPath);
      if (image.trim() !== expected) {
        form.setFieldsValue({ imageRegistrySource: 'custom' });
      }
    }
    scheduleFetchTags(image, templateId);
  }, [form, scheduleFetchTags]);

  return (
    <PageContainer title="新建应用">
      <Card>
        <ProForm
          form={form}
          onFinish={async (values) => {
            const template = templates.find((t) => t.id === values.templateId);
            const envSchema: any[] = template?.envSchema || [];
            const env = envSchema.map((schema: any) => ({
              key: schema.key,
              value: values[`env_${schema.key}`] ?? schema.default ?? '',
              secret: schema.secret ?? false,
            }));
            const created = await createApp({
              name: values.name,
              templateId: values.templateId,
              image: values.image,
              tag: values.tag,
              remark: values.remark,
              resources: {
                cpu: values.cpu || '1',
                memory: values.memory || '1Gi',
              },
              replicas: values.replicas || 1,
              env,
            });
            message.success('创建成功');
            history.push(`/app/detail/${created.id}`);
          }}
        >
          <ProFormText name="name" label="应用名称" rules={[{ required: true }]} />

          <ProFormSelect
            name="templateId"
            label="模板"
            rules={[{ required: true }]}
            options={templates.map((t) => ({
              label: `${t.name} (${t.image})`,
              value: t.id,
            }))}
            fieldProps={{
              onChange: handleTemplateChange,
            }}
          />

          <ProFormSelect
            name="imageRegistrySource"
            label="镜像源"
            rules={[{ required: true, message: '请选择镜像源' }]}
            initialValue="ghcr"
            options={registrySources}
            tooltip="默认支持 GHCR 与 Docker Hub，可切换；选「自定义」可覆盖完整镜像地址"
            fieldProps={{
              onChange: handleRegistrySourceChange,
            }}
          />

          <ProFormDependency name={['imageRegistrySource']}>
            {({ imageRegistrySource }) => {
              const isCustom = imageRegistrySource === 'custom';
              return (
                <ProFormText
                  name="image"
                  label="镜像地址"
                  rules={[{ required: true, message: '请输入镜像地址' }]}
                  placeholder={
                    isCustom
                      ? '输入完整镜像地址，如 ghcr.io/org/app 或 namespace/app'
                      : '根据镜像源自动生成，也可手动覆盖'
                  }
                  tooltip={isCustom ? '自定义模式下可输入任意合法镜像地址' : '修改后将自动切换为「自定义」镜像源'}
                  fieldProps={{
                    onChange: (e) => {
                      handleImageChange(e.target.value);
                    },
                  }}
                />
              );
            }}
          </ProFormDependency>

          <ProFormDependency name={['templateId', 'image']}>
            {({ templateId, image }) => (
              tagFallback ? (
                <ProFormText
                  name="tag"
                  label="镜像标签"
                  rules={[{ required: true, message: '请输入镜像标签' }]}
                  extra={
                    <>
                      {tagMessage}
                      {' '}
                      <a onClick={() => fetchTags(image, templateId)}><ReloadOutlined /> 刷新</a>
                    </>
                  }
                />
              ) : (
                <ProFormSelect
                  name="tag"
                  label="镜像标签"
                  rules={[{ required: true, message: '请选择镜像标签' }]}
                  options={tagOptions}
                  extra={<a onClick={() => fetchTags(image, templateId)}><ReloadOutlined /> 刷新标签</a>}
                  fieldProps={{
                    showSearch: true,
                    loading: tagLoading,
                    notFoundContent: tagLoading ? '加载中...' : '暂无标签',
                  }}
                />
              )
            )}
          </ProFormDependency>

          <ProFormTextArea name="remark" label="备注" />
          <ProFormDigit name="replicas" label="副本数" initialValue={1} min={1} fieldProps={{ precision: 0 }} />
          <ProFormText name="cpu" label="CPU 限制" initialValue="1" tooltip="如 0.5、1、2" />
          <ProFormText name="memory" label="内存限制" initialValue="1Gi" tooltip="如 512Mi、1Gi" />

          <ProFormDependency name={['templateId']}>
            {({ templateId }) => {
              const selectedTemplate = templates.find((t) => t.id === templateId);
              return (selectedTemplate?.envSchema || []).map((schema: any) => (
                schema.secret ? (
                  <ProFormText.Password
                    key={schema.key}
                    name={`env_${schema.key}`}
                    label={schema.label || schema.key}
                    tooltip={schema.description}
                    initialValue={schema.default}
                    rules={schema.required ? [{ required: true, message: `请填写${schema.label || schema.key}` }] : undefined}
                  />
                ) : (
                  <ProFormText
                    key={schema.key}
                    name={`env_${schema.key}`}
                    label={schema.label || schema.key}
                    tooltip={schema.description}
                    initialValue={schema.default}
                    rules={schema.required ? [{ required: true, message: `请填写${schema.label || schema.key}` }] : undefined}
                  />
                )
              ));
            }}
          </ProFormDependency>
        </ProForm>
      </Card>
    </PageContainer>
  );
}

export default () => {

  const { id } = useParams<{ id: string }>();
  const access = useAccess();

  const isNew = id === 'new';

  const [app, setApp] = useState<any>();
  const [runtimeStatus, setRuntimeStatus] = useState<any>();
  const [skillsContext, setSkillsContext] = useState<any>();

  const [templates, setTemplates] = useState<any[]>([]);

  const [logs, setLogs] = useState<string[]>([]);

  const [stats, setStats] = useState<any[]>([]);
  const [monitorStatus, setMonitorStatus] = useState<'connecting' | 'ok' | 'error' | 'unavailable'>('connecting');
  const [monitorMessage, setMonitorMessage] = useState<string>();

  const [mcpEndpoints, setMcpEndpoints] = useState<any[]>([]);
  const [mcpForm] = Form.useForm();

  const logRef = useRef<HTMLDivElement>(null);



  const load = async () => {

    if (!isNew) {

      const data = await getApp(Number(id));

      setApp(data);
      try {
        setRuntimeStatus(await getAppStatus(Number(id)));
      } catch {
        setRuntimeStatus(undefined);
      }
      try {
        setSkillsContext(await getAppSkills(Number(id)));
      } catch {
        setSkillsContext(undefined);
      }
      setMcpEndpoints(await listMcpEndpoints(Number(id)));

    }

    setTemplates(await listTemplates());

  };



  useEffect(() => { load(); }, [id]);

  useEffect(() => {
    if (isNew || !app?.runtimeRef) return undefined;
    const timer = setInterval(async () => {
      try {
        setRuntimeStatus(await getAppStatus(Number(id)));
      } catch {
        // ignore polling errors
      }
    }, 15000);
    return () => clearInterval(timer);
  }, [app, id, isNew]);

  useEffect(() => {

    if (isNew || !app?.runtimeRef) return;

    let es: EventSource | null = null;

    let cancelled = false;
    setMonitorStatus('connecting');
    setMonitorMessage(undefined);

    (async () => {

      try {

        const token = await getSseTicket();

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
            setStats((prev) => [...prev.slice(-20), {
              time: new Date().toLocaleTimeString(),
              mem: data.memUsedBytes || 0,
              cpu: data.cpuPercent || 0,
            }]);

          } catch {}

        };

        es.onerror = () => {
          setMonitorStatus('error');
          setMonitorMessage('无法连接监控流，请检查应用运行状态与权限');
        };

      } catch {

        setMonitorStatus('error');
        setMonitorMessage('无法获取监控凭证');
        message.error('无法连接监控流');

      }

    })();

    return () => {

      cancelled = true;

      es?.close();

    };

  }, [app, id, isNew]);



  useEffect(() => {

    if (isNew || !app?.runtimeRef) return;

    let es: EventSource | null = null;

    let cancelled = false;

    (async () => {

      try {

        const token = await getSseTicket();

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

    return () => {

      cancelled = true;

      es?.close();

    };

  }, [app, id, isNew]);



  const handleDownloadLogs = async () => {

    const token = await getSseTicket();

    window.open(getLogDownloadUrl(Number(id), token), '_blank');

  };

  if (isNew) {
    return <NewAppForm templates={templates} />;
  }



  const envSchema: any[] = app?.envSchema || [];

  const volumes: any[] = app?.volumes || [];

  const phaseColor: Record<string, string> = {
    RUNNING: 'green',
    STOPPED: 'default',
    CREATED: 'blue',
    ERROR: 'red',
    UNKNOWN: 'orange',
  };

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

      <Tabs items={[

        {

          key: 'overview',

          label: '概览',

          children: (

            <ProDescriptions column={2} dataSource={app} columns={[

              {
                title: '状态',
                dataIndex: 'status',
                render: (_, record) => (
                  <Space>
                    <Tag>{record.status}</Tag>
                    {runtimeStatus?.phase && (
                      <Tag color={phaseColor[runtimeStatus.phase] || 'default'}>
                        {runtimeStatus.phase}
                      </Tag>
                    )}
                    {runtimeStatus?.healthy != null && (
                      <Tag color={runtimeStatus.healthy ? 'green' : 'red'}>
                        {runtimeStatus.healthy ? '健康' : '不健康'}
                      </Tag>
                    )}
                  </Space>
                ),
              },
              {
                title: '运行消息',
                render: () => runtimeStatus?.message || '-',
              },

              { title: '镜像', dataIndex: 'image' },

              { title: '标签', dataIndex: 'tag' },

              { title: '运行时', dataIndex: 'runtimeProvider' },

              { title: '容器引用', dataIndex: 'runtimeRef' },
              {
                title: 'CPU 限制',
                render: (_, record) => record.resources?.cpu || '-',
              },
              {
                title: '内存限制',
                render: (_, record) => record.resources?.memory || '-',
              },
              {
                title: '副本数',
                dataIndex: 'replicas',
              },
              {
                title: '访问地址',
                dataIndex: 'accessUrls',
                render: (_, record) => (
                  (record.accessUrls || []).length > 0
                    ? (record.accessUrls || []).map((u: any) => (
                        <div key={u.name}>{u.name}: {u.url}</div>
                      ))
                    : '-'
                ),
              },
            ]} />

          ),

        },

        {
          key: 'config',
          label: '配置',
          children: <AppConfigPanel appId={Number(id)} app={app} onSaved={load} />,
        },

        {
          key: 'env',
          label: '环境变量',
          children: (
            <EnvConfigPanel
              appId={Number(id)}
              envSchema={envSchema}
              env={app?.env || []}
              onSaved={load}
            />
          ),
        },

        {
          key: 'skills',
          label: 'Skills',
          children: (
            <SkillsPanel context={skillsContext} canWriteSkill={access.canWriteSkill} />
          ),
        },

        {
          key: 'console',
          label: '控制台',
          children: (
            <Card>
              {access.canUseAppTerminal ? (
                <AppTerminal appId={Number(id)} disabled={!app?.runtimeRef || app?.status !== 'running'} />
              ) : (
                <Typography.Text type="secondary">无终端访问权限（需要 app:terminal）</Typography.Text>
              )}
            </Card>
          ),
        },

        {

          key: 'files',

          label: '数据卷文件',

          children: (

            <Card>

              <VolumeFilesPanel appId={Number(id)} volumes={volumes} />

            </Card>

          ),

        },

        {

          key: 'mcp',

          label: 'MCP 端点',

          children: (

            <Card>

              {access.canWriteApp && (

                <Form form={mcpForm} layout="inline" style={{ marginBottom: 16 }}

                  onFinish={async (values) => {

                    await createMcpEndpoint({ applicationId: Number(id), url: values.url });

                    message.success('MCP 端点已注册');

                    mcpForm.resetFields();

                    setMcpEndpoints(await listMcpEndpoints(Number(id)));

                  }}

                >

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

                dataSource={mcpEndpoints}

                columns={[

                  { title: 'URL', dataIndex: 'url', ellipsis: true },

                  {

                    title: '状态',

                    dataIndex: 'enabled',

                    render: (v) => <Tag color={v ? 'green' : 'default'}>{v ? '启用' : '禁用'}</Tag>,

                  },

                  {

                    title: '工具数',

                    render: (_, r) => (r.tools || []).length,

                  },

                  {

                    title: '发现时间',

                    dataIndex: 'discoveredAt',

                    render: (v) => (v ? new Date(v).toLocaleString() : '-'),

                  },

                  {

                    title: '操作',

                    render: (_, record) => [

                      access.canWriteApp && (

                        <a key="discover" onClick={async () => {

                          await discoverMcpEndpoint(record.id);

                          message.success('工具列表已更新');

                          setMcpEndpoints(await listMcpEndpoints(Number(id)));

                        }}>发现工具</a>

                      ),

                      access.canWriteApp && (

                        <Popconfirm key="del" title="确认删除？" onConfirm={async () => {

                          await deleteMcpEndpoint(record.id);

                          message.success('已删除');

                          setMcpEndpoints(await listMcpEndpoints(Number(id)));

                        }}>

                          <a style={{ marginLeft: 8, color: 'red' }}>删除</a>

                        </Popconfirm>

                      ),

                    ].filter(Boolean),

                  },

                ]}

              />

              {mcpEndpoints.some((e) => (e.tools || []).length > 0) && (

                <div style={{ marginTop: 16 }}>

                  <Typography.Text strong>已发现工具</Typography.Text>

                  {mcpEndpoints.filter((e) => (e.tools || []).length > 0).map((ep) => (

                    <div key={ep.id} style={{ marginTop: 8 }}>

                      <Typography.Text type="secondary">{ep.url}</Typography.Text>

                      <div>

                        {(ep.tools || []).map((t: any) => (

                          <Tag key={t.name} style={{ marginTop: 4 }}>{t.name}</Tag>

                        ))}

                      </div>

                    </div>

                  ))}

                </div>

              )}

            </Card>

          ),

        },

        {

          key: 'monitor',

          label: '监控',

          children: (
            <MonitorPanel
              stats={stats}
              monitorStatus={monitorStatus}
              monitorMessage={monitorMessage}
            />
          ),

        },

        {

          key: 'logs',

          label: '日志',

          children: (

            <>

              <div style={{ marginBottom: 8 }}>

                <Button onClick={handleDownloadLogs}>下载日志</Button>

              </div>

              <div ref={logRef} style={{ background: '#111', color: '#0f0', padding: 12, height: 400, overflow: 'auto', fontFamily: 'monospace' }}>

                {logs.map((l, i) => <div key={i}>{l}</div>)}

              </div>

            </>

          ),

        },

      ]} />

    </PageContainer>

  );

};

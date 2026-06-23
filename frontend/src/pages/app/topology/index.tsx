import { PageContainer, ProTable } from '@ant-design/pro-components';
import { ModalForm, ProForm, ProFormSelect, ProFormText, ProFormTextArea } from '@ant-design/pro-components';
import {
  Alert, Button, Card, Descriptions, Drawer, Modal, Popconfirm, Space, Table, Tabs, Tag, Timeline, Typography, Upload, message,
} from 'antd';
import { useRef, useState, useEffect } from 'react';
import { useAccess, history, useSearchParams } from '@umijs/max';
import {
  addTopologyLink,
  addTopologyNode,
  createTopology,
  deleteTopology,
  deployTopology,
  getTopology,
  listTopologies,
  redeployTopology,
  removeTopologyLink,
  removeTopologyNode,
} from '@/services/topology';
import { listApps } from '@/services/app';
import { listDelegations, recordDelegation } from '@/services/delegation';
import { createSkill, deleteSkill, downloadSkill, listSkills, notifyTopologySkillsReload } from '@/services/skill';
import type { TopologyDto } from '@/services/types/topology';

const statusMap: Record<string, { text: string; color: string }> = {
  draft: { text: '草稿', color: 'default' },
  deploying: { text: '部署中', color: 'processing' },
  deployed: { text: '已部署', color: 'green' },
};

const roleOptions = [
  { label: '网关 (gateway)', value: 'gateway' },
  { label: '工作节点 (worker)', value: 'worker' },
];

const protocolOptions = [
  { label: 'HTTP', value: 'http' },
  { label: 'MCP', value: 'mcp' },
];

const roleColor: Record<string, string> = {
  gateway: 'blue',
  worker: 'green',
};

const delegationStatusColor: Record<string, string> = {
  running: 'processing',
  completed: 'success',
  failed: 'error',
  cancelled: 'default',
};

function TopologyGraph({ nodes, links }: { nodes: any[]; links: any[] }) {
  const gateways = nodes.filter((n) => n.role === 'gateway');
  const workers = nodes.filter((n) => n.role !== 'gateway');
  const width = 560;
  const leftX = 120;
  const rightX = 440;
  const nodeHeight = 56;
  const gap = 24;

  const layoutNodes = [
    ...gateways.map((n, i) => ({ ...n, x: leftX, y: 40 + i * (nodeHeight + gap) })),
    ...workers.map((n, i) => ({ ...n, x: rightX, y: 40 + i * (nodeHeight + gap) })),
  ];
  const height = Math.max(200, layoutNodes.length > 0
    ? layoutNodes[layoutNodes.length - 1].y + nodeHeight + 40
    : 160);
  const nodeById = Object.fromEntries(layoutNodes.map((n) => [n.id, n]));

  return (
    <svg width="100%" viewBox={`0 0 ${width} ${height}`} style={{ background: '#fafafa', borderRadius: 8 }}>
      {links.map((link) => {
        const from = nodeById[link.fromNodeId];
        const to = nodeById[link.toNodeId];
        if (!from || !to) return null;
        const x1 = from.x + 80;
        const y1 = from.y + nodeHeight / 2;
        const x2 = to.x;
        const y2 = to.y + nodeHeight / 2;
        return (
          <g key={link.id}>
            <line x1={x1} y1={y1} x2={x2} y2={y2} stroke={link.protocol === 'mcp' ? '#722ed1' : '#1677ff'} strokeWidth={2} markerEnd="url(#arrow)" />
            <text x={(x1 + x2) / 2} y={(y1 + y2) / 2 - 6} textAnchor="middle" fontSize={11} fill="#666">{link.protocol}</text>
          </g>
        );
      })}
      <defs>
        <marker id="arrow" markerWidth="8" markerHeight="8" refX="6" refY="3" orient="auto">
          <path d="M0,0 L6,3 L0,6 Z" fill="#1677ff" />
        </marker>
      </defs>
      {layoutNodes.map((node) => (
        <g key={node.id}>
          <rect x={node.x} y={node.y} width={160} height={nodeHeight} rx={8} fill="#fff" stroke="#d9d9d9" strokeWidth={1.5} />
          <text x={node.x + 12} y={node.y + 22} fontSize={13} fontWeight={600} fill="#262626">{node.applicationName}</text>
          <text x={node.x + 12} y={node.y + 42} fontSize={11} fill="#8c8c8c">{node.role} · {node.applicationStatus || '-'}</text>
        </g>
      ))}
      {layoutNodes.length === 0 && (
        <text x={width / 2} y={height / 2} textAnchor="middle" fill="#999">添加成员后显示拓扑图</text>
      )}
    </svg>
  );
}

export default () => {
  const actionRef = useRef<any>();
  const access = useAccess();
  const [createOpen, setCreateOpen] = useState(false);
  const [memberOpen, setMemberOpen] = useState(false);
  const [linkOpen, setLinkOpen] = useState(false);
  const [detailOpen, setDetailOpen] = useState(false);
  const [currentTopology, setCurrentTopology] = useState<TopologyDto | null>(null);
  const [detailTopology, setDetailTopology] = useState<TopologyDto | null>(null);
  const [apps, setApps] = useState<any[]>([]);
  const [deployKey, setDeployKey] = useState<string | null>(null);
  const [deploySummary, setDeploySummary] = useState<TopologyDto | null>(null);
  const [delegations, setDelegations] = useState<any[]>([]);
  const [skills, setSkills] = useState<any[]>([]);
  const [delegationOpen, setDelegationOpen] = useState(false);
  const [skillOpen, setSkillOpen] = useState(false);
  const [detailTab, setDetailTab] = useState('overview');
  const [searchParams] = useSearchParams();

  const loadApps = async () => {
    const data = await listApps();
    setApps(data.filter((a: any) => ['running', 'created', 'stopped'].includes(a.status)));
  };

  const openDetail = async (record: { id: number }, initialTab?: string) => {
    const detail = await getTopology(record.id);
    setDetailTopology(detail);
    const validTabs = ['overview', 'delegations', 'skills'];
    setDetailTab(initialTab && validTabs.includes(initialTab) ? initialTab : 'overview');
    setDetailOpen(true);
    if (access.canViewDelegation) {
      try { setDelegations(await listDelegations({ topologyId: record.id })); } catch { setDelegations([]); }
    }
    if (access.canViewSkill) {
      try { setSkills(await listSkills({ topologyId: record.id })); } catch { setSkills([]); }
    }
  };

  const refreshDetail = async (id: number) => {
    const detail = await getTopology(id);
    setDetailTopology(detail);
    actionRef.current?.reload();
    if (access.canViewDelegation) {
      try { setDelegations(await listDelegations({ topologyId: id })); } catch { /* ignore */ }
    }
    if (access.canViewSkill) {
      try { setSkills(await listSkills({ topologyId: id })); } catch { /* ignore */ }
    }
  };

  useEffect(() => {
    const topologyIdParam = searchParams.get('topologyId');
    if (!topologyIdParam) return;
    const id = Number(topologyIdParam);
    if (Number.isNaN(id)) return;
    openDetail({ id }, searchParams.get('tab') || undefined);
  }, [searchParams]);

  const nodeOptions = (detailTopology?.nodes || []).map((n: any) => ({
    label: `${n.applicationName} (${n.role})`,
    value: n.id,
  }));

  return (
    <PageContainer title="协同拓扑">
      <ProTable
        actionRef={actionRef}
        rowKey="id"
        search={false}
        request={async () => {
          const data = await listTopologies();
          return { data, success: true };
        }}
        toolBarRender={() => access.canWriteTopology ? [
          <Button key="add" type="primary" onClick={() => setCreateOpen(true)}>新建拓扑</Button>,
        ] : []}
        columns={[
          { title: '名称', dataIndex: 'name' },
          { title: '网络', dataIndex: 'networkName' },
          {
            title: '状态',
            dataIndex: 'status',
            render: (_, r) => {
              const s = statusMap[r.status] || { text: r.status, color: 'default' };
              return (
                <Space>
                  <Tag color={s.color}>{s.text}</Tag>
                  {r.needsKeyRedeploy && <Tag color="warning">密钥需轮换重部署</Tag>}
                </Space>
              );
            },
          },
          { title: '成员数', render: (_, r) => (r.nodes || []).length },
          { title: '链路数', render: (_, r) => (r.links || []).length },
          { title: '描述', dataIndex: 'description', ellipsis: true },
          {
            title: '操作',
            valueType: 'option',
            render: (_, record) => [
              <a key="detail" onClick={() => openDetail(record)}>详情</a>,
              access.canWriteTopology && (
                <a key="add" onClick={async () => {
                  setCurrentTopology(record);
                  await loadApps();
                  setMemberOpen(true);
                }}>添加应用</a>
              ),
              access.canDeployTopology && (
                <a key="deploy" onClick={async () => {
                  const result = await deployTopology(record.id);
                  message.success('拓扑部署已触发');
                  if (result.inferenceKeyRaw) setDeployKey(result.inferenceKeyRaw);
                  setDeploySummary(result);
                  actionRef.current?.reload();
                }}>部署</a>
              ),
              access.canWriteTopology && (
                <Popconfirm key="del" title="确认删除此拓扑？" onConfirm={async () => {
                  await deleteTopology(record.id);
                  message.success('已删除');
                  actionRef.current?.reload();
                }}>
                  <a style={{ color: 'red' }}>删除</a>
                </Popconfirm>
              ),
            ].filter(Boolean),
          },
        ]}
      />

      <Drawer
        title={`拓扑详情 - ${detailTopology?.name || ''}`}
        open={detailOpen}
        width={720}
        onClose={() => setDetailOpen(false)}
        extra={detailTopology && access.canDeployTopology ? (
          <Space>
            {detailTopology.needsKeyRedeploy && (
              <Button type="primary" danger onClick={async () => {
                const result = await redeployTopology(detailTopology.id);
                message.success('已重新部署并更新推理密钥');
                if (result.inferenceKeyRaw) setDeployKey(result.inferenceKeyRaw);
                setDeploySummary(result);
                await refreshDetail(detailTopology.id);
              }}>轮换密钥并重部署</Button>
            )}
            <Button type="primary" onClick={async () => {
              const result = await deployTopology(detailTopology.id);
              message.success('拓扑部署已触发');
              if (result.inferenceKeyRaw) setDeployKey(result.inferenceKeyRaw);
              setDeploySummary(result);
              await refreshDetail(detailTopology.id);
            }}>部署</Button>
          </Space>
        ) : null}
      >
        {detailTopology && (
          <Tabs activeKey={detailTab} onChange={setDetailTab} items={[
            {
              key: 'overview',
              label: '概览',
              children: (
                <>
            {detailTopology.needsKeyRedeploy && (
              <Alert
                type="warning"
                showIcon
                style={{ marginBottom: 16 }}
                message="推理 API 密钥已轮换，请重新部署拓扑以注入新密钥到成员应用"
              />
            )}
            <Descriptions size="small" column={2} style={{ marginBottom: 16 }}>
              <Descriptions.Item label="状态">
                <Tag color={statusMap[detailTopology.status]?.color}>{statusMap[detailTopology.status]?.text || detailTopology.status}</Tag>
              </Descriptions.Item>
              <Descriptions.Item label="网络">{detailTopology.networkName}</Descriptions.Item>
              <Descriptions.Item label="描述" span={2}>{detailTopology.description || '-'}</Descriptions.Item>
            </Descriptions>

            <Card title="拓扑画布" size="small" style={{ marginBottom: 16 }}>
              <TopologyGraph nodes={detailTopology.nodes || []} links={detailTopology.links || []} />
            </Card>

            <Card
              title="成员节点"
              size="small"
              style={{ marginBottom: 16 }}
              extra={access.canWriteTopology && (
                <Button size="small" onClick={async () => {
                  setCurrentTopology(detailTopology);
                  await loadApps();
                  setMemberOpen(true);
                }}>添加成员</Button>
              )}
            >
              {(detailTopology.nodes || []).map((node: any) => (
                <Tag key={node.id} color={roleColor[node.role] || 'default'} style={{ marginBottom: 4 }}>
                  {node.applicationName}
                  <Tag style={{ marginLeft: 4 }}>{node.role}</Tag>
                  {access.canWriteTopology && (
                    <Popconfirm title="确认移除？" onConfirm={async () => {
                      await removeTopologyNode(detailTopology.id, node.applicationId);
                      message.success('已移除');
                      await refreshDetail(detailTopology.id);
                    }}>
                      <a style={{ marginLeft: 8 }}>移除</a>
                    </Popconfirm>
                  )}
                </Tag>
              ))}
            </Card>

            <Card
              title="互联链路"
              size="small"
              style={{ marginBottom: 16 }}
              extra={access.canWriteTopology && (detailTopology.nodes || []).length >= 2 && (
                <Button size="small" onClick={() => setLinkOpen(true)}>添加链路</Button>
              )}
            >
              <Table
                size="small"
                rowKey="id"
                pagination={false}
                dataSource={detailTopology.links || []}
                columns={[
                  { title: '源节点', dataIndex: 'fromApplicationName' },
                  { title: '目标节点', dataIndex: 'toApplicationName' },
                  { title: '协议', dataIndex: 'protocol', render: (v) => <Tag>{v}</Tag> },
                  { title: '对等 URL', dataIndex: 'peerUrl', ellipsis: true },
                  access.canWriteTopology ? {
                    title: '操作',
                    render: (_, link) => (
                      <Popconfirm title="确认删除链路？" onConfirm={async () => {
                        await removeTopologyLink(detailTopology.id, link.id);
                        message.success('链路已删除');
                        await refreshDetail(detailTopology.id);
                      }}>
                        <a style={{ color: 'red' }}>删除</a>
                      </Popconfirm>
                    ),
                  } : {},
                ].filter((c) => Object.keys(c).length > 0)}
              />
            </Card>

            {detailTopology.status === 'deployed' && (
              <>
                <Card title="成员访问地址" size="small" style={{ marginBottom: 16 }}>
                  <Table
                    size="small"
                    rowKey={(r) => `${r.applicationId}-${r.name}`}
                    pagination={false}
                    dataSource={detailTopology.memberAccessUrls || []}
                    columns={[
                      { title: '应用', dataIndex: 'applicationName' },
                      { title: '角色', dataIndex: 'role' },
                      { title: '端口', dataIndex: 'name' },
                      { title: '外部访问', dataIndex: 'url', ellipsis: true },
                      { title: '集群内互访', dataIndex: 'peerUrl', ellipsis: true },
                      {
                        title: '控制台',
                        render: (_, r) => (
                          <a onClick={() => history.push(`/app/detail/${r.applicationId}?tab=webConsole:${r.name}`)}>
                            打开 {r.name}
                          </a>
                        ),
                      },
                    ]}
                  />
                </Card>
                <Card title="注入环境变量预览" size="small" style={{ marginBottom: 16 }}>
                  <Table
                    size="small"
                    rowKey={(r) => `${r.applicationId}-${r.envKey}`}
                    pagination={false}
                    dataSource={detailTopology.injectedEnv || []}
                    columns={[
                      { title: '目标应用', dataIndex: 'applicationName' },
                      { title: '变量名', dataIndex: 'envKey' },
                      { title: '值', dataIndex: 'envValue', ellipsis: true },
                      { title: '来源', dataIndex: 'source' },
                    ]}
                  />
                </Card>
                {(detailTopology.injectedSkills || []).length > 0 && (
                  <Card title="注入共享技能" size="small">
                    <Table
                      size="small"
                      rowKey="id"
                      pagination={false}
                      dataSource={detailTopology.injectedSkills}
                      columns={[
                        { title: '技能名称', dataIndex: 'name' },
                        { title: '文件路径', dataIndex: 'filePath', ellipsis: true, render: (v) => v || '-' },
                      ]}
                    />
                  </Card>
                )}
              </>
            )}
                </>
              ),
            },
            access.canViewDelegation ? {
              key: 'delegations',
              label: '委派追踪',
              children: (
                <Card
                  size="small"
                  title="子智能体委派时间线"
                  extra={access.canWriteDelegation && (
                    <Button size="small" type="primary" onClick={() => setDelegationOpen(true)}>记录委派</Button>
                  )}
                >
                  <Timeline
                    items={(delegations || []).map((d) => ({
                      color: delegationStatusColor[d.status] || 'gray',
                      children: (
                        <div>
                          <Space wrap>
                            <Tag color={delegationStatusColor[d.status]}>{d.status}</Tag>
                            <Typography.Text strong>{d.parentAppName} → {d.childAppName}</Typography.Text>
                          </Space>
                          <div style={{ marginTop: 4 }}>{d.taskSummary}</div>
                          <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                            {d.startedAt ? new Date(d.startedAt).toLocaleString() : ''}
                            {d.completedAt ? ` · 完成 ${new Date(d.completedAt).toLocaleString()}` : ''}
                            {d.parentAppId && (
                              <>
                                {' · '}
                                <a onClick={() => history.push(`/app/detail/${d.parentAppId}?tab=delegation`)}>
                                  {d.parentAppName || `父应用 #${d.parentAppId}`}
                                </a>
                              </>
                            )}
                            {d.childAppId && (
                              <>
                                {' · '}
                                <a onClick={() => history.push(`/app/detail/${d.childAppId}?tab=delegation`)}>
                                  {d.childAppName || `子应用 #${d.childAppId}`}
                                </a>
                              </>
                            )}
                          </Typography.Text>
                        </div>
                      ),
                    }))}
                  />
                  {(delegations || []).length === 0 && (
                    <Typography.Text type="secondary">暂无委派记录，可通过 API 或「记录委派」手动登记（MVP）</Typography.Text>
                  )}
                </Card>
              ),
            } : null,
            access.canViewSkill ? {
              key: 'skills',
              label: '共享技能',
              children: (
                <Card
                  size="small"
                  title="拓扑共享技能"
                  extra={access.canWriteSkill && (
                    <Space>
                      <Button
                        size="small"
                        onClick={async () => {
                          const result = await notifyTopologySkillsReload(detailTopology.id);
                          const count = result.skills?.length ?? skills.length;
                          message.success(`已通知 Agent 重新加载技能（${count} 项）`);
                        }}
                      >
                        通知 Agent 重新加载技能
                      </Button>
                      <Button size="small" type="primary" onClick={() => setSkillOpen(true)}>上传技能</Button>
                    </Space>
                  )}
                >
                  <Table
                    size="small"
                    rowKey="id"
                    pagination={false}
                    dataSource={skills}
                    columns={[
                      { title: '名称', dataIndex: 'name' },
                      { title: '描述', dataIndex: 'description', ellipsis: true },
                      { title: '来源应用', dataIndex: 'applicationName', render: (v, record) => (
                        record.applicationId
                          ? <a onClick={() => history.push(`/app/detail/${record.applicationId}?tab=skills`)}>{v || `#${record.applicationId}`}</a>
                          : (v || '拓扑共享')
                      ) },
                      { title: '文件', dataIndex: 'filePath', ellipsis: true, render: (v) => v || '-' },
                      {
                        title: '操作',
                        render: (_, record) => [
                          record.filePath && (
                            <a key="down" onClick={async () => {
                              const { url } = await downloadSkill(record.id);
                              window.open(url);
                            }}>下载</a>
                          ),
                          access.canWriteSkill && (
                            <Popconfirm key="del" title="确认删除？" onConfirm={async () => {
                              await deleteSkill(record.id);
                              message.success('已删除');
                              setSkills(await listSkills({ topologyId: detailTopology.id }));
                            }}>
                              <a style={{ color: 'red', marginLeft: 8 }}>删除</a>
                            </Popconfirm>
                          ),
                        ].filter(Boolean),
                      },
                    ]}
                  />
                </Card>
              ),
            } : null,
          ].filter(Boolean) as any[]} />
        )}
      </Drawer>

      <ModalForm
        title="新建协同拓扑"
        open={createOpen}
        onOpenChange={setCreateOpen}
        modalProps={{ destroyOnClose: true }}
        onFinish={async (values) => {
          await createTopology(values);
          message.success('拓扑已创建');
          actionRef.current?.reload();
          return true;
        }}
      >
        <ProFormText name="name" label="名称" rules={[{ required: true, message: '请输入名称' }]} />
        <ProFormText name="networkName" label="Docker 网络名" initialValue="agentpanel-net"
          tooltip="成员应用将加入此内部网络以实现互访" />
        <ProFormTextArea name="description" label="描述" />
      </ModalForm>

      <ModalForm
        title={`添加成员 - ${currentTopology?.name || ''}`}
        open={memberOpen}
        onOpenChange={setMemberOpen}
        modalProps={{ destroyOnClose: true }}
        onFinish={async (values) => {
          if (!currentTopology) return false;
          await addTopologyNode(currentTopology.id, values);
          message.success('成员已添加');
          if (detailOpen) await refreshDetail(currentTopology.id);
          actionRef.current?.reload();
          return true;
        }}
      >
        <ProFormSelect
          name="applicationId"
          label="应用"
          rules={[{ required: true, message: '请选择应用' }]}
          options={apps.map((a) => ({ label: `${a.name} (${a.status})`, value: a.id }))}
        />
        <ProFormSelect name="role" label="角色" initialValue="worker" options={roleOptions}
          rules={[{ required: true }]} />
      </ModalForm>

      <ModalForm
        title="添加互联链路"
        open={linkOpen}
        onOpenChange={setLinkOpen}
        modalProps={{ destroyOnClose: true }}
        onFinish={async (values) => {
          if (!detailTopology) return false;
          await addTopologyLink(detailTopology.id, values);
          message.success('链路已添加');
          await refreshDetail(detailTopology.id);
          return true;
        }}
      >
        <ProFormSelect name="fromNodeId" label="源节点" rules={[{ required: true }]} options={nodeOptions} />
        <ProFormSelect name="toNodeId" label="目标节点" rules={[{ required: true }]} options={nodeOptions} />
        <ProFormSelect name="protocol" label="协议" initialValue="http" options={protocolOptions}
          rules={[{ required: true }]} />
      </ModalForm>

      <ModalForm
        title="记录委派"
        open={delegationOpen}
        onOpenChange={setDelegationOpen}
        modalProps={{ destroyOnClose: true }}
        onFinish={async (values) => {
          if (!detailTopology) return false;
          await recordDelegation({
            topologyId: detailTopology.id,
            parentAppId: values.parentAppId,
            childAppId: values.childAppId,
            taskSummary: values.taskSummary,
            status: values.status || 'completed',
            result: values.result ? { note: values.result } : {},
          });
          message.success('委派已记录');
          setDelegations(await listDelegations({ topologyId: detailTopology.id }));
          return true;
        }}
      >
        <ProFormSelect
          name="parentAppId"
          label="父应用（委派方）"
          rules={[{ required: true }]}
          options={(detailTopology?.nodes || []).map((n: any) => ({
            label: `${n.applicationName} (${n.role})`,
            value: n.applicationId,
          }))}
        />
        <ProFormSelect
          name="childAppId"
          label="子应用（被委派方）"
          rules={[{ required: true }]}
          options={(detailTopology?.nodes || []).map((n: any) => ({
            label: `${n.applicationName} (${n.role})`,
            value: n.applicationId,
          }))}
        />
        <ProFormTextArea name="taskSummary" label="任务摘要" rules={[{ required: true }]} />
        <ProFormSelect
          name="status"
          label="状态"
          initialValue="completed"
          options={[
            { label: '进行中', value: 'running' },
            { label: '已完成', value: 'completed' },
            { label: '失败', value: 'failed' },
            { label: '已取消', value: 'cancelled' },
          ]}
        />
        <ProFormTextArea name="result" label="结果备注（可选）" />
      </ModalForm>

      <ModalForm
        title="上传共享技能"
        open={skillOpen}
        onOpenChange={setSkillOpen}
        modalProps={{ destroyOnClose: true }}
        onFinish={async (values) => {
          if (!detailTopology) return false;
          const fileList = values.skillFile as any[];
          const file = fileList?.[0]?.originFileObj as File | undefined;
          await createSkill({
            topologyId: detailTopology.id,
            name: values.name,
            description: values.description,
            content: values.content,
            applicationId: values.applicationId,
          }, file);
          message.success('技能已保存');
          setSkills(await listSkills({ topologyId: detailTopology.id }));
          return true;
        }}
      >
        <ProFormText name="name" label="技能名称" rules={[{ required: true }]} />
        <ProFormTextArea name="description" label="描述" />
        <ProFormTextArea name="content" label="技能内容（Markdown/文本）" fieldProps={{ rows: 4 }} />
        <ProFormSelect
          name="applicationId"
          label="来源应用（可选）"
          options={(detailTopology?.nodes || []).map((n: any) => ({
            label: n.applicationName,
            value: n.applicationId,
          }))}
        />
        <ProForm.Item name="skillFile" label="技能文件（可选）" valuePropName="fileList" getValueFromEvent={(e) => (Array.isArray(e) ? e : e?.fileList)}>
          <Upload beforeUpload={() => false} maxCount={1}>
            <Button>选择文件</Button>
          </Upload>
        </ProForm.Item>
      </ModalForm>

      <Modal
        title="推理 API 密钥"
        open={!!deployKey}
        onCancel={() => setDeployKey(null)}
        footer={[
          <Button key="close" type="primary" onClick={() => setDeployKey(null)}>我已保存，关闭</Button>,
        ]}
      >
        <Typography.Text type="warning">
          部署已自动创建/更新推理密钥并注入成员应用环境变量。密钥仅显示一次，请立即复制保存。
        </Typography.Text>
        <Typography.Paragraph copyable={{ text: deployKey || '' }} code style={{ marginTop: 16, wordBreak: 'break-all' }}>
          {deployKey}
        </Typography.Paragraph>
      </Modal>

      <Modal
        title="部署结果摘要"
        open={!!deploySummary}
        onCancel={() => setDeploySummary(null)}
        footer={[<Button key="close" onClick={() => setDeploySummary(null)}>关闭</Button>]}
        width={640}
      >
        {deploySummary && (
          <>
            <Typography.Paragraph>状态：<Tag color="green">{deploySummary.status}</Tag></Typography.Paragraph>
            {(deploySummary.memberAccessUrls || []).length > 0 && (
              <>
                <Typography.Text strong>成员访问地址</Typography.Text>
                <Table
                  size="small"
                  style={{ marginTop: 8, marginBottom: 16 }}
                  pagination={false}
                  rowKey={(r) => `${r.applicationId}-${r.name}`}
                  dataSource={deploySummary.memberAccessUrls}
                  columns={[
                    { title: '应用', dataIndex: 'applicationName' },
                    { title: '外部', dataIndex: 'url' },
                    { title: '集群内', dataIndex: 'peerUrl' },
                    {
                      title: '控制台',
                      render: (_, r) => (
                        <a onClick={() => { setDeploySummary(null); history.push(`/app/detail/${r.applicationId}?tab=webConsole:${r.name}`); }}>
                          打开 {r.name}
                        </a>
                      ),
                    },
                  ]}
                />
              </>
            )}
            {(deploySummary.injectedEnv || []).length > 0 && (
              <>
                <Typography.Text strong>注入环境变量</Typography.Text>
                <Table
                  size="small"
                  style={{ marginTop: 8, marginBottom: 16 }}
                  pagination={false}
                  rowKey={(r) => `${r.applicationId}-${r.envKey}`}
                  dataSource={deploySummary.injectedEnv}
                  columns={[
                    { title: '应用', dataIndex: 'applicationName' },
                    { title: '变量', dataIndex: 'envKey' },
                    { title: '值', dataIndex: 'envValue', ellipsis: true },
                    { title: '来源', dataIndex: 'source' },
                  ]}
                />
              </>
            )}
            {(deploySummary.injectedSkills || []).length > 0 && (
              <>
                <Typography.Text strong>注入共享技能</Typography.Text>
                <Table
                  size="small"
                  style={{ marginTop: 8 }}
                  pagination={false}
                  rowKey="id"
                  dataSource={deploySummary.injectedSkills}
                  columns={[
                    { title: '技能名称', dataIndex: 'name' },
                    { title: '文件路径', dataIndex: 'filePath', ellipsis: true, render: (v) => v || '-' },
                  ]}
                />
              </>
            )}
          </>
        )}
      </Modal>
    </PageContainer>
  );
};

import { PageContainer, ProTable } from '@ant-design/pro-components';
import { Button, Card, Col, Input, Row, Select, Space, Tabs, Tree, Upload, message } from 'antd';
import type { DataNode } from 'antd/es/tree';
import { history, useAccess, useSearchParams } from '@umijs/max';
import { useEffect, useRef, useState } from 'react';
import { deleteFile, listFiles, presignFile } from '@/services/files';
import { listApps, getAppSkills } from '@/services/app';
import { parseAppIdFromObjectKey } from '@/utils/appStorage';
import VolumeFilesPanel from '@/pages/app/detail/components/VolumeFilesPanel';
import AppObjectStoragePanel from '@/pages/app/detail/components/AppObjectStoragePanel';
import AppSkillFilesPanel from '@/pages/app/detail/components/AppSkillFilesPanel';

type FileScope = { type: 'global' } | { type: 'app'; appId: number; appName: string };

const VALID_SUB_TABS = ['volume', 'object', 'skills'] as const;
type SubTab = typeof VALID_SUB_TABS[number];

export default () => {
  const access = useAccess();
  const [searchParams] = useSearchParams();
  const actionRef = useRef<any>();
  const [treeData, setTreeData] = useState<DataNode[]>([]);
  const [apps, setApps] = useState<any[]>([]);
  const [scope, setScope] = useState<FileScope>({ type: 'global' });
  const [prefix, setPrefix] = useState('');
  const [filterAppId, setFilterAppId] = useState<number | undefined>();
  const [selectedApp, setSelectedApp] = useState<any>();
  const [skillsContext, setSkillsContext] = useState<any>();
  const [activeSubTab, setActiveSubTab] = useState<SubTab>('volume');

  const selectApp = async (appId: number, apps?: any[]) => {
    const appList = apps || await listApps();
    const app = appList.find((a: any) => a.id === appId);
    setSelectedApp(app);
    setScope({ type: 'app', appId, appName: app?.name || String(appId) });
    try {
      setSkillsContext(await getAppSkills(appId));
    } catch {
      setSkillsContext(undefined);
    }
  };

  useEffect(() => {
    listApps().then(async (appList) => {
      setApps(appList || []);
      setTreeData([
        { key: 'global', title: '全局对象存储 (MinIO)', isLeaf: true },
        {
          key: 'apps',
          title: '应用数据卷',
          children: (appList || []).map((app: any) => ({
            key: `app-${app.id}`,
            title: `${app.name} (${app.templateCode || app.templateName})`,
            isLeaf: true,
          })),
        },
      ]);
      const appIdParam = searchParams.get('appId');
      const subTabParam = searchParams.get('subTab');
      if (subTabParam && VALID_SUB_TABS.includes(subTabParam as SubTab)) {
        setActiveSubTab(subTabParam as SubTab);
      }
      if (appIdParam) {
        await selectApp(Number(appIdParam), appList);
      }
    });
  }, [searchParams]);

  const onSelect = async (keys: React.Key[]) => {
    const key = String(keys[0] || '');
    if (key === 'global') {
      setScope({ type: 'global' });
      setSelectedApp(undefined);
      setSkillsContext(undefined);
      history.replace('/files');
      actionRef.current?.reload();
      return;
    }
    if (key.startsWith('app-')) {
      const appId = Number(key.replace('app-', ''));
      await selectApp(appId);
      history.replace(`/files?appId=${appId}&subTab=${activeSubTab}`);
    }
  };

  const onSubTabChange = (key: string) => {
    const subTab = key as SubTab;
    setActiveSubTab(subTab);
    if (scope.type === 'app') {
      history.replace(`/files?appId=${scope.appId}&subTab=${subTab}`);
    }
  };

  return (
    <PageContainer title="统一文件中心" subTitle="全局对象存储、应用数据卷与技能附件">
      <Row gutter={16}>
        <Col span={6}>
          <Card title="浏览范围" size="small">
            <Tree
              defaultExpandAll
              selectedKeys={scope.type === 'app' ? [`app-${scope.appId}`] : scope.type === 'global' ? ['global'] : []}
              treeData={treeData}
              onSelect={onSelect}
            />
          </Card>
        </Col>
        <Col span={18}>
          {scope.type === 'global' ? (
            <>
              <Space style={{ marginBottom: 16 }} wrap>
                <Input.Search
                  placeholder="前缀过滤"
                  style={{ width: 300 }}
                  onSearch={setPrefix}
                  enterButton="搜索"
                />
                <Select
                  allowClear
                  placeholder="按应用筛选 (apps/{id}/)"
                  style={{ width: 220 }}
                  value={filterAppId}
                  onChange={(v) => {
                    setFilterAppId(v);
                    actionRef.current?.reload();
                  }}
                  options={(apps || []).map((app: any) => ({
                    label: `${app.name} (#${app.id})`,
                    value: app.id,
                  }))}
                />
              </Space>
              <ProTable
                actionRef={actionRef}
                rowKey="key"
                search={false}
                params={{ prefix, filterAppId }}
                request={async () => {
                  const data = filterAppId
                    ? await listFiles(undefined, filterAppId)
                    : await listFiles(prefix);
                  const filtered = filterAppId
                    ? (data || []).filter((f: any) => parseAppIdFromObjectKey(f.key) === filterAppId)
                    : data;
                  return { data: filtered, success: true };
                }}
                toolBarRender={() => access.canWriteFiles ? [
                  <Upload
                    key="upload"
                    showUploadList={false}
                    customRequest={async ({ file, onSuccess, onError }) => {
                      try {
                        const key = (file as File).name;
                        const { url } = await presignFile(key, 'put');
                        await fetch(url, { method: 'PUT', body: file as File });
                        message.success('上传成功');
                        actionRef.current?.reload();
                        onSuccess?.(null);
                      } catch (e) {
                        onError?.(e as Error);
                      }
                    }}
                  >
                    <Button type="primary">上传文件</Button>
                  </Upload>,
                ] : []}
                columns={[
                  {
                    title: 'Key',
                    dataIndex: 'key',
                    render: (v) => {
                      const key = String(v || '');
                      const appIdFromKey = parseAppIdFromObjectKey(key);
                      if (!appIdFromKey) return key;
                      const relative = key.replace(`apps/${appIdFromKey}/`, '');
                      return (
                        <>
                          {relative}
                          {' '}
                          <a onClick={() => history.push(`/files?appId=${appIdFromKey}`)}>应用 #{appIdFromKey}</a>
                        </>
                      );
                    },
                  },
                  { title: '大小', dataIndex: 'size' },
                  { title: '修改时间', dataIndex: 'lastModified', valueType: 'dateTime' },
                  {
                    title: '操作',
                    valueType: 'option',
                    render: (_, record) => [
                      <a key="down" onClick={async () => { const { url } = await presignFile(record.key, 'get'); window.open(url); }}>下载</a>,
                      access.canWriteFiles && (
                        <a key="del" onClick={async () => { await deleteFile(record.key); actionRef.current?.reload(); }}>删除</a>
                      ),
                    ].filter(Boolean),
                  },
                ]}
              />
            </>
          ) : (
            <Card
              title={`应用文件 - ${scope.appName}`}
              extra={(
                <Space>
                  <Button type="link" onClick={() => history.push(`/app/detail/${scope.appId}?tab=files`)}>
                    在应用详情打开
                  </Button>
                </Space>
              )}
            >
              {selectedApp ? (
                <Tabs
                  activeKey={activeSubTab}
                  onChange={onSubTabChange}
                  items={[
                    {
                      key: 'volume',
                      label: '数据卷',
                      children: (
                        <VolumeFilesPanel
                          appId={scope.appId}
                          volumes={selectedApp.volumes || []}
                          fileProfiles={selectedApp.managementSchema?.fileProfiles || []}
                          canWrite={access.canWriteApp}
                        />
                      ),
                    },
                    {
                      key: 'object',
                      label: '对象存储',
                      children: <AppObjectStoragePanel appId={scope.appId} canWrite={access.canWriteFiles} />,
                    },
                    {
                      key: 'skills',
                      label: '技能附件',
                      children: (
                        <AppSkillFilesPanel
                          appId={scope.appId}
                          topologyId={skillsContext?.topologyId}
                        />
                      ),
                    },
                  ]}
                />
              ) : '加载中...'}
            </Card>
          )}
        </Col>
      </Row>
    </PageContainer>
  );
};

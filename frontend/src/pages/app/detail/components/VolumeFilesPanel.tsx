import { deleteAppFile, getAppFileDownloadUrl, listAppFiles, uploadAppFile } from '@/services/app';
import { mkdirAppFile, readAppFileText, renameAppFile, writeAppFileText } from '@/services/appVolumeFiles';
import { DeleteOutlined, DownloadOutlined, EditOutlined, FolderAddOutlined, FolderOpenOutlined, UploadOutlined } from '@ant-design/icons';
import { Breadcrumb, Button, Input, Modal, Select, Space, Table, Upload, message } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { useEffect, useState } from 'react';

export type FileEntry = { name: string; directory: boolean; size: number; modifiedAt: number };

function joinPath(base: string, name: string) {
  if (!base) return name;
  return base.endsWith('/') ? `${base}${name}` : `${base}/${name}`;
}

type Props = {
  appId: number;
  volumes: any[];
  fileProfiles?: any[];
  canWrite?: boolean;
};

export default function VolumeFilesPanel({ appId, volumes, fileProfiles = [], canWrite = false }: Props) {
  const [volume, setVolume] = useState<string>(volumes[0]?.name || 'data');
  const [currentPath, setCurrentPath] = useState('');
  const [files, setFiles] = useState<FileEntry[]>([]);
  const [loading, setLoading] = useState(false);
  const [editModal, setEditModal] = useState<{ path: string; content: string } | null>(null);

  const profile = fileProfiles.find((p) => p.volume === volume);
  const editableExtensions: string[] = profile?.editableExtensions || ['.json', '.yaml', '.yml', '.md', '.txt'];
  const criticalPaths: string[] = profile?.criticalPaths || [];

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

  const isEditable = (name: string) => editableExtensions.some((ext) => name.endsWith(ext));
  const isCritical = (name: string) => {
    const full = joinPath(currentPath, name);
    return criticalPaths.some((p) => full === p || full.endsWith(`/${p}`) || name === p);
  };

  const columns: ColumnsType<FileEntry> = [
    {
      title: '名称',
      dataIndex: 'name',
      render: (name, record) => (
        record.directory ? (
          <a onClick={() => setCurrentPath(joinPath(currentPath, name))}>
            <FolderOpenOutlined /> {name}
          </a>
        ) : (
          <span style={isCritical(name) ? { color: '#1677ff', fontWeight: 600 } : undefined}>{name}</span>
        )
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
      width: 240,
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
          !record.directory && isEditable(record.name) && canWrite && (
            <a
              key="edit"
              style={{ marginLeft: 8 }}
              onClick={async () => {
                try {
                  const content = await readAppFileText(appId, volume, filePath);
                  setEditModal({ path: filePath, content });
                } catch {
                  message.error('读取文件失败');
                }
              }}
            >
              <EditOutlined /> 编辑
            </a>
          ),
          canWrite && (
            <a
              key="rename"
              style={{ marginLeft: 8 }}
              onClick={() => {
                let newName = record.name;
                Modal.confirm({
                  title: '重命名',
                  content: <Input defaultValue={record.name} onChange={(e) => { newName = e.target.value; }} />,
                  onOk: async () => {
                    await renameAppFile(appId, volume, filePath, newName);
                    message.success('已重命名');
                    loadFiles();
                  },
                });
              }}
            >
              重命名
            </a>
          ),
          canWrite && (
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
            </a>
          ),
        ].filter(Boolean);
      },
    },
  ];

  if (!volumes.length) {
    return <div>该应用未配置数据卷</div>;
  }

  return (
    <>
      <div style={{ marginBottom: 16, display: 'flex', gap: 12, alignItems: 'center', flexWrap: 'wrap' }}>
        <span>数据卷：</span>
        <Select
          style={{ width: 220 }}
          value={volume}
          onChange={(v) => { setVolume(v); setCurrentPath(''); }}
          options={volumes.map((v) => ({
            label: fileProfiles.find((p) => p.volume === v.name)?.label || v.description || v.name,
            value: v.name,
          }))}
        />
        {canWrite && (
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
        )}
        {canWrite && (
          <Button
            icon={<FolderAddOutlined />}
            onClick={() => {
              let dirName = '';
              Modal.confirm({
                title: '新建目录',
                content: <Input placeholder="目录名" onChange={(e) => { dirName = e.target.value; }} />,
                onOk: async () => {
                  const path = joinPath(currentPath, dirName);
                  await mkdirAppFile(appId, volume, path);
                  message.success('目录已创建');
                  loadFiles();
                },
              });
            }}
          >
            新建目录
          </Button>
        )}
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
      <Modal
        title={`编辑 ${editModal?.path}`}
        open={!!editModal}
        width={800}
        onCancel={() => setEditModal(null)}
        onOk={async () => {
          if (!editModal) return;
          await writeAppFileText(appId, volume, editModal.path, editModal.content);
          message.success('已保存');
          setEditModal(null);
          loadFiles();
        }}
      >
        <Input.TextArea
          rows={16}
          value={editModal?.content}
          onChange={(e) => setEditModal((prev) => (prev ? { ...prev, content: e.target.value } : prev))}
          style={{ fontFamily: 'monospace' }}
        />
      </Modal>
    </>
  );
}

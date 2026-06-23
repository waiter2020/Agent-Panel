import { ProTable } from '@ant-design/pro-components';
import { Button, Upload, message } from 'antd';
import { useRef } from 'react';
import { deleteFile, listFiles, presignFile } from '@/services/files';
import { stripAppObjectKeyPrefix } from '@/utils/appStorage';

type Props = {
  appId: number;
  title?: string;
  canWrite?: boolean;
};

export default function AppObjectStoragePanel({ appId, title = '对象存储', canWrite = false }: Props) {
  const actionRef = useRef<any>();

  return (
    <ProTable
      headerTitle={title}
      actionRef={actionRef}
      rowKey="key"
      search={false}
      params={{ appId }}
      request={async () => {
        const data = await listFiles(undefined, appId);
        return { data, success: true };
      }}
      toolBarRender={() => canWrite ? [
        <Upload
          key="upload"
          showUploadList={false}
          customRequest={async ({ file, onSuccess, onError }) => {
            try {
              const fileName = (file as File).name;
              const { url } = await presignFile(fileName, 'put', appId);
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
          render: (v) => stripAppObjectKeyPrefix(String(v || ''), appId),
        },
        { title: '大小', dataIndex: 'size' },
        { title: '修改时间', dataIndex: 'lastModified', valueType: 'dateTime' },
        {
          title: '操作',
          valueType: 'option',
          render: (_, record) => [
            <a key="down" onClick={async () => {
              const { url } = await presignFile(record.key, 'get', appId);
              window.open(url);
            }}>下载</a>,
            canWrite && (
              <a key="del" onClick={async () => {
                await deleteFile(record.key, appId);
                message.success('已删除');
                actionRef.current?.reload();
              }}>删除</a>
            ),
          ].filter(Boolean),
        },
      ]}
    />
  );
}

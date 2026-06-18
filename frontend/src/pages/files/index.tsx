import { PageContainer, ProTable } from '@ant-design/pro-components';
import { Button, Input, message } from 'antd';
import { deleteFile, listFiles, presignFile } from '@/services/files';
import { useRef, useState } from 'react';

export default () => {
  const actionRef = useRef<any>();
  const [prefix, setPrefix] = useState('');

  return (
    <PageContainer title="文件管理" subTitle="对象存储 (MinIO/S3)">
      <Input.Search placeholder="前缀过滤" style={{ width: 300, marginBottom: 16 }} onSearch={setPrefix} enterButton="搜索" />
      <ProTable
        actionRef={actionRef}
        rowKey="key"
        search={false}
        params={{ prefix }}
        request={async () => {
          const data = await listFiles(prefix);
          return { data, success: true };
        }}
        toolBarRender={() => [
          <Button key="upload" type="primary" onClick={async () => {
            const key = prompt('请输入对象 Key');
            if (!key) return;
            const { url } = await presignFile(key, 'put');
            message.info(`请使用预签名 URL 上传: ${url}`);
          }}>获取上传链接</Button>,
        ]}
        columns={[
          { title: 'Key', dataIndex: 'key' },
          { title: '大小', dataIndex: 'size' },
          { title: '修改时间', dataIndex: 'lastModified', valueType: 'dateTime' },
          {
            title: '操作',
            valueType: 'option',
            render: (_, record) => [
              <a key="down" onClick={async () => { const { url } = await presignFile(record.key, 'get'); window.open(url); }}>下载</a>,
              <a key="del" onClick={async () => { await deleteFile(record.key); actionRef.current?.reload(); }}>删除</a>,
            ],
          },
        ]}
      />
    </PageContainer>
  );
};

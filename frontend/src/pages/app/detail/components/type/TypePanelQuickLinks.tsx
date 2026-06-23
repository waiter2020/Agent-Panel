import { Button, Space, Typography } from 'antd';
import { history } from '@umijs/max';

export default function TypePanelQuickLinks({ appId }: { appId?: number }) {
  if (!appId) return null;
  return (
    <Space wrap style={{ marginTop: 16 }}>
      <Typography.Text strong>快捷入口</Typography.Text>
      <Button type="link" size="small" onClick={() => history.push(`/app/detail/${appId}?tab=skills`)}>Skills</Button>
      <Button type="link" size="small" onClick={() => history.push(`/app/detail/${appId}?tab=memory`)}>记忆</Button>
      <Button type="link" size="small" onClick={() => history.push(`/app/detail/${appId}?tab=files`)}>数据卷</Button>
      <Button type="link" size="small" onClick={() => history.push(`/files?appId=${appId}`)}>文件中心</Button>
    </Space>
  );
}

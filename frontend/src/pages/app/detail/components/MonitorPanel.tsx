import { Alert, Card } from 'antd';
import { Line } from '@ant-design/charts';

type StatPoint = {
  time: string;
  mem: number;
  cpu: number;
};

type Props = {
  stats: StatPoint[];
  monitorStatus?: string;
  monitorMessage?: string;
};

export default function MonitorPanel({ stats, monitorStatus, monitorMessage }: Props) {
  const chartData = stats.flatMap((s) => [
    { time: s.time, value: s.cpu, type: 'CPU %' },
    { time: s.time, value: s.mem / 1024 / 1024, type: '内存 MB' },
  ]);

  return (
    <Card>
      {monitorStatus === 'error' && (
        <Alert
          type="error"
          showIcon
          style={{ marginBottom: 16 }}
          message="监控连接失败"
          description={monitorMessage || '无法连接监控流，请检查应用是否已部署且正在运行'}
        />
      )}
      {monitorStatus === 'unavailable' && (
        <Alert
          type="warning"
          showIcon
          style={{ marginBottom: 16 }}
          message="监控数据不可用"
          description={monitorMessage || '运行时未返回有效指标'}
        />
      )}
      {stats.length === 0 && monitorStatus === 'connecting' && (
        <Alert type="info" showIcon style={{ marginBottom: 16 }} message="正在连接监控流..." />
      )}
      <Line
        data={chartData}
        xField="time"
        yField="value"
        seriesField="type"
        height={300}
      />
    </Card>
  );
}

import { Alert, Card, Collapse } from 'antd';
import { Line } from '@ant-design/charts';

type StatPoint = {
  time: string;
  mem: number;
  cpu: number;
  netRx?: number;
  netTx?: number;
};

type Props = {
  stats: StatPoint[];
  monitorStatus?: string;
  monitorMessage?: string;
  networkAvailable?: boolean;
};

const METRICS_SERVER_HELP = `安装 metrics-server（示例）：
kubectl apply -f https://github.com/kubernetes-sigs/metrics-server/releases/latest/download/components.yaml

验证：
kubectl top pods -n <apps-namespace>`;

export default function MonitorPanel({
  stats,
  monitorStatus,
  monitorMessage,
  networkAvailable = true,
}: Props) {
  const chartData = stats.flatMap((s) => {
    const points = [
      { time: s.time, value: s.cpu, type: 'CPU %' },
      { time: s.time, value: s.mem / 1024 / 1024, type: '内存 MB' },
    ];
    if (networkAvailable) {
      points.push(
        { time: s.time, value: (s.netRx || 0) / 1024, type: '网络下行 KB/s' },
        { time: s.time, value: (s.netTx || 0) / 1024, type: '网络上行 KB/s' },
      );
    }
    return points;
  });

  const isK8sMetricsHint = monitorMessage?.includes('metrics-server')
    || monitorMessage?.includes('metrics.k8s.io');

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
          description={(
            <div>
              <div>{monitorMessage || '运行时未返回有效指标'}</div>
              {isK8sMetricsHint && (
                <Collapse
                  ghost
                  size="small"
                  style={{ marginTop: 8 }}
                  items={[{
                    key: 'help',
                    label: '排查指引',
                    children: (
                      <pre style={{ margin: 0, whiteSpace: 'pre-wrap', fontSize: 12 }}>
                        {METRICS_SERVER_HELP}
                      </pre>
                    ),
                  }]}
                />
              )}
            </div>
          )}
        />
      )}
      {networkAvailable === false && monitorStatus === 'ok' && (
        <Alert
          type="info"
          showIcon
          style={{ marginBottom: 16 }}
          message="网络指标不可用"
          description="CPU/内存正常，但无法读取网络流量（Docker 网络或 kubelet nodes/proxy 权限）"
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

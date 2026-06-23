import AppWebConsolePanel from './AppWebConsolePanel';

type Props = {
  appId: number;
  consoleKey: string;
  title?: string;
  disabled?: boolean;
  accessUrls?: { name: string; url: string }[];
};

export default function OpenClawGatewayPanel(props: Props) {
  return (
    <AppWebConsolePanel
      {...props}
      trustedProxyHint
      failureHint="若 Gateway 仍提示输入令牌，请重新部署应用以应用 trusted-proxy 配置；部署后刷新本页即可自动连接。"
    />
  );
}

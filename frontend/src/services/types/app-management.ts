export type DashboardStatsDto = {
  totalApps: number;
  runningApps: number;
  stoppedApps: number;
  deployingApps: number;
  errorApps: number;
  byTemplate: Record<string, number>;
  byRuntime: Record<string, number>;
  exposedPorts: number;
  internalPorts: number;
  portConflicts: number;
  topologyCount: number;
  deployedTopologies: number;
  recentApps: RecentAppDto[];
  portUsage?: PortUsageDto[];
};

export type RecentAppDto = {
  id: number;
  name: string;
  status: string;
  templateCode: string;
};

export type PortUsageDto = {
  appId: number;
  appName: string;
  templateCode: string;
  portName: string;
  containerPort: number;
  hostPort?: number;
  expose: boolean;
  protocol?: string;
  conflict?: boolean;
};

export type AppHealthDto = {
  healthy: boolean;
  message?: string;
  statusCode?: number;
  checkedUrl?: string;
  webConsoles: AppHealthConsoleLink[];
};

export type AppHealthConsoleLink = {
  key: string;
  title: string;
  proxyPath: string;
};

export type TaskBoardDto = {
  id: number;
  name: string;
  scopeType: string;
  scopeRef?: string;
  columns?: TaskColumnDto[];
};

export type TaskColumnDto = {
  id: number;
  name: string;
  statusMapping?: string;
  orderNo: number;
  color?: string;
  tasks?: TaskItemDto[];
};

export type TaskItemDto = {
  id: number;
  columnId: number;
  applicationId?: number;
  title: string;
  description?: string;
  priority?: string;
  orderNo: number;
  appName?: string;
  appStatus?: string;
  templateCode?: string;
};

export type AppTabDef = {
  key: string;
  label?: string;
  permission?: string;
  consoleKey?: string;
  portRef?: string;
  panelKey?: string;
};

export type ManagementSchema = {
  tabs?: AppTabDef[];
  webConsoles?: Array<Record<string, unknown>>;
  fileProfiles?: Array<Record<string, unknown>>;
  healthCheck?: Record<string, unknown>;
};

export const KNOWN_TAB_KEYS = [
  'overview',
  'config',
  'env',
  'webConsole',
  'console',
  'files',
  'ports',
  'skills',
  'memory',
  'delegation',
  'mcp',
  'monitor',
  'logs',
  'typePanel',
] as const;

export type KnownTabKey = (typeof KNOWN_TAB_KEYS)[number];

export function isKnownTabKey(key: string): key is KnownTabKey {
  return (KNOWN_TAB_KEYS as readonly string[]).includes(key);
}

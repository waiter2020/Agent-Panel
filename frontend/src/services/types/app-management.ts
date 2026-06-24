export type DashboardStatsDto = {
  totalApps: number;
  createdApps?: number;
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

export type ApplicationDto = {
  id: number;
  name: string;
  templateId?: number;
  templateName?: string;
  templateCode?: string;
  managementSchema?: ManagementSchema;
  ownerId?: number;
  image?: string;
  tag?: string;
  status?: string;
  ports?: Array<Record<string, unknown>>;
  resources?: Record<string, unknown>;
  volumes?: Array<Record<string, unknown>>;
  replicas?: number;
  runtimeProvider?: string;
  remark?: string;
  runtimeRef?: string;
  runtimeNamespace?: string;
  env?: Array<{ key: string; value?: string; secret?: boolean }>;
  envSchema?: Array<Record<string, unknown>>;
  accessUrls?: Array<Record<string, unknown>>;
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

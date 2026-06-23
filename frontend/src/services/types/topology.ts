export type TopologyNodeDto = {
  id: number;
  applicationId: number;
  applicationName?: string;
  applicationStatus?: string;
  role: string;
  config?: Record<string, unknown>;
};

export type TopologyLinkDto = {
  id: number;
  topologyId: number;
  fromNodeId: number;
  toNodeId: number;
  fromApplicationName?: string;
  toApplicationName?: string;
  protocol?: string;
  config?: Record<string, unknown>;
  peerUrl?: string;
  createdAt?: string;
};

export type InjectedEnvDto = {
  applicationId: number;
  applicationName?: string;
  envKey: string;
  envValue: string;
  source?: string;
};

export type InjectedSkillDto = {
  id: number;
  name: string;
  filePath?: string;
};

export type MemberAccessUrlDto = {
  applicationId: number;
  applicationName?: string;
  role?: string;
  name: string;
  url: string;
  peerUrl?: string;
};

export type TopologyDto = {
  id: number;
  name: string;
  description?: string;
  networkName?: string;
  status: string;
  ownerId?: number;
  inferenceApiKeyId?: number;
  nodes?: TopologyNodeDto[];
  links?: TopologyLinkDto[];
  injectedEnv?: InjectedEnvDto[];
  injectedSkills?: InjectedSkillDto[];
  memberAccessUrls?: MemberAccessUrlDto[];
  needsKeyRedeploy?: boolean;
  createdAt?: string;
  updatedAt?: string;
  inferenceKeyRaw?: string;
};

export type CreateTopologyRequest = {
  name: string;
  description?: string;
  networkName?: string;
};

export type AddTopologyNodeRequest = {
  applicationId: number;
  role?: string;
  config?: Record<string, unknown>;
};

export type AddTopologyLinkRequest = {
  fromNodeId: number;
  toNodeId: number;
  protocol?: string;
  config?: Record<string, unknown>;
};

export type RegistrySourceId = 'ghcr' | 'dockerhub' | 'custom';

export type RegistrySource = {
  id: RegistrySourceId;
  name: string;
  host: string;
  description?: string;
};

const GHCR_HOST = 'ghcr.io';

export function stripImageTag(image: string): string {
  const ref = image.trim();
  const at = ref.indexOf('@');
  const withoutDigest = at > 0 ? ref.slice(0, at) : ref;
  const colon = withoutDigest.lastIndexOf(':');
  if (colon > 0) {
    const before = withoutDigest.slice(0, colon);
    if (before.includes('/') || !before.match(/:[0-9]+$/)) {
      return before;
    }
  }
  return withoutDigest;
}

export function extractRepositoryPath(image: string): string {
  const ref = stripImageTag(image);
  if (!ref) return ref;
  if (ref.toLowerCase().startsWith(`${GHCR_HOST}/`)) {
    return ref.slice(GHCR_HOST.length + 1);
  }
  if (ref.toLowerCase().startsWith('docker.io/')) {
    return ref.slice('docker.io/'.length);
  }
  if (ref.toLowerCase().startsWith('registry-1.docker.io/')) {
    return ref.slice('registry-1.docker.io/'.length);
  }
  const slash = ref.indexOf('/');
  if (slash > 0) {
    const first = ref.slice(0, slash);
    if (first.includes('.') || first.includes(':')) {
      return ref.slice(slash + 1);
    }
  }
  return ref;
}

export function detectRegistrySource(image: string): RegistrySourceId {
  const ref = stripImageTag(image);
  if (!ref) return 'ghcr';
  if (ref.toLowerCase().startsWith(`${GHCR_HOST}/`) || ref.toLowerCase() === GHCR_HOST) {
    return 'ghcr';
  }
  if (ref.toLowerCase().startsWith('docker.io/')
    || ref.toLowerCase().startsWith('registry-1.docker.io/')) {
    return 'dockerhub';
  }
  const slash = ref.indexOf('/');
  if (slash < 0) return 'dockerhub';
  const first = ref.slice(0, slash);
  if (!first.includes('.') && !first.includes(':')) {
    return 'dockerhub';
  }
  if (['docker.io', 'registry-1.docker.io', 'index.docker.io'].includes(first.toLowerCase())) {
    return 'dockerhub';
  }
  return 'custom';
}

export function applyRegistrySource(source: RegistrySourceId, repositoryPath: string): string {
  const repo = repositoryPath.trim();
  if (!repo) return repo;
  if (source === 'custom') return repo;
  if (source === 'dockerhub') {
    return repo.startsWith('library/') ? repo.slice('library/'.length) : repo;
  }
  if (repo.toLowerCase().startsWith(`${GHCR_HOST}/`)) {
    return repo;
  }
  return `${GHCR_HOST}/${repo}`;
}

export const DEFAULT_REGISTRY_SOURCES: RegistrySource[] = [
  { id: 'ghcr', name: 'GHCR', host: 'ghcr.io', description: 'GitHub Container Registry' },
  { id: 'dockerhub', name: 'Docker Hub', host: 'docker.io', description: 'Docker 官方镜像仓库' },
  { id: 'custom', name: '自定义', host: '', description: '手动输入完整镜像地址' },
];

export function parseAppIdFromObjectKey(key?: string): number | undefined {
  if (!key) return undefined;
  const match = key.match(/^apps\/(\d+)\//);
  return match ? Number(match[1]) : undefined;
}

export function stripAppObjectKeyPrefix(key: string, appId?: number): string {
  if (!appId) return key;
  const prefix = `apps/${appId}/`;
  return key.startsWith(prefix) ? key.slice(prefix.length) : key;
}

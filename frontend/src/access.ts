export default (initialState: { currentUser?: API.CurrentUser }) => {
  const permissions = Array.isArray(initialState?.currentUser?.permissions)
    ? initialState.currentUser.permissions
    : [];
  const has = (code: string) => permissions.includes(code);

  return {
    canAccessDashboard: has('app:read'),
    canAccessSystem: has('system:user:read') || has('system:role:read') || has('system:menu:read')
      || has('system:apikey:read') || has('system:setting:read') || has('system:tenant:read'),
    canManageUser: has('system:user:read'),
    canManageTenant: has('system:tenant:read'),
    canManageRole: has('system:role:read'),
    canManageMenu: has('system:menu:read'),
    canAccessApp: has('app:read'),
    canWriteApp: has('app:write'),
    canDeployApp: has('app:deploy'),
    canOperateApp: has('app:operate'),
    canUseAppTerminal: has('app:terminal'),
    canDeleteApp: has('app:delete'),
    canAccessFiles: has('file:read'),
    canWriteFiles: has('file:write'),
    canAccessAi: has('ai:read'),
    canWriteAi: has('ai:write'),
    canChatAi: has('ai:chat'),
    canAudit: has('audit:read'),
    canManageSettings: has('system:setting:read'),
    canWriteSettings: has('system:setting:write'),
    canManageApiKey: has('system:apikey:write'),
    canViewApiKey: has('system:apikey:read'),
    canViewTopology: has('topology:read'),
    canWriteTopology: has('topology:write'),
    canDeployTopology: has('topology:deploy'),
    canViewMemory: has('memory:read'),
    canWriteMemory: has('memory:write'),
    canViewSkill: has('skill:read'),
    canWriteSkill: has('skill:write'),
    canViewDelegation: has('delegation:read'),
    canWriteDelegation: has('delegation:write'),
  };
};

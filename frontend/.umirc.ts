import { defineConfig } from '@umijs/max';

export default defineConfig({
  esbuildMinifyIIFE: true,
  antd: {},
  access: {},
  model: {},
  initialState: {},
  request: {},
  layout: {
    title: 'Agent Panel',
    locale: false,
  },
  links: [{ rel: 'icon', href: '/favicon.svg', type: 'image/svg+xml' }],
  locale: {
    default: 'zh-CN',
    antd: true,
  },
  proxy: process.env.NODE_ENV === 'development' ? {
    '/api': {
      target: 'http://localhost:8080',
      changeOrigin: true,
    },
    '/v1': {
      target: 'http://localhost:8080',
      changeOrigin: true,
    },
  } : undefined,
  routes: [
    { path: '/login', layout: false, component: './login' },
    { path: '/', redirect: '/dashboard' },
    { path: '/dashboard', name: '仪表盘', icon: 'DashboardOutlined', component: './dashboard', access: 'canAccessDashboard' },
    {
      path: '/system',
      name: '系统管理',
      icon: 'SettingOutlined',
      access: 'canAccessSystem',
      routes: [
        { path: '/system/user', name: '用户管理', component: './system/user', access: 'canManageUser' },
        { path: '/system/tenant', name: '租户管理', component: './system/tenant', access: 'canManageTenant' },
        { path: '/system/role', name: '角色管理', component: './system/role', access: 'canManageRole' },
        { path: '/system/menu', name: '菜单管理', component: './system/menu', access: 'canManageMenu' },
        { path: '/system/audit', name: '审计日志', component: './system/audit', access: 'canAudit' },
        { path: '/system/apikey', name: 'API 密钥', component: './system/apikey', access: 'canViewApiKey' },
        { path: '/system/settings', name: '系统设置', component: './system/settings', access: 'canManageSettings' },
      ],
    },
    {
      path: '/app',
      name: '应用中心',
      icon: 'AppstoreOutlined',
      access: 'canAccessApp',
      routes: [
        { path: '/app/list', name: '应用列表', component: './app/list', access: 'canAccessApp' },
        { path: '/app/kanban', name: '运维看板', component: './app/kanban', access: 'canAccessApp' },
        { path: '/app/ports', name: '端口全景', component: './app/ports', access: 'canAccessApp' },
        { path: '/app/topology', name: '协同拓扑', component: './app/topology', access: 'canViewTopology' },
        { path: '/app/memory', name: '共享记忆', component: './app/memory', access: 'canViewMemory' },
        { path: '/app/detail/:id', name: '应用详情', hideInMenu: true, component: './app/detail', access: 'canAccessApp' },
      ],
    },
    { path: '/files', name: '文件管理', icon: 'FolderOutlined', component: './files', access: 'canAccessFiles' },
    {
      path: '/ai',
      name: '模型网关',
      icon: 'RobotOutlined',
      access: 'canAccessAi',
      routes: [
        { path: '/ai/models', name: '模型配置', component: './ai/models', access: 'canAccessAi' },
        { path: '/ai/playground', name: '对话调试', component: './ai/playground', access: 'canChatAi' },
      ],
    },
  ],
  npmClient: 'pnpm',
});

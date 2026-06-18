import { LockOutlined, UserOutlined } from '@ant-design/icons';
import { LoginForm, ProFormCheckbox, ProFormText } from '@ant-design/pro-components';
import { Spin, message } from 'antd';
import { useEffect, useRef, useState } from 'react';
import { login, fetchUserInfo } from '@/services/auth';
import {
  clearSavedCredentials,
  getSavedCredentials,
  saveCredentials,
} from '@/utils/auth-storage';
import './index.less';

export default () => {
  const [autoLogging, setAutoLogging] = useState(false);
  const autoLoginAttempted = useRef(false);

  const doLogin = async (values: {
    username: string;
    password: string;
    rememberMe?: boolean;
  }) => {
    const data = await login({
      username: values.username,
      password: values.password,
    });
    if (!data?.accessToken || !data?.refreshToken) {
      message.error('登录响应异常，请重试');
      return false;
    }

    localStorage.setItem('accessToken', data.accessToken);
    localStorage.setItem('refreshToken', data.refreshToken);

    if (values.rememberMe) {
      saveCredentials(values.username, values.password);
    } else {
      clearSavedCredentials();
    }

    const redirect = new URL(window.location.href).searchParams.get('redirect');
    const target = redirect && redirect !== '/login' ? redirect : '/dashboard';
    window.location.assign(target);
    return true;
  };

  const handleSubmit = async (values: {
    username: string;
    password: string;
    rememberMe?: boolean;
  }) => {
    try {
      return await doLogin(values);
    } catch (e: unknown) {
      const err = e as { data?: { message?: string }; message?: string };
      const msg = err?.data?.message || err?.message;
      message.error(msg && msg !== 'success' ? msg : '登录失败');
      return false;
    }
  };

  useEffect(() => {
    if (autoLoginAttempted.current) {
      return;
    }
    autoLoginAttempted.current = true;

    const tryAutoLogin = async () => {
      const token = localStorage.getItem('accessToken');
      if (token) {
        try {
          const user = await fetchUserInfo();
          if (user) {
            window.location.assign('/dashboard');
            return;
          }
        } catch {
          localStorage.removeItem('accessToken');
          localStorage.removeItem('refreshToken');
        }
      }

      const saved = getSavedCredentials();
      if (!saved) {
        return;
      }

      setAutoLogging(true);
      try {
        await doLogin({
          username: saved.username,
          password: saved.password,
          rememberMe: true,
        });
      } catch {
        clearSavedCredentials();
        message.error('自动登录失败，请重新输入密码');
      } finally {
        setAutoLogging(false);
      }
    };

    tryAutoLogin();
  }, []);

  const saved = getSavedCredentials();

  if (autoLogging) {
    return (
      <div className="login-page">
        <div className="login-container">
          <div className="login-card">
            <div className="auto-login-tip">
              <Spin size="large" />
              <p style={{ marginTop: 16 }}>正在自动登录...</p>
            </div>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="login-page">
      <div className="login-container">
        <div className="login-card">
          <div className="login-header">
            <img src="/logo.svg" alt="Agent Panel" className="login-logo" />
            <h1>Agent Panel</h1>
            <p>AI Agent 托管面板</p>
          </div>
          <LoginForm
            className="login-form"
            onFinish={handleSubmit}
            initialValues={{
              username: saved?.username || '',
              password: saved?.password || '',
              rememberMe: !!saved,
            }}
            submitter={{ searchConfig: { submitText: '登录' } }}
          >
            <ProFormText
              name="username"
              fieldProps={{ size: 'large', prefix: <UserOutlined /> }}
              placeholder="用户名"
              rules={[{ required: true, message: '请输入用户名' }]}
            />
            <ProFormText.Password
              name="password"
              fieldProps={{ size: 'large', prefix: <LockOutlined /> }}
              placeholder="密码"
              rules={[{ required: true, message: '请输入密码' }]}
            />
            <ProFormCheckbox name="rememberMe">记住用户名和密码</ProFormCheckbox>
          </LoginForm>
        </div>
        <div className="login-footer">统一管理 AI Agent 部署、监控与模型网关</div>
      </div>
    </div>
  );
};

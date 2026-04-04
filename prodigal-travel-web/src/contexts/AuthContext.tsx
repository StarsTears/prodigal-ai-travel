import { message } from 'antd';
import React, {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
} from 'react';
import {
  deregister as apiDeregister,
  login as apiLogin,
  loginByCode as apiLoginByCode,
  logout as apiLogout,
  register as apiRegister,
  sendEmailCode as apiSendEmailCode,
} from '@/api/auth';
import {
  AUTH_EXPIRED_EVENT,
  clearAuthSession,
  getStoredToken,
  getStoredUser,
  setAuthSession,
} from '@/utils/authStorage';
import { assertBaseResultOk, unwrapBaseResult } from '@/utils/apiResult';

export interface AuthContextValue {
  token: string | null;
  user: API.LoginResponse | null;
  ready: boolean;
  loginWithPassword: (account: string, password: string) => Promise<void>;
  loginWithEmailCode: (email: string, code: string) => Promise<void>;
  registerAccount: (params: {
    email: string;
    username: string;
    password: string;
    nickname?: string;
  }) => Promise<void>;
  sendLoginCode: (email: string) => Promise<void>;
  /** 通知服务端吊销当前 JWT 后清除本地态 */
  logout: () => Promise<void>;
  /** 注销账号（逻辑删除），成功后清除本地态 */
  deregisterAccount: () => Promise<void>;
}

const AuthContext = createContext<AuthContextValue | null>(null);

export const AuthProvider: React.FC<{ children: React.ReactNode }> = ({
  children,
}) => {
  const [token, setToken] = useState<string | null>(null);
  const [user, setUser] = useState<API.LoginResponse | null>(null);
  const [ready, setReady] = useState(false);

  const hydrate = useCallback(() => {
    const t = getStoredToken();
    const u = getStoredUser();
    if (t && u && u.token === t) {
      setToken(t);
      setUser(u);
    } else {
      if (t || u) {
        clearAuthSession();
      }
      setToken(null);
      setUser(null);
    }
    setReady(true);
  }, []);

  useEffect(() => {
    hydrate();
  }, [hydrate]);

  useEffect(() => {
    const onExpired = () => {
      message.warning('登录已过期，请重新登录');
      setToken(null);
      setUser(null);
    };
    window.addEventListener(AUTH_EXPIRED_EVENT, onExpired);
    return () => window.removeEventListener(AUTH_EXPIRED_EVENT, onExpired);
  }, []);

  const loginWithPassword = useCallback(async (account: string, password: string) => {
    const res = await apiLogin({ account: account.trim(), password });
    const data = unwrapBaseResult<API.LoginResponse>(res.data);
    if (!data.token) {
      throw new Error('登录未返回 token');
    }
    setAuthSession(data.token, data);
    setToken(data.token);
    setUser(data);
  }, []);

  const loginWithEmailCode = useCallback(async (email: string, code: string) => {
    const res = await apiLoginByCode({
      email: email.trim(),
      code: code.trim(),
    });
    const data = unwrapBaseResult<API.LoginResponse>(res.data);
    if (!data.token) {
      throw new Error('登录未返回 token');
    }
    setAuthSession(data.token, data);
    setToken(data.token);
    setUser(data);
  }, []);

  const registerAccount = useCallback(
    async (params: {
      email: string;
      username: string;
      password: string;
      nickname?: string;
    }) => {
      const res = await apiRegister({
        email: params.email.trim(),
        username: params.username.trim(),
        password: params.password,
        nickname: params.nickname?.trim() || params.username.trim(),
      });
      unwrapBaseResult<string>(res.data);
    },
    []
  );

  const sendLoginCode = useCallback(async (email: string) => {
    const res = await apiSendEmailCode({ email: email.trim() });
    unwrapBaseResult<string>(res.data);
  }, []);

  const logout = useCallback(async () => {
    try {
      if (getStoredToken()) {
        const res = await apiLogout();
        assertBaseResultOk(res.data);
      }
    } catch {
      /* 网络失败仍清理本地，避免无法退出 */
    } finally {
      clearAuthSession();
      setToken(null);
      setUser(null);
    }
  }, []);

  const deregisterAccount = useCallback(async () => {
    const res = await apiDeregister();
    assertBaseResultOk(res.data);
    clearAuthSession();
    setToken(null);
    setUser(null);
    message.success('账号已注销');
  }, []);

  const value = useMemo(
    () => ({
      token,
      user,
      ready,
      loginWithPassword,
      loginWithEmailCode,
      registerAccount,
      sendLoginCode,
      logout,
      deregisterAccount,
    }),
    [
      token,
      user,
      ready,
      loginWithPassword,
      loginWithEmailCode,
      registerAccount,
      sendLoginCode,
      logout,
      deregisterAccount,
    ]
  );

  return (
    <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
  );
};

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) {
    throw new Error('useAuth must be used within AuthProvider');
  }
  return ctx;
}

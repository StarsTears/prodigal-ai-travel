import { LockOutlined, MailOutlined, UserOutlined } from '@ant-design/icons';
import {
  Button,
  Card,
  Flex,
  Form,
  Input,
  message,
  Segmented,
  Tabs,
  Typography,
  theme,
} from 'antd';
import { isAxiosError } from 'axios';
import React, { useCallback, useEffect, useState } from 'react';
import { useAuth } from '@/contexts/AuthContext';

const { Text, Title } = Typography;

const SEND_INTERVAL_SEC = 60;

function formatRequestError(e: unknown): string {
  if (e instanceof Error && e.message.trim()) {
    return e.message;
  }
  if (isAxiosError(e)) {
    const d = e.response?.data;
    if (d && typeof d === 'object' && 'msg' in d) {
      const m = (d as { msg?: unknown }).msg;
      if (typeof m === 'string' && m.trim()) return m.trim();
    }
    return e.message || '网络异常';
  }
  return '请求失败';
}

export interface AuthCardProps {
  /** 登录成功后回调（例如关闭弹窗） */
  onLoginSuccess?: () => void;
  /** 标题下的说明 */
  subtitle?: string;
  /** 嵌入弹窗时使用更紧凑的卡片样式 */
  embedded?: boolean;
}

/** 密码登录：useForm 仅在本组件内与 Form 同时挂载，避免 Ant Design「未连接 Form」警告 */
const LoginPasswordForm: React.FC<{
  onLoginSuccess?: () => void;
  prefillAccount?: string | null;
}> = ({ onLoginSuccess, prefillAccount }) => {
  const { loginWithPassword } = useAuth();
  const [form] = Form.useForm<{ account: string; password: string }>();
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    if (prefillAccount?.trim()) {
      form.setFieldsValue({ account: prefillAccount.trim() });
    }
  }, [prefillAccount, form]);

  const onFinish = async (v: { account: string; password: string }) => {
    setLoading(true);
    try {
      await loginWithPassword(v.account, v.password);
      onLoginSuccess?.();
    } catch (e) {
      form.setFields([{ name: 'password', errors: [formatRequestError(e)] }]);
    } finally {
      setLoading(false);
    }
  };

  return (
    <Form
      form={form}
      layout="vertical"
      requiredMark={false}
      onFinish={onFinish}
      size="large"
    >
      <Form.Item
        name="account"
        rules={[{ required: true, message: '请输入用户名或邮箱' }]}
      >
        <Input
          prefix={<UserOutlined />}
          placeholder="用户名或邮箱"
          autoComplete="username"
        />
      </Form.Item>
      <Form.Item
        name="password"
        rules={[{ required: true, message: '请输入密码' }]}
      >
        <Input.Password
          prefix={<LockOutlined />}
          placeholder="密码"
          autoComplete="current-password"
        />
      </Form.Item>
      <Button type="primary" htmlType="submit" block size="large" loading={loading}>
        登录
      </Button>
    </Form>
  );
};

/** 验证码登录：表单实例与发送逻辑同层，保证 validateFields 始终作用于已挂载的 Form */
const LoginCodeForm: React.FC<{
  onLoginSuccess?: () => void;
  prefillEmail?: string | null;
}> = ({ onLoginSuccess, prefillEmail }) => {
  const { sendLoginCode, loginWithEmailCode } = useAuth();
  const [form] = Form.useForm<{ email: string; code: string }>();
  const [loading, setLoading] = useState(false);
  const [sendCodeLoading, setSendCodeLoading] = useState(false);
  const [sendLeft, setSendLeft] = useState(0);

  useEffect(() => {
    if (prefillEmail?.trim()) {
      form.setFieldsValue({ email: prefillEmail.trim() });
    }
  }, [prefillEmail, form]);

  useEffect(() => {
    if (sendLeft <= 0) return;
    const t = window.setInterval(() => {
      setSendLeft((s) => (s <= 1 ? 0 : s - 1));
    }, 1000);
    return () => window.clearInterval(t);
  }, [sendLeft]);

  const onSendCode = useCallback(async () => {
    if (sendLeft > 0 || sendCodeLoading) return;
    let email: string;
    try {
      const v = await form.validateFields(['email']);
      email = String(v.email ?? '').trim();
    } catch {
      return;
    }
    if (!email) return;

    setSendCodeLoading(true);
    try {
      await sendLoginCode(email);
      message.success('验证码已发送');
      setSendLeft(SEND_INTERVAL_SEC);
    } catch (e) {
      form.setFields([{ name: 'email', errors: [formatRequestError(e)] }]);
    } finally {
      setSendCodeLoading(false);
    }
  }, [form, sendCodeLoading, sendLeft, sendLoginCode]);

  const onFinish = async (v: { email: string; code: string }) => {
    setLoading(true);
    try {
      await loginWithEmailCode(v.email, v.code);
      onLoginSuccess?.();
    } catch (e) {
      form.setFields([{ name: 'code', errors: [formatRequestError(e)] }]);
    } finally {
      setLoading(false);
    }
  };

  return (
    <Form
      form={form}
      layout="vertical"
      requiredMark={false}
      onFinish={onFinish}
      size="large"
    >
      <Form.Item
        name="email"
        rules={[
          { required: true, message: '请输入邮箱' },
          { type: 'email', message: '邮箱格式不正确' },
        ]}
      >
        <Input
          prefix={<MailOutlined />}
          placeholder="已注册邮箱"
          autoComplete="email"
        />
      </Form.Item>
      <Form.Item label={null} style={{ marginBottom: 8 }}>
        <Flex gap={8}>
          <Form.Item
            name="code"
            noStyle
            rules={[{ required: true, message: '请输入验证码' }]}
          >
            <Input
              placeholder="6 位验证码"
              maxLength={6}
              style={{ flex: 1 }}
            />
          </Form.Item>
          <Button
            type="default"
            htmlType="button"
            onClick={() => void onSendCode()}
            disabled={sendLeft > 0 || sendCodeLoading || loading}
            loading={sendCodeLoading}
            style={{ flexShrink: 0 }}
          >
            {sendLeft > 0 ? `${sendLeft}s 后可重发` : '发送验证码'}
          </Button>
        </Flex>
      </Form.Item>
      <Text
        type="secondary"
        style={{ fontSize: 12, display: 'block', marginBottom: 12 }}
      >
        验证码仅对已注册邮箱发送；新用户请先完成注册。
      </Text>
      <Button type="primary" htmlType="submit" block size="large" loading={loading}>
        登录
      </Button>
    </Form>
  );
};

const RegisterForm: React.FC<{
  onRegistered: (email: string) => void;
}> = ({ onRegistered }) => {
  const { registerAccount } = useAuth();
  const [form] = Form.useForm<{
    email: string;
    username: string;
    password: string;
    password2: string;
  }>();
  const [loading, setLoading] = useState(false);

  const onFinish = async (v: {
    email: string;
    username: string;
    password: string;
    password2: string;
  }) => {
    if (v.password !== v.password2) {
      form.setFields([{ name: 'password2', errors: ['两次密码不一致'] }]);
      return;
    }
    setLoading(true);
    try {
      await registerAccount({
        email: v.email,
        username: v.username,
        password: v.password,
      });
      message.success('注册成功，请登录');
      form.resetFields();
      onRegistered(v.email.trim());
    } catch (e) {
      form.setFields([{ name: 'email', errors: [formatRequestError(e)] }]);
    } finally {
      setLoading(false);
    }
  };

  return (
    <Form
      form={form}
      layout="vertical"
      requiredMark={false}
      onFinish={onFinish}
      size="large"
    >
      <Form.Item
        name="email"
        rules={[
          { required: true, message: '请输入邮箱' },
          { type: 'email', message: '邮箱格式不正确' },
        ]}
      >
        <Input
          prefix={<MailOutlined />}
          placeholder="邮箱"
          autoComplete="email"
        />
      </Form.Item>
      <Form.Item
        name="username"
        rules={[
          { required: true, message: '请输入用户名' },
          {
            pattern: /^[a-zA-Z0-9_]{3,64}$/,
            message: '3–64 位字母、数字或下划线',
          },
        ]}
      >
        <Input
          prefix={<UserOutlined />}
          placeholder="用户名"
          autoComplete="username"
        />
      </Form.Item>
      <Form.Item
        name="password"
        rules={[
          { required: true, message: '请输入密码' },
          { min: 6, message: '至少 6 位' },
        ]}
      >
        <Input.Password
          prefix={<LockOutlined />}
          placeholder="密码（至少 6 位）"
          autoComplete="new-password"
        />
      </Form.Item>
      <Form.Item
        name="password2"
        rules={[{ required: true, message: '请再次输入密码' }]}
      >
        <Input.Password
          prefix={<LockOutlined />}
          placeholder="确认密码"
          autoComplete="new-password"
        />
      </Form.Item>
      <Button type="primary" htmlType="submit" block size="large" loading={loading}>
        注册
      </Button>
    </Form>
  );
};

/**
 * 登录 / 注册表单卡片：可单独用于弹窗或全屏页。
 */
export const AuthCard: React.FC<AuthCardProps> = ({
  onLoginSuccess,
  subtitle = '登录后开始对话',
  embedded = false,
}) => {
  const { token: themeToken } = theme.useToken();
  const [tab, setTab] = useState<'login' | 'register'>('login');
  const [loginMode, setLoginMode] = useState<'password' | 'code'>('password');
  const [postRegisterEmail, setPostRegisterEmail] = useState<string | null>(null);

  return (
    <Card
      variant="borderless"
      style={{
        width: '100%',
        maxWidth: 400,
        marginInline: 'auto',
        borderRadius: themeToken.borderRadiusLG * 2,
        boxShadow: embedded ? 'none' : '0 8px 32px rgba(0, 0, 0, 0.08)',
      }}
      styles={{
        body: {
          padding: embedded ? '8px 4px 12px' : '28px 28px 32px',
        },
      }}
    >
      <Flex vertical align="center" gap={4} style={{ marginBottom: embedded ? 16 : 22 }}>
        <img
          src="/logo.svg"
          alt=""
          width={embedded ? 36 : 40}
          height={embedded ? 36 : 40}
          style={{ display: 'block' }}
        />
        <Title level={embedded ? 5 : 4} style={{ margin: 0 }}>
          AI 旅游助手
        </Title>
        <Text type="secondary" style={{ fontSize: 13, textAlign: 'center' }}>
          {subtitle}
        </Text>
      </Flex>

      <Tabs
        activeKey={tab}
        onChange={(k) => setTab(k as 'login' | 'register')}
        centered
        items={[
          {
            key: 'login',
            label: '登录',
            children: (
              <Flex vertical gap={14}>
                <Segmented
                  block
                  value={loginMode}
                  onChange={(v) => setLoginMode(v as 'password' | 'code')}
                  options={[
                    { label: '密码登录', value: 'password' },
                    { label: '验证码登录', value: 'code' },
                  ]}
                />
                {loginMode === 'password' ? (
                  <LoginPasswordForm
                    onLoginSuccess={onLoginSuccess}
                    prefillAccount={postRegisterEmail}
                  />
                ) : (
                  <LoginCodeForm
                    onLoginSuccess={onLoginSuccess}
                    prefillEmail={postRegisterEmail}
                  />
                )}
              </Flex>
            ),
          },
          {
            key: 'register',
            label: '注册',
            children: (
              <RegisterForm
                onRegistered={(email) => {
                  setPostRegisterEmail(email);
                  setTab('login');
                }}
              />
            ),
          },
        ]}
      />
    </Card>
  );
};

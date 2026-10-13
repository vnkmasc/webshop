import { useState } from 'react';
import { Navigate, useNavigate } from 'react-router-dom';
import { Alert, Button, Card, Form, Input } from 'antd';
import { errorMessage } from '@/api/client';
import { useAuth } from '@/store/auth';
import { LogoMark } from '@/shop/components/Logo';

export default function AdminLoginPage() {
  const { user, login, logout } = useAuth();
  const navigate = useNavigate();
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  if (user?.role === 'ADMIN') return <Navigate to="/admin" replace />;

  const submit = async (v: { email: string; password: string }) => {
    setLoading(true);
    setError(null);
    try {
      const u = await login(v.email, v.password);
      if (u.role !== 'ADMIN') {
        logout();
        setError('Tài khoản này không có quyền quản trị');
        return;
      }
      navigate('/admin', { replace: true });
    } catch (err) {
      setError(errorMessage(err));
    } finally {
      setLoading(false);
    }
  };

  return (
    <div style={{ minHeight: '100vh', display: 'grid', placeItems: 'center', background: '#1f1d1a', padding: 16 }}>
      <Card style={{ width: '100%', maxWidth: 400 }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 20 }}>
          <LogoMark />
          <div><b style={{ fontSize: 18 }}>Sải Bước</b><div style={{ color: '#888' }}>Quản trị cửa hàng</div></div>
        </div>
        {error && <Alert type="error" showIcon message={error} style={{ marginBottom: 16 }} />}
        <Form layout="vertical" onFinish={submit} requiredMark={false}>
          <Form.Item name="email" label="Email" rules={[{ required: true, type: 'email' }]}><Input autoComplete="username" /></Form.Item>
          <Form.Item name="password" label="Mật khẩu" rules={[{ required: true }]}><Input.Password autoComplete="current-password" /></Form.Item>
          <Button type="primary" htmlType="submit" block size="large" loading={loading}>Đăng nhập</Button>
        </Form>
      </Card>
    </div>
  );
}

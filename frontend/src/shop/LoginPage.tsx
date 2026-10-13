import { useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { Alert, Button, Card, Form, Input } from 'antd';
import { errorMessage } from '@/api/client';
import { useAuth } from '@/store/auth';

export default function LoginPage() {
  const { login } = useAuth();
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const next = params.get('next');

  const submit = async (v: { email: string; password: string }) => {
    setLoading(true);
    setError(null);
    try {
      const user = await login(v.email, v.password);
      navigate(next && next.startsWith('/') ? next : user.role === 'ADMIN' ? '/admin' : '/', { replace: true });
    } catch (err) {
      setError(errorMessage(err));
    } finally {
      setLoading(false);
    }
  };

  return (
    <Card style={{ maxWidth: 420, margin: '48px auto' }} title="Đăng nhập">
      {error && <Alert type="error" message={error} showIcon style={{ marginBottom: 16 }} />}
      <Form layout="vertical" onFinish={submit} requiredMark={false}>
        <Form.Item name="email" label="Email" rules={[{ required: true, type: 'email', message: 'Nhập email hợp lệ' }]}>
          <Input autoComplete="username" />
        </Form.Item>
        <Form.Item name="password" label="Mật khẩu" rules={[{ required: true, message: 'Nhập mật khẩu' }]}>
          <Input.Password autoComplete="current-password" />
        </Form.Item>
        <Button type="primary" htmlType="submit" block size="large" loading={loading}>Đăng nhập</Button>
      </Form>
      <div style={{ marginTop: 16, textAlign: 'center' }}>
        Chưa có tài khoản? <Link to={`/dang-ky${next ? `?next=${encodeURIComponent(next)}` : ''}`}>Đăng ký</Link>
      </div>
    </Card>
  );
}

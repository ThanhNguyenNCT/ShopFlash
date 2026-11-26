import { useState } from 'react';
import { Alert, Button, Card, Descriptions, Form, Input, Space, Tag, Typography } from 'antd';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { tokens } from '@/shared/design-system';
import useAuth from './useAuth';
import safeNext from './safeNext';

export default function LoginPage() {
  const { user, isAdmin, login, logout } = useAuth();
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const next = safeNext(params.get('next'));
  const needsAdmin = !!next && (next === '/admin' || next.startsWith('/admin/'));

  const submit = async (values) => {
    setBusy(true);
    setError('');
    try {
      const account = await login(values);
      // ADMIN luôn vào khu quản trị (giữ next nếu next là trang quản trị); khách thì về trang đang xem.
      if (account.role === 'ADMIN') navigate(needsAdmin ? next : '/admin', { replace: true });
      else if (next && !needsAdmin) navigate(next, { replace: true });
      else navigate('/', { replace: true });
    } catch (problem) {
      setError(problem.message ?? 'Không đăng nhập được. Vui lòng thử lại.');
    } finally {
      setBusy(false);
    }
  };

  return (
    <div style={{ maxWidth: tokens.space[8] * 16, margin: '0 auto', minWidth: 0, overflowWrap: 'anywhere' }}>
      <Card>
        <Space orientation="vertical" size={tokens.space[4]} style={{ width: '100%' }}>
          <div>
            <Typography.Title level={3} style={{ margin: 0 }}>Đăng nhập</Typography.Title>
            <Typography.Text type="secondary">Dùng tài khoản ShopFlash của bạn.</Typography.Text>
          </div>
          {needsAdmin && <Alert type="info" showIcon title="Trang quản trị cần tài khoản Admin." />}
          {user && <Descriptions size="small" bordered column={1} items={[
            { key: 'name', label: 'Đang đăng nhập', children: user.fullName },
            { key: 'email', label: 'Email', children: <span className="sf-code">{user.email}</span> },
            { key: 'role', label: 'Vai trò', children: <Tag color={isAdmin ? 'processing' : 'default'}>{isAdmin ? 'Admin' : 'Khách'}</Tag> },
          ]} />}
          {error && <Alert type="error" showIcon title={error} />}
          <Form layout="vertical" onFinish={submit} requiredMark={false} style={{ width: '100%' }}>
            <Form.Item name="email" label="Email" rules={[{ required: true, message: 'Nhập email.' }, { type: 'email', message: 'Email không hợp lệ.' }]}>
              <Input type="email" autoComplete="username" maxLength={255} />
            </Form.Item>
            <Form.Item name="password" label="Mật khẩu" rules={[{ required: true, message: 'Nhập mật khẩu.' }]}>
              <Input.Password autoComplete="current-password" />
            </Form.Item>
            <Button type="primary" htmlType="submit" loading={busy} block>Đăng nhập</Button>
          </Form>
          {user && <Button onClick={logout} block>Đăng xuất</Button>}
          <Typography.Text>Chưa có tài khoản? <Link to={next ? `/register?next=${encodeURIComponent(next)}` : '/register'}>Đăng ký</Link></Typography.Text>
        </Space>
      </Card>
    </div>
  );
}

import { useState } from 'react';
import { Alert, Button, Card, Form, Input, Typography } from 'antd';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { apiClient } from '@/shared/api/client';
import { tokens } from '@/shared/design-system';
import safeNext from './safeNext';

export default function RegisterPage() {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const next = safeNext(params.get('next'));
  const loginHref = next ? `/login?next=${encodeURIComponent(next)}` : '/login';

  const submit = async (values) => {
    setBusy(true);
    setError('');
    try {
      await apiClient.post('/auth/register', {
        fullName: values.fullName.trim(), email: values.email.trim(),
        phone: values.phone?.trim() || null, password: values.password,
      });
      navigate(loginHref, { replace: true });
    } catch (problem) {
      setError(problem.message ?? 'Không đăng ký được. Vui lòng thử lại.');
    } finally {
      setBusy(false);
    }
  };

  return (
    <div style={{ maxWidth: tokens.space[8] * 16, margin: '0 auto', minWidth: 0, overflowWrap: 'anywhere' }}>
      <Card>
        <Typography.Title level={3} style={{ marginTop: 0 }}>Đăng ký tài khoản</Typography.Title>
        {error && <Alert type="error" showIcon title={error} style={{ marginBottom: tokens.space[4] }} />}
        <Form layout="vertical" onFinish={submit} requiredMark={false}>
          <Form.Item name="fullName" label="Họ và tên" rules={[{ required: true, whitespace: true, message: 'Nhập họ và tên.' }, { max: 150, message: 'Tối đa 150 ký tự.' }]}>
            <Input autoComplete="name" maxLength={150} />
          </Form.Item>
          <Form.Item name="email" label="Email" rules={[{ required: true, message: 'Nhập email.' }, { type: 'email', message: 'Email không hợp lệ.' }]}>
            <Input type="email" autoComplete="email" maxLength={255} />
          </Form.Item>
          <Form.Item name="phone" label="Số điện thoại (không bắt buộc)" rules={[{ max: 30, message: 'Tối đa 30 ký tự.' }]}>
            <Input type="tel" autoComplete="tel" maxLength={30} />
          </Form.Item>
          <Form.Item name="password" label="Mật khẩu" rules={[{ required: true, message: 'Nhập mật khẩu.' }, { min: 10, message: 'Cần ít nhất 10 ký tự.' }, {
            validator: async (_, value) => { if (value && new TextEncoder().encode(value).length > 72) throw new Error('Tối đa 72 byte UTF-8.'); },
          }]}>
            <Input.Password autoComplete="new-password" />
          </Form.Item>
          <Form.Item name="confirm" label="Nhập lại mật khẩu" dependencies={['password']} rules={[{ required: true, message: 'Nhập lại mật khẩu.' }, ({ getFieldValue }) => ({
            validator(_, value) { return !value || getFieldValue('password') === value ? Promise.resolve() : Promise.reject(new Error('Mật khẩu nhập lại không khớp.')); },
          })]}>
            <Input.Password autoComplete="new-password" />
          </Form.Item>
          <Button type="primary" htmlType="submit" loading={busy} block>Đăng ký</Button>
        </Form>
        <Typography.Text>Đã có tài khoản? <Link to={loginHref}>Đăng nhập</Link></Typography.Text>
      </Card>
    </div>
  );
}

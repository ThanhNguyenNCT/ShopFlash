import { Alert, Button, Card, Descriptions, Space, Tag, Typography } from 'antd';
import { LogoutOutlined, SafetyCertificateOutlined, UserOutlined } from '@ant-design/icons';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { tokens } from '@/shared/design-system';
import useAuth from './useAuth';
import safeNext from './safeNext';
import { MOCK_USERS } from './mockUsers';

// F0 TẠM. TV5 thay file này bằng form đăng nhập thật (email + mật khẩu, backend kiểm tra bcrypt, tạo phiên).
export default function LoginPage() {
  const { user, isAdmin, loginAs, logout } = useAuth();
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const next = safeNext(params.get('next'));
  const needsAdmin = !!next && (next === '/admin' || next.startsWith('/admin/'));

  const enter = (role) => {
    loginAs(role);
    if (next && (!needsAdmin || role === 'ADMIN')) navigate(next, { replace: true });
    else navigate(role === 'ADMIN' ? '/admin' : '/', { replace: true });
  };

  return (
    <div
      style={{
        maxWidth: tokens.space[8] * 16,
        margin: '0 auto',
        display: 'grid',
        gridTemplateColumns: 'minmax(0, 1fr)', // cho phép co dưới độ rộng nội dung (email, đường dẫn dài) ở màn 375px
        gap: tokens.space[4],
        overflowWrap: 'anywhere',
      }}
    >
      <Card>
        <Space orientation="vertical" size={tokens.space[4]} style={{ width: '100%' }}>
          <div>
            <Typography.Title level={3} style={{ margin: 0 }}>Đăng nhập</Typography.Title>
            <Typography.Text type="secondary">Chọn vai trò để dùng thử ShopFlash.</Typography.Text>
          </div>

          {needsAdmin && user && !isAdmin && (
            <Alert type="error" showIcon title="Tài khoản hiện tại không có quyền quản trị. Vào lại với vai trò Admin." />
          )}
          {needsAdmin && !user && (
            <Alert type="info" showIcon title="Trang quản trị cần đăng nhập với vai trò Admin." />
          )}

          {user && (
            <Descriptions
              size="small"
              bordered
              column={1}
              items={[
                { key: 'name', label: 'Đang đăng nhập', children: user.fullName },
                { key: 'email', label: 'Email', children: <span className="sf-code">{user.email}</span> },
                { key: 'role', label: 'Vai trò', children: <Tag color={isAdmin ? 'processing' : 'default'}>{isAdmin ? 'Admin' : 'Khách'}</Tag> },
              ]}
            />
          )}

          <Space orientation="vertical" size={tokens.space[2]} style={{ width: '100%' }}>
            <Button type="primary" size="large" block icon={<UserOutlined />} onClick={() => enter('CUSTOMER')}>
              Vào với vai trò Khách
            </Button>
            <Button size="large" block icon={<SafetyCertificateOutlined />} onClick={() => enter('ADMIN')}>
              Vào với vai trò Admin
            </Button>
            {user && (
              <Button type="text" block icon={<LogoutOutlined />} onClick={logout}>
                Đăng xuất
              </Button>
            )}
          </Space>

          <Typography.Text type="secondary">
            Tài khoản dev seed: <span className="sf-code">{MOCK_USERS.CUSTOMER.email}</span> và{' '}
            <span className="sf-code">{MOCK_USERS.ADMIN.email}</span>. Chưa có tài khoản?{' '}
            <Link to="/register">Đăng ký</Link>
          </Typography.Text>
        </Space>
      </Card>

      <Alert
        type="warning"
        showIcon
        title="Đây là bản đăng nhập tạm (F0) để cả nhóm dùng thử giao diện"
        description={
          <>
            Chưa gọi backend, không có mật khẩu; vai trò được nhớ trong trình duyệt. <strong>TV5</strong> phụ trách bản thật:
            form email và mật khẩu có kiểm tra dữ liệu, backend băm mật khẩu bằng bcrypt (<span className="sf-code">common/security</span>), phiên đăng nhập. Sửa{' '}
            <span className="sf-code">frontend/src/shared/auth/LoginPage.jsx</span> và{' '}
            <span className="sf-code">AuthProvider.jsx</span>, giữ đúng hợp đồng trong{' '}
            <span className="sf-code">frontend/src/shared/auth/AGENTS.md</span>.
          </>
        }
      />
    </div>
  );
}

import { Suspense, useState } from 'react';
import { Button, Grid, Layout, Menu, Spin, Tag, Typography } from 'antd';
import { LogoutOutlined, ThunderboltFilled, UserOutlined } from '@ant-design/icons';
import { Link, Outlet, useLocation, useNavigate } from 'react-router-dom';
import { tokens } from '@/shared/design-system';
import { useAuth } from '@/shared/auth';
import './layouts.css';

const { Sider, Content, Header } = Layout;

// Menu Ant Design có nhóm. `menu` đã được router sắp theo nhóm rồi theo menu.order.
function menuItems(menu) {
  const groups = [];
  for (const r of menu) {
    const name = r.menu.group ?? 'Khác';
    let g = groups.find((x) => x.label === name);
    if (!g) groups.push((g = { type: 'group', key: `group:${name}`, label: name, children: [] }));
    g.children.push({ key: r.path, icon: r.menu.icon, label: r.menu.label });
  }
  return groups;
}

/**
 * Khung màn quản trị: menu nhóm bên trái (thu gọn thành nút ở màn hẹp), header có người dùng, "Về cửa hàng", "Đăng xuất".
 * Đã nằm sau RequireAdmin (router). Trang con đọc danh sách menu qua useOutletContext().adminMenu.
 */
export default function AdminLayout({ menu }) {
  const navigate = useNavigate();
  const { pathname } = useLocation();
  const { user, logout } = useAuth();
  const screens = Grid.useBreakpoint();
  const wide = !!screens.lg;
  const [collapsed, setCollapsed] = useState(false);
  // Màn hẹp: bớt lề để nội dung không bị ép.
  const gutter = screens.md ? tokens.space[6] : tokens.space[4];
  const selected = menu
    .map((r) => r.path)
    .filter((p) => pathname === p || pathname.startsWith(`${p}/`))
    .sort((a, b) => b.length - a.length)[0];

  const onMenu = ({ key }) => {
    navigate(key);
    if (!wide) setCollapsed(true); // màn hẹp: chọn xong thì thu menu lại
  };
  const doLogout = () => {
    logout();
    navigate('/login', { replace: true });
  };

  return (
    <Layout style={{ minHeight: '100vh' }}>
      <Sider
        breakpoint="lg"
        collapsedWidth={0}
        collapsed={collapsed}
        onCollapse={setCollapsed}
        theme="light"
        width={220}
        // Nút mở menu khi Sider thu về 0: đặt trong Header (mặc định top 64px đè lên tiêu đề trang).
        zeroWidthTriggerStyle={{ top: tokens.space[3] }}
      >
        <Link to="/admin" className="sf-admin-brand">
          <ThunderboltFilled aria-hidden /> ShopFlash <small>Admin</small>
        </Link>
        <Menu mode="inline" selectedKeys={selected ? [selected] : []} items={menuItems(menu)} onClick={onMenu} />
      </Sider>
      <Layout style={{ minWidth: 0 }}>
        <Header
          className="sf-admin-header"
          style={{
            background: tokens.color.surface,
            paddingInlineEnd: gutter,
            // Màn hẹp: chừa chỗ cho nút mở menu của Sider (nằm sát mép trái header).
            paddingInlineStart: wide ? gutter : tokens.space[8] + tokens.space[6],
          }}
        >
          {user && (
            <span className="sf-admin-header__user">
              <UserOutlined aria-hidden />
              {screens.sm && <Typography.Text strong className="sf-admin-header__name">{user.fullName}</Typography.Text>}
              <Tag color="processing">{user.role === 'ADMIN' ? 'Admin' : user.role}</Tag>
            </span>
          )}
          <Button type="text" icon={<LogoutOutlined />} aria-label="Đăng xuất" onClick={doLogout}>
            {screens.sm && 'Đăng xuất'}
          </Button>
        </Header>
        <Content style={{ padding: gutter, minWidth: 0 }}>
          <Suspense fallback={<Spin />}>
            <Outlet context={{ adminMenu: menu }} />
          </Suspense>
        </Content>
      </Layout>
    </Layout>
  );
}

import { Suspense } from 'react';
import { Layout, Menu, Spin, Typography } from 'antd';
import { Outlet, useLocation, useNavigate } from 'react-router-dom';
import { tokens } from '@/shared/design-system';

const { Sider, Content, Header } = Layout;

// Khung tối thiểu cho màn quản trị. Đăng nhập/phân quyền (F0) sẽ gắn vào đây khi shared/auth có.
export default function AdminLayout({ menu }) {
  const navigate = useNavigate();
  const { pathname } = useLocation();
  const selected = menu
    .map((r) => r.path)
    .filter((p) => pathname === p || pathname.startsWith(`${p}/`))
    .sort((a, b) => b.length - a.length)[0];

  return (
    <Layout style={{ minHeight: '100vh' }}>
      <Sider breakpoint="lg" collapsedWidth={0} theme="light" width={220}>
        <Typography.Title level={5} style={{ padding: tokens.space[4], margin: 0 }}>
          ShopFlash Admin
        </Typography.Title>
        <Menu
          mode="inline"
          selectedKeys={selected ? [selected] : []}
          items={menu.map((r) => ({ key: r.path, icon: r.menu.icon, label: r.menu.label }))}
          onClick={({ key }) => navigate(key)}
        />
      </Sider>
      <Layout>
        <Header style={{ background: tokens.color.surface, paddingInline: tokens.space[6] }} />
        <Content style={{ padding: tokens.space[6] }}>
          <Suspense fallback={<Spin />}>
            <Outlet />
          </Suspense>
        </Content>
      </Layout>
    </Layout>
  );
}

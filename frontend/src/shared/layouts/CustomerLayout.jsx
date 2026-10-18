import { Suspense, useEffect, useState } from 'react';
import { Button, Divider, Drawer, Dropdown, Grid, Input, Spin, Typography } from 'antd';
import {
  DashboardOutlined, HomeOutlined, LoginOutlined, LogoutOutlined, MenuOutlined, ProfileOutlined,
  SearchOutlined, ShoppingCartOutlined, ThunderboltFilled, ThunderboltOutlined, UserOutlined,
} from '@ant-design/icons';
import { Link, NavLink, Outlet, useLocation, useNavigate, useSearchParams } from 'react-router-dom';
import { tokens } from '@/shared/design-system';
import { useAuth } from '@/shared/auth';
import './layouts.css';

const NAV = [
  { to: '/flash-sale', label: 'Flash Sale', icon: <ThunderboltOutlined /> },
  { to: '/orders', label: 'Đơn hàng', icon: <ProfileOutlined /> },
];

// Ô tìm kiếm ở header: gửi sang /search?q=...; đang ở /search thì hiện lại từ khóa trên URL.
function SearchBox({ className }) {
  const navigate = useNavigate();
  const { pathname } = useLocation();
  const [params] = useSearchParams();
  const urlQ = pathname === '/search' ? params.get('q') ?? '' : '';
  const [q, setQ] = useState(urlQ);
  useEffect(() => setQ(urlQ), [urlQ]);

  const submit = (value) => {
    const v = value.trim();
    navigate(v ? `/search?q=${encodeURIComponent(v)}` : '/search');
  };
  return (
    <Input.Search
      className={className}
      placeholder="Tìm sản phẩm, danh mục, SKU"
      aria-label="Tìm sản phẩm"
      allowClear
      value={q}
      onChange={(e) => setQ(e.target.value)}
      onSearch={submit}
      enterButton={<SearchOutlined aria-label="Tìm" />}
    />
  );
}

/** Khung trang khách hàng: header (logo, tìm kiếm, điều hướng, giỏ, tài khoản), nội dung, footer. */
export default function CustomerLayout() {
  const screens = Grid.useBreakpoint();
  const mobile = !screens.md;
  const navigate = useNavigate();
  const { pathname, search } = useLocation();
  const { user, isAdmin, logout } = useAuth();
  const [drawerOpen, setDrawerOpen] = useState(false);

  // Đổi trang thì đóng menu mobile.
  useEffect(() => setDrawerOpen(false), [pathname, search]);

  const loginHref = pathname === '/login' ? '/login' : `/login?next=${encodeURIComponent(pathname + search)}`;
  const doLogout = () => {
    logout();
    navigate('/');
  };

  const accountItems = user
    ? [
        {
          key: 'who',
          disabled: true,
          label: (
            <span>
              <Typography.Text strong>{user.fullName}</Typography.Text>
              <br />
              <span className="sf-code">{user.email}</span>
            </span>
          ),
        },
        { type: 'divider' },
        { key: '/orders', icon: <ProfileOutlined />, label: 'Đơn hàng của tôi' },
        ...(isAdmin ? [{ key: '/admin', icon: <DashboardOutlined />, label: 'Trang quản trị' }] : []),
        { type: 'divider' },
        { key: 'logout', icon: <LogoutOutlined />, label: 'Đăng xuất', danger: true },
      ]
    : [];
  const onAccount = ({ key }) => (key === 'logout' ? doLogout() : navigate(key));

  return (
    <div className="sf-shop">
      <header className="sf-shop-header">
        <div className="sf-container sf-shop-header__inner">
          {mobile && (
            <Button type="text" icon={<MenuOutlined />} aria-label="Mở menu" onClick={() => setDrawerOpen(true)} />
          )}
          <Link to="/" className="sf-shop-logo" aria-label="ShopFlash, về trang chủ">
            <ThunderboltFilled aria-hidden /> ShopFlash
          </Link>
          {!mobile && <SearchBox className="sf-shop-header__search" />}
          {!mobile && (
            <nav className="sf-shop-nav" aria-label="Điều hướng chính">
              {NAV.map((n) => <NavLink key={n.to} to={n.to}>{n.icon}{n.label}</NavLink>)}
            </nav>
          )}
          <div className="sf-shop-header__actions">
            <Button type="text" icon={<ShoppingCartOutlined />} aria-label="Giỏ hàng" onClick={() => navigate('/cart')}>
              {!mobile && 'Giỏ hàng'}
            </Button>
            {user ? (
              <Dropdown menu={{ items: accountItems, onClick: onAccount }} trigger={['click']} placement="bottomRight">
                <Button type="text" icon={<UserOutlined />} aria-label={`Tài khoản ${user.fullName}`}>
                  {!mobile && user.fullName}
                </Button>
              </Dropdown>
            ) : (
              <Button icon={<LoginOutlined />} aria-label="Đăng nhập" onClick={() => navigate(loginHref)}>
                {!mobile && 'Đăng nhập'}
              </Button>
            )}
          </div>
        </div>
        {mobile && (
          <div className="sf-container sf-shop-header__search-row">
            <SearchBox />
          </div>
        )}
      </header>

      <Drawer
        title="ShopFlash"
        placement="left"
        size={tokens.space[8] * 9}
        open={drawerOpen}
        onClose={() => setDrawerOpen(false)}
      >
        <nav className="sf-drawer-nav sf-shop-nav" aria-label="Điều hướng chính (mobile)">
          <NavLink to="/" end><HomeOutlined />Trang chủ</NavLink>
          {NAV.map((n) => <NavLink key={n.to} to={n.to}>{n.icon}{n.label}</NavLink>)}
          <NavLink to="/cart"><ShoppingCartOutlined />Giỏ hàng</NavLink>
          {isAdmin && <NavLink to="/admin"><DashboardOutlined />Trang quản trị</NavLink>}
        </nav>
        <Divider />
        {user ? (
          <div className="sf-drawer-nav">
            <Typography.Text strong>{user.fullName}</Typography.Text>
            <span className="sf-code">{user.email}</span>
            <Button icon={<LogoutOutlined />} onClick={doLogout} block>Đăng xuất</Button>
          </div>
        ) : (
          <Button icon={<LoginOutlined />} onClick={() => navigate(loginHref)} block>Đăng nhập</Button>
        )}
      </Drawer>

      <main className="sf-container sf-shop-main">
        <Suspense fallback={<Spin />}>
          <Outlet />
        </Suspense>
      </main>

      <footer className="sf-shop-footer">
        <div className="sf-container sf-shop-footer__inner">
          <span>ShopFlash · Bài tập lớn Hệ quản trị Cơ sở dữ liệu (HK261)</span>
          <span>Oracle Database + Redis Stack</span>
        </div>
      </footer>
    </div>
  );
}

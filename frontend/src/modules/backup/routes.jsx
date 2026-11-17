import { lazy } from 'react';
// import { CloudServerOutlined } from '@ant-design/icons'; // dùng lại khi bật menu

// Module backup (TV1): F11 sao lưu. Metadata route: xem frontend/AGENTS.md.
const AdminBackupsPage = lazy(() => import('./pages/AdminBackupsPage'));

export default [
  {
    path: '/admin/backups',
    element: <AdminBackupsPage />,
    title: 'Sao lưu và phục hồi',
    // Ẩn khỏi sidebar nhưng vẫn truy cập được bằng URL /admin/backups. Bỏ comment dòng dưới để hiện lại.
    // menu: { label: 'Sao lưu', icon: <CloudServerOutlined />, group: 'Hệ thống', order: 1, owner: 'TV1' },
  },
];

import { lazy } from 'react';
import { BarChartOutlined, DashboardOutlined, ExperimentOutlined } from '@ant-design/icons';

// Module query (TV3): F5 báo cáo doanh thu, F6 top bán chạy và khách VIP. Metadata route: xem frontend/AGENTS.md.
const AdminDashboardPage = lazy(() => import('./pages/AdminDashboardPage'));
const ReportsPage = lazy(() => import('./pages/ReportsPage'));
const QueryLabPage = lazy(() => import('./pages/QueryLabPage'));

export default [
  {
    path: '/admin',
    element: <AdminDashboardPage />,
    title: 'Tổng quan quản trị',
    menu: { label: 'Tổng quan', icon: <DashboardOutlined />, group: 'Tổng quan', order: 0, owner: 'TV3', feature: 'F5, F6' },
  },
  {
    path: '/admin/reports',
    element: <ReportsPage />,
    title: 'Báo cáo',
    menu: { label: 'Báo cáo', icon: <BarChartOutlined />, group: 'Báo cáo', order: 1, owner: 'TV3', feature: 'F5, F6' },
  },
  {
    path: '/admin/reports/lab',
    element: <QueryLabPage />,
    title: 'So sánh Oracle–Redis',
    menu: { label: 'So sánh Oracle–Redis', icon: <ExperimentOutlined />, group: 'Báo cáo', order: 2, owner: 'TV3', feature: 'F5, F6' },
  },
];

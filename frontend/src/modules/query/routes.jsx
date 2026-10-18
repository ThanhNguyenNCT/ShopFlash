import { lazy } from 'react';
import { BarChartOutlined, ExperimentOutlined } from '@ant-design/icons';

const ReportsPage = lazy(() => import('./pages/ReportsPage'));
const QueryLabPage = lazy(() => import('./pages/QueryLabPage'));

export default [
  { path: '/admin/reports', element: <ReportsPage />, menu: { label: 'Báo cáo', icon: <BarChartOutlined /> } },
  { path: '/admin/reports/lab', element: <QueryLabPage />, menu: { label: 'So sánh Oracle–Redis', icon: <ExperimentOutlined /> } },
];

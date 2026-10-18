import { useCallback, useRef, useState } from 'react';
import dayjs from 'dayjs';
import relativeTime from 'dayjs/plugin/relativeTime';
import 'dayjs/locale/vi';
import { PriceTag } from '@/shared/design-system';
import { REPORTS } from '../../api';
import { DATE_FORMAT, formatInt } from '../../format';

dayjs.extend(relativeTime);

export const JOIN_REPORTS = REPORTS.filter((r) => r.joinHint);
export const reportLabel = (slug) => REPORTS.find((r) => r.slug === slug)?.label ?? slug;
export const sourceLabel = (source) => (source === 'redis' ? 'Redis' : 'Oracle');

export const HINT_LABEL = {
  AUTO: 'AUTO (optimizer tự chọn)',
  NL: 'NL (nested loops)',
  HASH: 'HASH (hash join)',
  MERGE: 'MERGE (sort-merge join)',
  AGGREGATE: 'AGGREGATE (FT.AGGREGATE trên server)',
  CLIENT: 'CLIENT (kéo về Java tự tính)',
};

export function rangeParams(range) {
  if (!range?.[0] || !range?.[1]) return {};
  return { from: range[0].format(DATE_FORMAT), to: range[1].format(DATE_FORMAT) };
}

// Tham số riêng theo báo cáo: limit cho top-products, page/size cho báo cáo phân trang.
export function reportParams(report, { limit, page, size } = {}) {
  const meta = REPORTS.find((r) => r.slug === report);
  const params = {};
  if (report === 'top-products') params.limit = limit;
  if (meta?.paged) Object.assign(params, { page, size });
  return params;
}

// Khóa so sánh tham số: kết quả chỉ còn "hiện hành" khi tham số đang chọn trùng tham số của lần chạy.
export const runKey = (...parts) => JSON.stringify(parts);

export function formatDateTime(iso) {
  if (!iso) return null;
  const d = dayjs(iso);
  return { relative: d.locale('vi').fromNow(), absolute: d.format('DD/MM/YYYY HH:mm:ss') };
}

const MONEY_FIELDS = new Set(['revenue', 'totalSpent', 'basePrice', 'averageSpent']);
export const isMoneyField = (field) => MONEY_FIELDS.has(field);

const num = (v) => <span className="sf-num">{formatInt(v)}</span>;
const money = (v) => <PriceTag price={v} size="sm" />;
const code = (v) => <span className="sf-code">{v}</span>;

// Cột bảng kết quả theo contract (mục "Dạng rows theo báo cáo").
const COLUMNS = {
  'revenue-by-month': [
    { title: 'Tháng', dataIndex: 'month', render: code },
    { title: 'Số đơn', dataIndex: 'orderCount', align: 'right', render: num },
    { title: 'Doanh thu', dataIndex: 'revenue', align: 'right', render: money },
  ],
  'revenue-by-category': [
    { title: 'Mã DM', dataIndex: 'categoryId', render: code },
    { title: 'Danh mục', dataIndex: 'categoryName' },
    { title: 'Số lượng', dataIndex: 'quantity', align: 'right', render: num },
    { title: 'Doanh thu', dataIndex: 'revenue', align: 'right', render: money },
  ],
  'top-products': [
    { title: '#', dataIndex: 'rank', render: num },
    { title: 'Mã SP', dataIndex: 'productId', render: code },
    { title: 'Sản phẩm', dataIndex: 'productName' },
    { title: 'Danh mục', dataIndex: 'categoryName' },
    { title: 'Số lượng', dataIndex: 'quantity', align: 'right', render: num },
    { title: 'Doanh thu', dataIndex: 'revenue', align: 'right', render: money },
  ],
  'vip-customers': [
    { title: 'Mã KH', dataIndex: 'userId', render: code },
    { title: 'Họ tên', dataIndex: 'fullName' },
    { title: 'Email', dataIndex: 'email' },
    { title: 'Số đơn', dataIndex: 'orderCount', align: 'right', render: num },
    { title: 'Tổng chi', dataIndex: 'totalSpent', align: 'right', render: money },
  ],
  'unsold-products': [
    { title: 'Mã SP', dataIndex: 'productId', render: code },
    { title: 'Sản phẩm', dataIndex: 'productName' },
    { title: 'Danh mục', dataIndex: 'categoryName' },
    { title: 'Giá gốc', dataIndex: 'basePrice', align: 'right', render: money },
  ],
};

const ROW_KEY = {
  'revenue-by-month': 'month',
  'revenue-by-category': 'categoryId',
  'top-products': 'productId',
  'vip-customers': 'userId',
  'unsold-products': 'productId',
};

export function columnsFor(report, rows = []) {
  if (COLUMNS[report]) return COLUMNS[report];
  return Object.keys(rows[0] ?? {}).map((k) => ({ title: k, dataIndex: k }));
}

export const rowKeyFor = (report) => ROW_KEY[report] ?? ((_, i) => i);

const IDLE = { loading: false, error: null, data: null };

// Gọi API có loading/error; bỏ qua kết quả của lần gọi cũ nếu người dùng bấm lại.
// Bắt đầu lần chạy mới hay chạy lỗi thì bỏ dữ liệu cũ, để không hiện kết quả lần trước như thể là kết quả hiện tại.
export function useApi(fn) {
  const [state, setState] = useState(IDLE);
  const seq = useRef(0);
  const run = useCallback(
    async (...args) => {
      const id = ++seq.current;
      setState({ ...IDLE, loading: true });
      try {
        const data = await fn(...args);
        if (id === seq.current) setState({ ...IDLE, data });
        return data;
      } catch (error) {
        if (id === seq.current) setState({ ...IDLE, error });
        return undefined;
      }
    },
    [fn],
  );
  // Xóa kết quả/lỗi đang hiện và bỏ qua response của lần gọi đang chờ (nếu có).
  const reset = useCallback(() => {
    seq.current += 1;
    setState(IDLE);
  }, []);
  return { ...state, run, reset };
}

// Kết quả của Promise.allSettled → { value, error } để mỗi khung tự hiện lỗi của mình.
export const settle = (r) => (r.status === 'fulfilled' ? { value: r.value, error: null } : { value: null, error: r.reason });

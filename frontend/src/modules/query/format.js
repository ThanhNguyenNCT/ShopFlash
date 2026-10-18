import dayjs from 'dayjs';

// Định dạng và khoảng ngày dùng chung cho trang Báo cáo và trang So sánh Oracle–Redis.
// Tiền đầy đủ luôn dùng formatVND / PriceTag của design system. Giá trị thiếu (null/undefined) hiện là "—".

export const DATE_FORMAT = 'YYYY-MM-DD';
export const EMPTY = '—';

// Giống mặc định của backend: từ ngày 1 của tháng cách đây 11 tháng tới hôm nay (12 tháng tính cả tháng hiện tại).
export function defaultRange() {
  const today = dayjs();
  return [today.subtract(11, 'month').startOf('month'), today];
}

// "01/11/2025 – 18/10/2026", để ghi khoảng ngày lên tiêu đề kết quả.
export function formatRange(range) {
  if (!range?.[0] || !range?.[1]) return EMPTY;
  return `${range[0].format('DD/MM/YYYY')} – ${range[1].format('DD/MM/YYYY')}`;
}

const nf = (opts) => new Intl.NumberFormat('vi-VN', opts);
const intFormat = nf({ maximumFractionDigits: 0 });
const msFormat = nf({ maximumFractionDigits: 2 });
const oneDecimal = nf({ maximumFractionDigits: 1 });

const missing = (n) => n === null || n === undefined || n === '' || Number.isNaN(Number(n));

export const formatInt = (n) => (missing(n) ? EMPTY : intFormat.format(Number(n)));
export const formatMs = (n) => (missing(n) ? EMPTY : `${msFormat.format(Number(n))} ms`);
export const formatRatio = (n) => (missing(n) ? EMPTY : oneDecimal.format(Number(n)));

// 8_400_000_000 -> "8,4 tỷ"; 312_000_000 -> "312 tr" (chỉ cho trục biểu đồ, chỗ chật)
export function formatVNDCompact(n) {
  const v = Number(n) || 0;
  const abs = Math.abs(v);
  if (abs >= 1e9) return `${oneDecimal.format(v / 1e9)} tỷ`;
  if (abs >= 1e6) return `${oneDecimal.format(v / 1e6)} tr`;
  if (abs >= 1e3) return `${oneDecimal.format(v / 1e3)} N`;
  return intFormat.format(v);
}

// "2026-10" -> "T10/2026"
export function formatMonth(month) {
  const [y, m] = String(month).split('-');
  return m ? `T${Number(m)}/${y}` : String(month);
}

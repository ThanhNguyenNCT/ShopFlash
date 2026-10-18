// Định dạng số cho trang báo cáo. Tiền đầy đủ luôn dùng formatVND / PriceTag của design system.

const nf = (opts) => new Intl.NumberFormat('vi-VN', opts);
const intFormat = nf({ maximumFractionDigits: 0 });
const msFormat = nf({ maximumFractionDigits: 2 });
const oneDecimal = nf({ maximumFractionDigits: 1 });

export const formatInt = (n) => intFormat.format(Number(n) || 0);
export const formatMs = (n) => `${msFormat.format(Number(n) || 0)} ms`;

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

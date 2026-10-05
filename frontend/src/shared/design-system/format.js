// 549000 -> "549.000₫"
export function formatVND(amount) {
  return Math.round(Number(amount) || 0).toLocaleString('vi-VN') + '₫';
}

// (549000, 999000) -> 45  (phần trăm giảm, làm tròn)
export function discountPercent(price, originalPrice) {
  if (!originalPrice || originalPrice <= price) return 0;
  return Math.round((1 - price / originalPrice) * 100);
}

// 8077 -> ["02", "14", "37"]
export function splitDuration(totalSeconds) {
  const s = Math.max(0, Math.floor(totalSeconds));
  return [Math.floor(s / 3600), Math.floor((s % 3600) / 60), s % 60].map((n) => String(n).padStart(2, '0'));
}

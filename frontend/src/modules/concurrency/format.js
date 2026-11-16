import { useEffect, useState } from 'react';
import dayjs from 'dayjs';

// Tiện ích dùng chung trong module concurrency.

// --- Đồng hồ theo giờ server ---
// Mọi đếm ngược dựa trên serverTime trong response, không theo đồng hồ máy khách (máy khách có thể lệch vài phút).
// offset = giờ server - giờ máy khách lúc nhận response; giờ server hiện tại = Date.now() + offset.

export function offsetFrom(serverTime) {
  const ms = Date.parse(serverTime);
  return Number.isFinite(ms) ? ms - Date.now() : 0;
}

/**
 * Cập nhật offset chỉ khi lệch hơn 0,5 s so với giá trị cũ. Mỗi response cho offset hơi khác nhau (độ trễ mạng);
 * đổi liên tục sẽ làm đồng hồ đếm ngược khởi động lại và nhảy giây.
 */
export function nextOffset(current, serverTime) {
  const fresh = offsetFrom(serverTime);
  return Math.abs(fresh - current) > 500 ? fresh : current;
}

/** Mốc giờ server → mốc tương ứng trên đồng hồ máy khách, để truyền cho FlashCountdown (đếm theo Date.now()). */
export function toClientTime(serverInstant, offset) {
  return Date.parse(serverInstant) - offset;
}

/** Giờ server hiện tại (ms), cập nhật mỗi giây. */
export function useServerNow(offset) {
  const [now, setNow] = useState(() => Date.now() + offset);
  useEffect(() => {
    setNow(Date.now() + offset);
    const timer = setInterval(() => setNow(Date.now() + offset), 1000);
    return () => clearInterval(timer);
  }, [offset]);
  return now;
}

// --- Hiển thị ---

export function formatDateTime(value) {
  return value ? dayjs(value).format('DD/MM/YYYY HH:mm') : '–';
}

export function formatTime(value) {
  return value ? dayjs(value).format('HH:mm DD/MM') : '–';
}

/** Trạng thái chương trình (FLASH_SALES.status). Tag luôn có chữ, màu chỉ để nhận nhanh. */
export const SALE_STATUS = {
  DRAFT: { label: 'Nháp', color: 'default' },
  SCHEDULED: { label: 'Đã lên lịch', color: 'processing' },
  ACTIVE: { label: 'Đang chạy', color: 'success' },
  ENDED: { label: 'Đã kết thúc', color: 'default' },
  CANCELLED: { label: 'Đã hủy', color: 'warning' },
};

export const saleStatusOptions = Object.entries(SALE_STATUS).map(([value, { label }]) => ({ value, label }));

// --- Kiểm tra form ---

/** Backend giới hạn độ dài theo byte UTF-8 (chữ có dấu chiếm 2–3 byte), sau khi chuẩn hóa NFC và bỏ khoảng trắng hai đầu. */
export function utf8Length(value) {
  return new TextEncoder().encode(String(value ?? '').normalize('NFC').trim()).length;
}

/** Rule của Ant Form: tối đa maxBytes byte, kèm thông báo. */
export function maxBytesRule(maxBytes, message) {
  return {
    validator: (_, value) => (utf8Length(value) <= maxBytes ? Promise.resolve() : Promise.reject(new Error(message))),
  };
}

export function formatNumber(value, digits = 0) {
  if (value === null || value === undefined || Number.isNaN(value)) return '–';
  return Number(value).toLocaleString('vi-VN', { minimumFractionDigits: digits, maximumFractionDigits: digits });
}

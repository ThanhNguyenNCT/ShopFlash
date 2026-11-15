package com.shopflash.modules.concurrency.model;

/**
 * Kết quả giữ suất. created = false nghĩa là người dùng đã có reservation còn hiệu lực và nhận lại đúng
 * reservation đó (bấm "Mua ngay" hai lần). Giờ theo đồng hồ Redis, epoch ms.
 */
public record ReserveOutcome(boolean created, String reservationId, long expiresAtMs, long serverTimeMs) {
}

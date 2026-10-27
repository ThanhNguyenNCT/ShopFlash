package com.shopflash.modules.transaction.security;

/** Danh tính khách hàng đã được backend xác minh cho nghiệp vụ Transaction. */
public record TransactionCustomer(long userId, String email, String role) {}

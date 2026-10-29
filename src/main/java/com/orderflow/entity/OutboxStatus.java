package com.orderflow.entity;

public enum OutboxStatus {
    PENDING,
    IN_FLIGHT,
    DELIVERED,
    FAILED,
    FAILED_PERMANENT
}

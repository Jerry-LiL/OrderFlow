package com.orderflow.dto.request;

import jakarta.validation.constraints.NotNull;

public record StockUpdateRequest(
        @NotNull(message = "Delta is required")
        Integer delta // positive to increase, negative to decrease
) {}

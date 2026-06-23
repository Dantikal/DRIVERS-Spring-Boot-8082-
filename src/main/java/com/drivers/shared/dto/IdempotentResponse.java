package com.drivers.shared.dto;

import lombok.Builder;

@Builder
public record IdempotentResponse<T>(
        T data,
        boolean isReplayed
) {}
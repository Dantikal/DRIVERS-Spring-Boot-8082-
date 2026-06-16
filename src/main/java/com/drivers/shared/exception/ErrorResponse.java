package com.drivers.shared.exception;

import lombok.Builder;

@Builder
public record ErrorResponse(
        ErrorDetail errorDetail
) {}

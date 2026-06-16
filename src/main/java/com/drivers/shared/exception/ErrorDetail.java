package com.drivers.shared.exception;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

import java.util.List;
import java.util.Map;

@Builder
public record ErrorDetail(
        String code,
        String message,
        Map<String, List<String>> fields,
        @JsonProperty("trace_id")
        String traceId
) {}

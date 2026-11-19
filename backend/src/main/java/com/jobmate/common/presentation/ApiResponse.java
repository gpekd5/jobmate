package com.jobmate.common.presentation;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(boolean success, T data, ApiError error) {

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, data, null);
    }

    public static ApiResponse<Void> failure(String code, String message, List<FieldError> details) {
        return new ApiResponse<>(false, null, new ApiError(code, message, details));
    }

    public record ApiError(String code, String message, List<FieldError> details) {
    }

    public record FieldError(String field, String reason) {
    }
}

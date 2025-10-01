package ru.sunveil.precision_pdf.controller.dto;

import lombok.Data;

@Data
public class ApiResponse<T> {
    private boolean success;
    private String message;
    private T data;
    private long processingTimeMs;

    public ApiResponse(boolean success, String message, T data, long processingTimeMs) {
        this.success = success;
        this.message = message;
        this.data = data;
        this.processingTimeMs = processingTimeMs;
    }

    public static <T> ApiResponse<T> success(T data, String message, long processingTimeMs) {
        return new ApiResponse<>(true, message, data, processingTimeMs);
    }

    public static <T> ApiResponse<T> error(String message, long processingTimeMs) {
        return new ApiResponse<>(false, message, null, processingTimeMs);
    }

}
package vn.saibuoc.shop.common;

import java.util.List;

public record ErrorResponse(boolean success, String errorCode, String message, List<FieldError> details) {

    public record FieldError(String field, String message) {
    }

    public static ErrorResponse of(String errorCode, String message) {
        return new ErrorResponse(false, errorCode, message, List.of());
    }

    public static ErrorResponse of(String errorCode, String message, List<FieldError> details) {
        return new ErrorResponse(false, errorCode, message, details);
    }
}

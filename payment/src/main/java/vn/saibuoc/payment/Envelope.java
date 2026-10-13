package vn.saibuoc.payment;

public record Envelope<T>(boolean success, T data, String message, String errorCode) {

    public static <T> Envelope<T> ok(T data) {
        return new Envelope<>(true, data, "", null);
    }

    public static <T> Envelope<T> error(String code, String message) {
        return new Envelope<>(false, null, message, code);
    }
}

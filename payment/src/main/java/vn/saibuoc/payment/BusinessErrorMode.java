package vn.saibuoc.payment;

import java.time.Instant;
import java.util.Map;

/**
 * "Core banking" style failure: the charge endpoint answers HTTP 200 but carries a non-"00" responseCode in the
 * body (and in the X-Response-Code header). Switched on by the shop backend for a limited time.
 *
 * @param code    responseCode returned instead of "00"
 * @param percent share of charges affected
 * @param until   the mode switches itself off at this instant
 */
public record BusinessErrorMode(String code, int percent, Instant until) {

    /** Supported codes and their messages. */
    public static final Map<String, String> MESSAGES = Map.of(
            "99", "Hệ thống core bận, vui lòng thử lại sau",
            "68", "Tài khoản không đủ điều kiện giao dịch",
            "12", "Sai định dạng giao dịch");

    public boolean activeAt(Instant now) {
        return code != null && percent > 0 && until != null && now.isBefore(until);
    }

    public String message() {
        return MESSAGES.get(code);
    }
}

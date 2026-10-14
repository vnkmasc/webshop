package vn.saibuoc.payment;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class BusinessErrorModeTest {

    private static final Instant NOW = Instant.parse("2026-10-14T03:00:00Z");

    @Test
    void activeOnlyBeforeExpiryAndWithAPercentage() {
        assertThat(new BusinessErrorMode("99", 40, NOW.plusSeconds(60)).activeAt(NOW)).isTrue();
        assertThat(new BusinessErrorMode("99", 40, NOW.minusSeconds(1)).activeAt(NOW)).isFalse();
        assertThat(new BusinessErrorMode("99", 0, NOW.plusSeconds(60)).activeAt(NOW)).isFalse();
        assertThat(new BusinessErrorMode("99", 40, null).activeAt(NOW)).isFalse();
    }

    @Test
    void knowsTheSupportedCodes() {
        assertThat(BusinessErrorMode.MESSAGES).containsOnlyKeys("99", "68", "12");
        assertThat(new BusinessErrorMode("68", 10, NOW).message()).contains("không đủ điều kiện");
    }
}

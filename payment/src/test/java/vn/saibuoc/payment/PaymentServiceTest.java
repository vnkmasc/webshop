package vn.saibuoc.payment;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PaymentServiceTest {

    @Test
    void luhnAcceptsTestCardsAndRejectsTypos() {
        assertThat(PaymentService.luhn("4111111111111111")).isTrue();
        assertThat(PaymentService.luhn("4000000000000002")).isTrue();
        assertThat(PaymentService.luhn("4111111111111112")).isFalse();
    }

    @Test
    void detectsBrand() {
        assertThat(PaymentService.brand("4111111111111111")).isEqualTo("VISA");
        assertThat(PaymentService.brand("5555555555554444")).isEqualTo("MASTERCARD");
        assertThat(PaymentService.brand("9704000000000018")).isEqualTo("NAPAS");
    }
}

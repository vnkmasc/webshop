package vn.saibuoc.shop.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SlugsTest {

    @Test
    void stripsVietnameseMarks() {
        assertThat(Slugs.of("Sải Bước Swift Đỏ")).isEqualTo("sai-buoc-swift-do");
        assertThat(Slugs.of("  Giày   chạy bộ! ")).isEqualTo("giay-chay-bo");
    }
}

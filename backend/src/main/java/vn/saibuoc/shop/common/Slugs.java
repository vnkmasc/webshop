package vn.saibuoc.shop.common;

import java.text.Normalizer;

public final class Slugs {

    private Slugs() {
    }

    /** "Giày Chạy Bộ Đen" -> "giay-chay-bo-den" */
    public static String of(String text) {
        String ascii = Normalizer.normalize(text.replace('Đ', 'D').replace('đ', 'd'), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return ascii.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
    }
}

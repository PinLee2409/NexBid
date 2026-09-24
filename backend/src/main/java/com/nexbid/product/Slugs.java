package com.nexbid.product;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * EN: Turns a display name into something safe for a URL.
 * VI: Biến tên hiển thị thành chuỗi an toàn để đặt trên URL.
 */
final class Slugs {

    private static final Pattern COMBINING_MARKS = Pattern.compile("\\p{M}");
    private static final Pattern NON_SLUG = Pattern.compile("[^a-z0-9]+");
    private static final Pattern EDGE_DASHES = Pattern.compile("^-+|-+$");

    private static final int MAX_LENGTH = 80;

    private Slugs() {
    }

    /**
     * EN: Strips accents first, so "Đồng hồ" becomes "dong-ho" rather than losing the words entirely.
     * VI: Bỏ dấu trước, để "Đồng hồ" thành "dong-ho" chứ không phải mất sạch chữ.
     */
    static String from(String name) {
        // EN: Đ and đ carry no combining mark, so they survive normalisation and must be handled by hand.
        // VI: Đ và đ không mang dấu phụ nên sống sót qua bước chuẩn hoá, phải xử lý riêng.
        String latinised = name.replace("đ", "d").replace("Đ", "D");

        String withoutMarks = COMBINING_MARKS
                .matcher(Normalizer.normalize(latinised, Normalizer.Form.NFD))
                .replaceAll("");

        String slug = EDGE_DASHES
                .matcher(NON_SLUG.matcher(withoutMarks.toLowerCase(Locale.ROOT)).replaceAll("-"))
                .replaceAll("");

        if (slug.isEmpty()) {
            throw new IllegalArgumentException("Name produces an empty slug: " + name);
        }

        if (slug.length() <= MAX_LENGTH) {
            return slug;
        }

        // EN: Truncating can leave a trailing dash, which would read as a different slug.
        // VI: Cắt ngắn có thể để lại dấu gạch cuối, khiến nó thành một slug khác.
        return EDGE_DASHES.matcher(slug.substring(0, MAX_LENGTH)).replaceAll("");
    }
}

package com.nexbid.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** EN: Slugs end up in URLs, so they must be predictable. / VI: Slug nằm trên URL nên phải đoán trước được. */
class SlugsTest {

    @ParameterizedTest
    @CsvSource({
            "Watches,                watches",
            "Trading Cards,          trading-cards",
            "  Spaced  Out  ,        spaced-out",
            "Art & Prints,           art-prints",
            "Đồng hồ,                dong-ho",
            "Máy ảnh cũ,             may-anh-cu",
            "Sneakers!!!,            sneakers",
            "A---B,                  a-b",
    })
    void producesUrlSafeSlugs(String name, String expected) {
        assertThat(Slugs.from(name)).isEqualTo(expected);
    }

    @Test
    void neverEndsWithADashAfterTruncation() {
        String slug = Slugs.from("a".repeat(78) + " bb");
        assertThat(slug).hasSizeLessThanOrEqualTo(80).doesNotEndWith("-");
    }

    @Test
    void aNameWithNoLettersOrDigitsIsRejected() {
        assertThatThrownBy(() -> Slugs.from("!!!"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

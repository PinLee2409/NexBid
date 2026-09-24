package com.nexbid.product.storage;

/**
 * EN: Identifies an image by what it actually starts with, not by what the upload claims to be.
 * VI: Nhận dạng ảnh bằng những byte nó thực sự bắt đầu, không phải bằng lời khai của phía tải lên.
 */
final class MagicBytes {

    private MagicBytes() {
    }

    /** EN: Null when the header matches no image we accept. / VI: Trả null khi header không khớp ảnh nào ta nhận. */
    static String imageTypeOf(byte[] header) {
        if (header.length < 12) {
            return null;
        }

        // FF D8 FF
        if (matches(header, 0, 0xFF, 0xD8, 0xFF)) {
            return "image/jpeg";
        }

        // 89 50 4E 47 0D 0A 1A 0A
        if (matches(header, 0, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)) {
            return "image/png";
        }

        // "GIF87a" / "GIF89a"
        if (matches(header, 0, 'G', 'I', 'F', '8')) {
            return "image/gif";
        }

        // EN: RIFF....WEBP — the size sits between the two markers, so they are checked separately.
        // VI: RIFF....WEBP — kích thước nằm giữa hai dấu hiệu nên phải kiểm riêng từng phần.
        if (matches(header, 0, 'R', 'I', 'F', 'F') && matches(header, 8, 'W', 'E', 'B', 'P')) {
            return "image/webp";
        }

        // EN: ....ftypavif — an ISO base media box, same family as MP4.
        // VI: ....ftypavif — một box theo chuẩn ISO base media, cùng họ với MP4.
        if (matches(header, 4, 'f', 't', 'y', 'p') && matches(header, 8, 'a', 'v', 'i', 'f')) {
            return "image/avif";
        }

        return null;
    }

    private static boolean matches(byte[] header, int offset, int... expected) {
        if (header.length < offset + expected.length) {
            return false;
        }

        for (int i = 0; i < expected.length; i++) {
            if ((header[offset + i] & 0xFF) != expected[i]) {
                return false;
            }
        }

        return true;
    }
}

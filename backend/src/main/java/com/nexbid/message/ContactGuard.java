package com.nexbid.message;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * EN: Keeps a sale on NexBid. A message may not carry a phone number, an email address, a link, a long run of digits
 *     (an account number) or the name of a chat or payment app: those are how a deal leaves the platform, and with it
 *     the buyer's protection. Separators and Vietnamese accents are ignored, so "0 9 1 2 . 3 4 5" and "Zalo" are caught.
 * VI: Giữ giao dịch trên NexBid. Tin nhắn không được chứa số điện thoại, email, đường link, dãy số dài (số tài khoản)
 *     hay tên ứng dụng chat, ví điện tử: đó là cách giao dịch rời khỏi sàn, và người mua mất luôn sự bảo vệ. Bỏ qua dấu
 *     phân cách và dấu tiếng Việt, nên "0 9 1 2 . 3 4 5" hay "Zalo" đều bị bắt.
 */
final class ContactGuard {

    /** EN: Eight digits or more, with any spaces, dots or dashes between them. / VI: Tám chữ số trở lên, có thể xen khoảng trắng, chấm, gạch. */
    private static final Pattern DIGITS = Pattern.compile("\\d(?:[\\s.\\-_/]*\\d){7,}");
    private static final Pattern EMAIL = Pattern.compile("[\\w.+-]+\\s*(@|\\(at\\)|\\[at\\])\\s*[\\w-]+\\s*\\.\\s*[a-z]{2,}");
    private static final Pattern LINK = Pattern.compile("(https?://|www\\.|\\b[\\w-]+\\.(com|vn|net|me|io|org|link)\\b)");
    private static final List<String> APPS = List.of("zalo", "telegram", "viber", "whatsapp", "messenger", "facebook",
            "fb.com", "momo", "zalopay", "skype", "wechat", "instagram");

    private ContactGuard() {
    }

    /** EN: Null when the message is fine, else what it contains. / VI: Null khi tin nhắn hợp lệ, ngược lại là thứ nó chứa. */
    static String findContact(String body) {
        String plain = Normalizer.normalize(body, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .replace('đ', 'd').replace('Đ', 'D').toLowerCase(Locale.ROOT);
        if (DIGITS.matcher(plain).find()) {
            return "a phone or account number";
        }
        if (EMAIL.matcher(plain).find()) {
            return "an email address";
        }
        if (LINK.matcher(plain).find()) {
            return "a link";
        }
        String squeezed = plain.replaceAll("[^a-z.]", "");
        for (String app : APPS) {
            if (squeezed.contains(app)) {
                return "another app (" + app + ")";
            }
        }
        return null;
    }
}

package com.nexbid.common.security;

import java.util.Set;
import java.util.UUID;

/**
 * EN: Who the request belongs to, taken from the token. Every module reads it; auth is what produces it.
 * VI: Request này là của ai, lấy từ token. Mọi module đều đọc nó; auth là nơi tạo ra nó.
 *
 * <p>EN: It lives in common, not auth: auth already depends on user, so putting it there would make a cycle.
 * <p>VI: Nó nằm ở common chứ không phải auth: auth vốn đã phụ thuộc user, đặt ở đó sẽ tạo vòng lặp.
 *
 * <p>EN: Roles are plain strings here so common stays independent of the user module's enum.
 * <p>VI: Roles để dạng chuỗi, để common không phụ thuộc vào enum của module user.
 */
public record CurrentUser(UUID id, String email, Set<String> roles) {

    public boolean hasRole(String role) {
        return roles.contains(role);
    }
}

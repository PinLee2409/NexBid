package com.nexbid.user;

/**
 * EN: Account status (spec §5). BLOCKED users keep their history but cannot act.
 * VI: Trạng thái tài khoản (spec §5). Người bị BLOCKED vẫn giữ lịch sử nhưng không thao tác được.
 */
public enum UserStatus {
    ACTIVE,
    BLOCKED
}

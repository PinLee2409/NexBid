package com.nexbid.user;

import java.util.List;

/**
 * EN: One page of accounts for the admin console, in the same shape as every other paged list.
 * VI: Một trang tài khoản cho trang quản trị, cùng dạng với mọi danh sách phân trang khác.
 */
public record UserPage(List<UserAccount> items, int page, int pageSize, long totalItems, int totalPages) {
}

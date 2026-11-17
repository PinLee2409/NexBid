package com.nexbid.user;

/**
 * EN: The three roles in spec §4. One user may hold several — a seller also buys.
 * VI: Ba vai trò ở spec §4. Một người có thể giữ nhiều vai — người bán cũng đi mua.
 */
public enum RoleName {
    BUYER,
    SELLER,
    ADMIN,
    /** EN: Reviews lots, reports, shill suspects and seller requests. / VI: Duyệt lô, báo cáo, nghi vấn tự trả giá và yêu cầu bán hàng. */
    MODERATOR,
    /** EN: Payouts, refunds, finance reports, stray charges and vouchers. / VI: Chi trả, hoàn tiền, báo cáo tài chính, khoản thu thừa và voucher. */
    FINANCE,
    /** EN: Orders, disputes, emails and accounts. / VI: Đơn hàng, khiếu nại, email và tài khoản. */
    SUPPORT;

    /** EN: The roles an admin hands out to staff. / VI: Các vai trò admin cấp cho nhân viên. */
    public static final java.util.Set<RoleName> STAFF = java.util.EnumSet.of(MODERATOR, FINANCE, SUPPORT);
}

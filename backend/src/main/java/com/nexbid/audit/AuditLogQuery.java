package com.nexbid.audit;

/** EN: Bounded, one-based audit pages. / VI: Trang audit đánh số từ một và có giới hạn kích thước. */
public record AuditLogQuery(Integer page, Integer size) {

    public AuditLogQuery {
        page = page == null || page < 1 ? 1 : page;
        size = size == null || size < 1 ? 50 : Math.min(size, 100);
    }

    public long offset() {
        return (long) (page - 1) * size;
    }
}

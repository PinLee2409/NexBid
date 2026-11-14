package com.nexbid.common;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Locale;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.nexbid.common.exception.BusinessException;
import com.nexbid.common.exception.ErrorCode;

/**
 * EN: Every bank reference an admin records — order refunds, extra VNPay charges sent back, seller payouts — is used
 *     once. A reference typed twice is almost always the same transfer recorded twice, so it is refused. Compared
 *     without case or spaces. Joins the caller's transaction, so a refused or failed recording claims nothing.
 * VI: Mọi mã giao dịch ngân hàng admin ghi — hoàn tiền đơn, trả lại khoản VNPay thu thừa, chi trả người bán — chỉ dùng
 *     một lần. Mã nhập hai lần gần như luôn là cùng một lần chuyển bị ghi hai lần, nên bị từ chối. So sánh không phân biệt
 *     hoa thường và khoảng trắng. Tham gia transaction của nơi gọi, nên lần ghi bị từ chối hoặc lỗi không giữ mã nào.
 */
@Component
public class BankReferences {

    private final JdbcTemplate jdbc;

    BankReferences(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void claim(String reference, String purpose) {
        int inserted = jdbc.update("""
                INSERT INTO bank_references (reference, purpose, created_at) VALUES (?, ?, ?)
                ON CONFLICT (reference) DO NOTHING
                """, normalized(reference), purpose, Instant.now().atOffset(ZoneOffset.UTC));
        if (inserted == 0) {
            throw new BusinessException(ErrorCode.REFERENCE_USED,
                    "The bank reference " + reference.trim() + " was already recorded for another transfer");
        }
    }

    static String normalized(String reference) {
        return reference.replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
    }
}

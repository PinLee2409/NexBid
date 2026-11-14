package com.nexbid.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * EN: Where NexBid sends someone money: a seller's payouts, a buyer's refunds. Also the request body of
 *     PUT /api/users/me/bank-account. The holder's name is matched against the bank's records by the admin.
 * VI: Nơi NexBid chuyển tiền cho một người: tiền bán hàng của người bán, tiền hoàn của người mua. Cũng là body của
 *     PUT /api/users/me/bank-account. Tên chủ tài khoản được admin đối chiếu với thông tin ngân hàng.
 */
public record BankAccount(

        @NotBlank(message = "Bank is required")
        @Size(max = 120, message = "Bank must be at most 120 characters")
        String bankName,

        @NotBlank(message = "Account number is required")
        @Pattern(regexp = "[0-9 ]{6,30}", message = "An account number has 6 to 30 digits")
        String accountNumber,

        @NotBlank(message = "Account holder is required")
        @Size(max = 120, message = "Account holder must be at most 120 characters")
        String accountHolder) {

    /** EN: Trimmed, digits only, holder in capitals as banks print it. / VI: Đã cắt khoảng trắng, chỉ còn số, tên chủ in hoa như ngân hàng in. */
    BankAccount cleaned() {
        return new BankAccount(bankName.trim(), accountNumber.replaceAll("\\s", ""), accountHolder.trim().toUpperCase());
    }

    /** EN: "Vietcombank ••••1234": enough to tell accounts apart, not enough to use one. / VI: "Vietcombank ••••1234": đủ để phân biệt, không đủ để dùng. */
    public String masked() {
        return bankName + " ••••" + accountNumber.substring(Math.max(0, accountNumber.length() - 4));
    }
}

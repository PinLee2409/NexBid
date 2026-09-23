package com.nexbid.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * EN: A role as a row, not just an enum — spec §23 gives roles their own table.
 * VI: Vai trò là một dòng dữ liệu, không chỉ là enum — spec §23 cho roles bảng riêng.
 */
@Entity
@Table(name = "roles")
public class Role {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    // EN: Stored as text, so the database stays readable and the order of the enum never matters.
    // VI: Lưu dạng chữ, để đọc thẳng trong DB được và không phụ thuộc thứ tự khai báo enum.
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, unique = true, length = 32)
    private RoleName name;

    protected Role() {
        // EN: Required by JPA. / VI: JPA bắt buộc phải có.
    }

    public Role(RoleName name) {
        this.name = name;
    }

    public Integer getId() {
        return id;
    }

    public RoleName getName() {
        return name;
    }
}

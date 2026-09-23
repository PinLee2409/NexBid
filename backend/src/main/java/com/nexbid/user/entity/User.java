package com.nexbid.user.entity;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

/**
 * EN: An account (spec §23). Fields only — registration and login rules arrive at functions 04 and 05.
 * VI: Một tài khoản (spec §23). Mới chỉ có field — luật đăng ký và đăng nhập nằm ở chức năng 04 và 05.
 */
@Entity
@Table(name = "users")
public class User {

    // EN: UUID, not a sequence — the client contract types every id as a string, and ids do not leak user counts.
    // VI: Dùng UUID thay vì số tăng dần — hợp đồng với client khai id là chuỗi, và id không để lộ số lượng user.
    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "full_name", nullable = false, length = 120)
    private String fullName;

    @Column(nullable = false, unique = true, length = 254)
    private String email;

    // EN: A BCrypt hash, never a password. Nothing here ever stores the plain text.
    // VI: Lưu hash BCrypt, không bao giờ lưu mật khẩu. Chỗ này không bao giờ chứa chuỗi gốc.
    @Column(nullable = false, length = 100)
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private UserStatus status = UserStatus.ACTIVE;

    // EN: EAGER because authorisation needs the roles on every authenticated request anyway.
    // VI: EAGER vì mỗi request đã đăng nhập đều cần roles để phân quyền.
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "user_roles",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id"))
    private Set<Role> roles = new LinkedHashSet<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected User() {
        // EN: Required by JPA. / VI: JPA bắt buộc phải có.
    }

    public User(String fullName, String email, String password) {
        this.fullName = fullName;
        this.email = email;
        this.password = password;
    }

    // EN: Timestamps are set here, so no caller can forget them or set them wrong.
    // VI: Mốc thời gian set ở đây, để không ai quên hoặc set sai.
    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public void addRole(Role role) {
        this.roles.add(role);
    }

    public boolean isBlocked() {
        return this.status == UserStatus.BLOCKED;
    }

    public UUID getId() {
        return id;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getEmail() {
        return email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public UserStatus getStatus() {
        return status;
    }

    public void setStatus(UserStatus status) {
        this.status = status;
    }

    public Set<Role> getRoles() {
        return Set.copyOf(roles);
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}

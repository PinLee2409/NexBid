package com.nexbid.user;

import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.nexbid.common.exception.ErrorCode;
import com.nexbid.common.exception.ResourceNotFoundException;
import org.springframework.transaction.annotation.Transactional;

import com.nexbid.user.entity.Role;
import com.nexbid.user.entity.User;
import com.nexbid.user.repository.RoleRepository;
import com.nexbid.user.repository.UserRepository;

/**
 * EN: The public API of the user module. Entities and repositories stay internal — only this crosses the boundary.
 * VI: API công khai của module user. Entity và repository nằm bên trong — chỉ lớp này đi qua ranh giới module.
 */
@Service
public class UserService {

    private final UserRepository users;
    private final RoleRepository roles;

    public UserService(UserRepository users, RoleRepository roles) {
        this.users = users;
        this.roles = roles;
    }

    public boolean emailTaken(String email) {
        return users.existsByEmailIgnoreCase(email);
    }

    public Optional<UserAccount> findByEmail(String email) {
        return users.findByEmailIgnoreCase(email).map(UserService::toAccount);
    }

    /**
     * EN: For authentication only. Everything else must use findByEmail, which cannot expose the hash.
     * VI: Chỉ dùng cho xác thực. Mọi chỗ khác phải dùng findByEmail, vốn không thể lộ hash ra ngoài.
     */
    public Optional<UserCredentials> findCredentialsByEmail(String email) {
        return users.findByEmailIgnoreCase(email).map(user -> new UserCredentials(
                user.getId(),
                user.getFullName(),
                user.getEmail(),
                user.getPassword(),
                user.getStatus(),
                roleNames(user)));
    }

    /**
     * EN: Creates an account with the given role. The caller hashes the password; this module never sees the plain text.
     * VI: Tạo tài khoản với vai trò cho trước. Bên gọi tự hash mật khẩu; module này không bao giờ thấy chuỗi gốc.
     */
    @Transactional
    public UserAccount create(String fullName, String email, String passwordHash, RoleName roleName) {
        Role role = roles.findByName(roleName)
                .orElseThrow(() -> new IllegalStateException("Role missing from database: " + roleName));

        User user = new User(fullName, email, passwordHash);
        user.addRole(role);

        return toAccount(users.save(user));
    }

    public Optional<UserAccount> findById(java.util.UUID id) {
        return users.findById(id).map(UserService::toAccount);
    }

    /**
     * EN: Names for a list of ids, in one query. Screens that show many rows would otherwise ask per row.
     * VI: Lấy tên cho một loạt id trong một truy vấn. Nếu không, màn hình nhiều dòng sẽ hỏi từng dòng một.
     */
    public java.util.Map<java.util.UUID, String> namesOf(java.util.Collection<java.util.UUID> ids) {
        if (ids.isEmpty()) {
            return java.util.Map.of();
        }

        return users.findAllById(ids).stream()
                .collect(Collectors.toMap(User::getId, User::getFullName));
    }

    /**
     * EN: Renames an account. The id comes from the token, never from the request body.
     * VI: Đổi tên tài khoản. Id lấy từ token, không bao giờ lấy từ body của request.
     */
    @Transactional
    public UserAccount updateFullName(java.util.UUID id, String fullName) {
        User user = users.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.USER_NOT_FOUND, "No account with id " + id));

        user.setFullName(fullName.trim());
        return toAccount(users.save(user));
    }

    /**
     * EN: Adds a role to an existing account. Idempotent, so running it twice is harmless.
     * VI: Thêm vai trò cho tài khoản đã có. Gọi nhiều lần vẫn an toàn vì không nhân đôi.
     */
    @Transactional
    public UserAccount grantRole(String email, RoleName roleName) {
        User user = users.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.USER_NOT_FOUND, "No account for " + email));

        Role role = roles.findByName(roleName)
                .orElseThrow(() -> new IllegalStateException("Role missing from database: " + roleName));

        user.addRole(role);
        return toAccount(users.save(user));
    }

    private static UserAccount toAccount(User user) {
        return new UserAccount(
                user.getId(),
                user.getFullName(),
                user.getEmail(),
                roleNames(user),
                user.getStatus(),
                user.getCreatedAt());
    }

    private static java.util.Set<RoleName> roleNames(User user) {
        return user.getRoles().stream().map(Role::getName).collect(Collectors.toUnmodifiableSet());
    }
}

package com.nexbid.user;

import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
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

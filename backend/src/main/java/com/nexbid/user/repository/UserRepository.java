package com.nexbid.user.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.nexbid.user.entity.User;

/**
 * EN: Lookups registration and login need. Email is the login identifier, so it is matched case-insensitively.
 * VI: Các truy vấn cho đăng ký và đăng nhập. Email là định danh đăng nhập nên so khớp không phân biệt hoa thường.
 */
public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);
}

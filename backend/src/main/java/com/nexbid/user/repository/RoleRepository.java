package com.nexbid.user.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.nexbid.user.entity.Role;
import com.nexbid.user.RoleName;

/**
 * EN: The three rows are seeded by migration, so this only ever reads.
 * VI: Ba dòng dữ liệu do migration tạo sẵn, nên chỗ này chỉ đọc.
 */
public interface RoleRepository extends JpaRepository<Role, Integer> {

    Optional<Role> findByName(RoleName name);
}

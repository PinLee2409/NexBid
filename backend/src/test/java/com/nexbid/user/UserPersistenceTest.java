package com.nexbid.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import com.nexbid.user.entity.Role;
import com.nexbid.user.RoleName;
import com.nexbid.user.entity.User;
import com.nexbid.user.UserStatus;
import com.nexbid.user.repository.RoleRepository;
import com.nexbid.user.repository.UserRepository;
import com.nexbid.support.TestInfrastructure;

/**
 * EN: Runs against the real migration, so the entity and the SQL are proven to agree.
 * VI: Chạy trên chính migration thật, nên entity và SQL chắc chắn khớp nhau.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestInfrastructure.class)
class UserPersistenceTest {

    @Autowired
    private UserRepository users;

    @Autowired
    private RoleRepository roles;

    @Test
    void migrationSeedsTheThreeRoles() {
        assertThat(roles.findAll()).hasSize(3);
        assertThat(roles.findByName(RoleName.ADMIN)).isPresent();
    }

    @Test
    void savingSetsTimestampsAndDefaultsToActive() {
        User saved = users.save(new User("Pin Le", "pin@nexbid.com", "{bcrypt}hash"));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isEqualTo(saved.getCreatedAt());
    }

    @Test
    void emailIsUniqueRegardlessOfCase() {
        users.saveAndFlush(new User("Pin Le", "pin@nexbid.com", "hash"));

        // EN: Same address, different capitals — the database must still refuse it.
        // VI: Cùng địa chỉ, khác hoa thường — database vẫn phải từ chối.
        assertThatThrownBy(() ->
                users.saveAndFlush(new User("Someone Else", "PIN@NEXBID.COM", "hash")))
                .isInstanceOf(Exception.class);
    }

    @Test
    void emailLookupIgnoresCase() {
        users.saveAndFlush(new User("Pin Le", "pin@nexbid.com", "hash"));

        assertThat(users.findByEmailIgnoreCase("PIN@nexbid.com")).isPresent();
        assertThat(users.existsByEmailIgnoreCase("pin@NEXBID.com")).isTrue();
    }

    @Test
    void aUserCanHoldSeveralRoles() {
        User user = new User("Pin Le", "seller@nexbid.com", "hash");
        user.addRole(roles.findByName(RoleName.BUYER).orElseThrow());
        user.addRole(roles.findByName(RoleName.SELLER).orElseThrow());

        User saved = users.saveAndFlush(user);

        assertThat(saved.getRoles()).extracting(Role::getName)
                .containsExactlyInAnyOrder(RoleName.BUYER, RoleName.SELLER);
    }
}

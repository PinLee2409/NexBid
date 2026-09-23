package com.nexbid;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

/**
 * EN: Fails the build if one module reaches into another's internals — the boundary is checked, not hoped for.
 * VI: Build đỏ nếu một module thò tay vào ruột module khác — ranh giới được kiểm chứng, không phải trông chờ.
 */
class ModularityTest {

    static final ApplicationModules MODULES = ApplicationModules.of(NexbidApplication.class);

    @Test
    void modulesRespectTheirBoundaries() {
        MODULES.verify();
    }

    /**
     * EN: Writes module diagrams to target/spring-modulith-docs for the README.
     * VI: Sinh sơ đồ module vào target/spring-modulith-docs để đưa vào README.
     */
    @Test
    void writeDocumentation() {
        new Documenter(MODULES).writeDocumentation();
    }
}

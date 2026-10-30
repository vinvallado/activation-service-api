package com.deroyque.activationserviceapi.integration;

import static org.assertj.core.api.Assertions.*;

import com.deroyque.activationserviceapi.entity.ActivationRequestEntity;
import com.deroyque.activationserviceapi.repository.ActivationRequestRepository;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ActivationRequestPersistenceIT {
    @Container static final PostgreSQLContainer<?> postgres=new PostgreSQLContainer<>("postgres:17-alpine");
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",postgres::getJdbcUrl); registry.add("spring.datasource.username",postgres::getUsername);
        registry.add("spring.datasource.password",postgres::getPassword); registry.add("spring.flyway.enabled",() -> true);
    }
    @Autowired ActivationRequestRepository repository;
    @Test void databaseEnforcesGlobalExternalIdUniqueness() {
        var now=Instant.now(); repository.saveAndFlush(new ActivationRequestEntity(UUID.randomUUID(),"EXT-INTEGRATION","client-a","CUST-1","PRODUCT-1",now));
        assertThatThrownBy(() -> repository.saveAndFlush(new ActivationRequestEntity(UUID.randomUUID(),"EXT-INTEGRATION","client-b","CUST-2","PRODUCT-2",now)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}

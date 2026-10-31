package com.deroyque.activationserviceapi.integration;

import static org.assertj.core.api.Assertions.*;

import com.deroyque.activationserviceapi.entity.ActivationRequestEntity;
import com.deroyque.activationserviceapi.entity.ActivationTaskEntity;
import com.deroyque.activationserviceapi.model.ActivationStatus;
import com.deroyque.activationserviceapi.repository.ActivationTaskClaimRepository;
import com.deroyque.activationserviceapi.repository.ActivationTaskClaimRepositoryImpl;
import com.deroyque.activationserviceapi.repository.ActivationTaskRepository;
import com.deroyque.activationserviceapi.repository.ActivationRequestRepository;
import java.time.Instant;
import java.util.UUID;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.context.annotation.Import;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(ActivationTaskClaimRepositoryImpl.class)
class ActivationRequestPersistenceIT {
    @Container static final PostgreSQLContainer<?> postgres=new PostgreSQLContainer<>("postgres:17-alpine");
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",postgres::getJdbcUrl); registry.add("spring.datasource.username",postgres::getUsername);
        registry.add("spring.datasource.password",postgres::getPassword); registry.add("spring.flyway.enabled",() -> true);
    }
    @Autowired ActivationRequestRepository repository;
    @Autowired ActivationTaskRepository tasks;
    @Autowired ActivationTaskClaimRepository claims;
    @Autowired EntityManager entityManager;
    @Test void databaseEnforcesGlobalExternalIdUniqueness() {
        var now=Instant.now(); repository.saveAndFlush(new ActivationRequestEntity(UUID.randomUUID(),"EXT-INTEGRATION","client-a","CUST-1","PRODUCT-1",now));
        assertThatThrownBy(() -> repository.saveAndFlush(new ActivationRequestEntity(UUID.randomUUID(),"EXT-INTEGRATION","client-b","CUST-2","PRODUCT-2",now)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test void claimsReschedulesAndCompletesDurableTaskWithOwnershipToken() {
        var now=Instant.parse("2026-10-30T18:00:00Z");
        var request=repository.saveAndFlush(new ActivationRequestEntity(UUID.randomUUID(),"EXT-TASK","client-a","CUST-1","PRODUCT-1",now));
        tasks.saveAndFlush(new ActivationTaskEntity(UUID.randomUUID(),request,now,"corr-1"));

        var first=claims.claimNext(now,now.plusSeconds(30));
        assertThat(first).isPresent();
        assertThat(claims.reschedule(first.orElseThrow().taskId(),UUID.randomUUID(),now.plusSeconds(1),now,"wrong token")).isFalse();
        assertThat(claims.reschedule(first.orElseThrow().taskId(),first.orElseThrow().claimToken(),now.plusSeconds(1),now,"temporary")).isTrue();

        var second=claims.claimNext(now.plusSeconds(1),now.plusSeconds(31));
        assertThat(second).isPresent();
        assertThat(claims.complete(second.orElseThrow().taskId(),second.orElseThrow().claimToken(),ActivationStatus.COMPLETED,now.plusSeconds(2),null)).isTrue();
        entityManager.clear();
        assertThat(repository.findById(request.getId()).orElseThrow().getStatus()).isEqualTo(ActivationStatus.COMPLETED);
    }
}

package com.deroyque.activationserviceapi.repository;

import com.deroyque.activationserviceapi.entity.ActivationTaskEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
public interface ActivationTaskRepository extends JpaRepository<ActivationTaskEntity, UUID> { }

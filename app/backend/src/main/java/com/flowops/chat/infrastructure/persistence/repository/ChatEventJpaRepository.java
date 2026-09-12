package com.flowops.chat.infrastructure.persistence.repository;

import com.flowops.chat.infrastructure.persistence.entity.ChatEventJpaEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatEventJpaRepository extends JpaRepository<ChatEventJpaEntity, UUID> {}

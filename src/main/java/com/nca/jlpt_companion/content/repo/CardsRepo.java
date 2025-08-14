package com.nca.jlpt_companion.content.repo;

import com.nca.jlpt_companion.content.model.CardEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CardsRepo extends JpaRepository<CardEntity, UUID> {
    boolean existsById(UUID id);
}

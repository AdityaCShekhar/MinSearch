package com.aditya.minsearch.auth.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataUserAccountRepository extends JpaRepository<UserAccountJpaEntity, UUID> {

	Optional<UserAccountJpaEntity> findByEmail(String email);
}

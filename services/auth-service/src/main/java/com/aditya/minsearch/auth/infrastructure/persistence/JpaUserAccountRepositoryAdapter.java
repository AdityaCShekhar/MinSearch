package com.aditya.minsearch.auth.infrastructure.persistence;

import com.aditya.minsearch.auth.domain.UserAccount;
import com.aditya.minsearch.auth.domain.UserAccountRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class JpaUserAccountRepositoryAdapter implements UserAccountRepository {

	private final SpringDataUserAccountRepository repository;

	public JpaUserAccountRepositoryAdapter(SpringDataUserAccountRepository repository) {
		this.repository = repository;
	}

	@Override
	public UserAccount save(UserAccount userAccount) {
		return repository.save(new UserAccountJpaEntity(userAccount)).toDomain();
	}

	@Override
	public Optional<UserAccount> findById(UUID id) {
		return repository.findById(id).map(UserAccountJpaEntity::toDomain);
	}

	@Override
	public Optional<UserAccount> findByNormalizedEmail(String normalizedEmail) {
		return repository.findByEmail(normalizedEmail).map(UserAccountJpaEntity::toDomain);
	}
}

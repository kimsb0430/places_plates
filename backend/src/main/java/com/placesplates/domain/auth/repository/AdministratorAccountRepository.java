package com.placesplates.domain.auth.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.placesplates.domain.auth.entity.AdministratorAccount;

public interface AdministratorAccountRepository extends JpaRepository<AdministratorAccount, UUID> {

	Optional<AdministratorAccount> findByEmailIgnoreCase(String email);
}

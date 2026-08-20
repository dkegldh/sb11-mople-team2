package com.codeit.mople.domain.user.repository;

import com.codeit.mople.domain.user.entity.AuthProvider;
import com.codeit.mople.domain.user.entity.Role;
import com.codeit.mople.domain.user.entity.User;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, UUID>, UserRepositoryCustom {
  Optional<User> findByEmail(String email);
  Optional<User> findByProviderAndProviderId(AuthProvider provider, String providerId);
  boolean existsByEmail(String email);
  boolean existsByEmailAndRole(String email, Role role);
  boolean existsByRole(Role role);
  boolean existsByIdAndLockedTrue(UUID id);
}

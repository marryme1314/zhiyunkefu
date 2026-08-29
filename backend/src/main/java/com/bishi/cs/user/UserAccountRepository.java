package com.bishi.cs.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {
    Optional<UserAccount> findByEmail(String email);

    Optional<UserAccount> findByPhone(String phone);

    List<UserAccount> findAllByOrderByCreatedAtDesc();

    long countByRoleIgnoreCase(String role);
}

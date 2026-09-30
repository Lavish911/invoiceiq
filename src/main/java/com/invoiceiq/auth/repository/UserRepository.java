package com.invoiceiq.auth.repository;

import com.invoiceiq.auth.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByEmailAndTenantId(String email, UUID tenantId);
    
    Optional<User> findByIdAndTenantId(UUID id, UUID tenantId);
    
    // Used by authentication provider across tenants
    java.util.List<User> findByEmail(String email);
}

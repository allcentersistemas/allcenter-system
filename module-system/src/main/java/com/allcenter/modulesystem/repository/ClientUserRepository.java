package com.allcenter.modulesystem.repository;

import com.allcenter.modulesystem.model.ClientUser;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClientUserRepository extends JpaRepository<ClientUser, Long> {

    Optional<ClientUser> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);
}

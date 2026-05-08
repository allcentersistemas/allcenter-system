package com.allcenter.moduleclient.repository;

import com.allcenter.moduleclient.model.ClientUser;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClientUserRepository extends JpaRepository<ClientUser, Long> {

    Optional<ClientUser> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);
}

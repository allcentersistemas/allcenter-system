package com.allcenter.modulesystem.repository;

import com.allcenter.modulesystem.model.ClientUser;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClientUserRepository extends JpaRepository<ClientUser, Long> {

    Optional<ClientUser> findByEmailIgnoreCase(String email);

    Optional<ClientUser> findByUsernameIgnoreCase(String username);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByUsernameIgnoreCase(String username);

    boolean existsByDocumentodeindentificacionIgnoreCase(String documentodeindentificacion);

    boolean existsByRucIgnoreCase(String ruc);
}

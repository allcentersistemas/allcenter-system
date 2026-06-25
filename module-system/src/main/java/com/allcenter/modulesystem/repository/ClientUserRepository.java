package com.allcenter.modulesystem.repository;

import com.allcenter.modulesystem.model.ClientUser;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ClientUserRepository extends JpaRepository<ClientUser, Long> {

    Optional<ClientUser> findByEmailIgnoreCase(String email);

    Optional<ClientUser> findByUsernameIgnoreCase(String username);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByUsernameIgnoreCase(String username);

    boolean existsByDocumentodeindentificacionIgnoreCase(String documentodeindentificacion);

    boolean existsByRucIgnoreCase(String ruc);

    @Query(
            "SELECT COUNT(c) > 0 FROM ClientUser c WHERE LOWER(c.email) = LOWER(:email) AND c.id <> :excludeId")
    boolean existsByEmailIgnoreCaseAndIdNot(@Param("email") String email, @Param("excludeId") Long excludeId);

    @Query(
            "SELECT COUNT(c) > 0 FROM ClientUser c WHERE LOWER(c.username) = LOWER(:username) AND c.id <> :excludeId")
    boolean existsByUsernameIgnoreCaseAndIdNot(
            @Param("username") String username, @Param("excludeId") Long excludeId);

    @Query(
            "SELECT COUNT(c) > 0 FROM ClientUser c WHERE LOWER(c.documentodeindentificacion) = LOWER(:doc) AND c.id <> :excludeId")
    boolean existsByDocumentodeindentificacionIgnoreCaseAndIdNot(
            @Param("doc") String documentodeindentificacion, @Param("excludeId") Long excludeId);

    @Query("SELECT COUNT(c) > 0 FROM ClientUser c WHERE LOWER(c.ruc) = LOWER(:ruc) AND c.id <> :excludeId")
    boolean existsByRucIgnoreCaseAndIdNot(@Param("ruc") String ruc, @Param("excludeId") Long excludeId);
}

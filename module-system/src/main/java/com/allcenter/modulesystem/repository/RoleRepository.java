package com.allcenter.modulesystem.repository;

import com.allcenter.modulesystem.model.Role;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoleRepository extends JpaRepository<Role, Long> {

    Optional<Role> findByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCase(String name);
}

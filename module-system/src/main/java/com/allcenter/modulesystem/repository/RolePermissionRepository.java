package com.allcenter.modulesystem.repository;

import com.allcenter.modulesystem.model.RolePermission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RolePermissionRepository extends JpaRepository<RolePermission, Long> {

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM RolePermission p WHERE p.role.id = :roleId")
    void deleteByRoleId(@Param("roleId") Long roleId);
}

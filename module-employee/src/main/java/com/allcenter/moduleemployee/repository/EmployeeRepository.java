package com.allcenter.moduleemployee.repository;

import com.allcenter.moduleemployee.model.Employee;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EmployeeRepository extends JpaRepository<Employee, Long> {

    @Query("SELECT DISTINCT e FROM Employee e LEFT JOIN FETCH e.roles WHERE LOWER(e.email) = LOWER(:email)")
    Optional<Employee> findByEmailIgnoreCase(@Param("email") String email);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByEmployeeCode(String employeeCode);

    @Query("SELECT DISTINCT e FROM Employee e LEFT JOIN FETCH e.roles WHERE e.externalDirectoryId = :guid")
    Optional<Employee> findByExternalDirectoryId(@Param("guid") String externalDirectoryId);

    @Query("SELECT DISTINCT e FROM Employee e LEFT JOIN FETCH e.roles WHERE LOWER(e.userPrincipalName) = LOWER(:upn)")
    Optional<Employee> findByUserPrincipalNameIgnoreCase(@Param("upn") String userPrincipalName);

    @Query("SELECT DISTINCT e FROM Employee e LEFT JOIN FETCH e.roles WHERE e.id = :id")
    Optional<Employee> findByIdWithRoles(@Param("id") Long id);

    @Query("SELECT DISTINCT e FROM Employee e LEFT JOIN FETCH e.roles")
    List<Employee> findAllWithRoles();

    @Query("SELECT COUNT(DISTINCT e.id) FROM Employee e JOIN e.roles r WHERE r.id = :roleId")
    long countEmployeesWithRole(@Param("roleId") Long roleId);
}

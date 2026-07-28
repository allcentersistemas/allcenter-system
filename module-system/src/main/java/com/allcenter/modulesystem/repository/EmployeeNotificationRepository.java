package com.allcenter.modulesystem.repository;

import com.allcenter.modulesystem.model.EmployeeNotification;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EmployeeNotificationRepository extends JpaRepository<EmployeeNotification, Long> {

    long countByEmployeeIdAndReadAtIsNull(Long employeeId);

    List<EmployeeNotification> findTop30ByEmployeeIdOrderByCreatedAtDesc(Long employeeId);

    List<EmployeeNotification> findByEmployeeIdAndReadAtIsNull(Long employeeId);

    @Query(
            """
            SELECT n FROM EmployeeNotification n
            WHERE n.id = :id AND n.employeeId = :employeeId
            """)
    java.util.Optional<EmployeeNotification> findByIdAndEmployeeId(
            @Param("id") Long id, @Param("employeeId") Long employeeId);
}

package com.allcenter.modulesystem.model;

import com.allcenter.modulesystem.listener.RoleAuditEntityListener;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(name = "roles")
@EntityListeners({AuditingEntityListener.class, RoleAuditEntityListener.class})
@Getter
@Setter
public class Role {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Nombre lógico del rol (ej. USER, ADMIN, HR). Sin prefijo {@code ROLE_}; se añade al mapear a Spring
     * Security.
     */
    @Column(nullable = false, unique = true, length = 64)
    private String name;

    @Column(length = 500)
    private String description;

    @CreatedBy
    @Column(name = "created_by")
    private Long createdBy;

    @LastModifiedBy
    @Column(name = "last_modified_by")
    private Long lastModifiedBy;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}

package com.allcenter.modulesystem.model;

import com.allcenter.modulesystem.listener.EmployeeAuditEntityListener;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(name = "employees")
@EntityListeners({AuditingEntityListener.class, EmployeeAuditEntityListener.class})
@Getter
@Setter
public class Employee {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Código interno único (ej. EMP-2026-00042) */
    @Column(nullable = false, unique = true, name = "employee_code", length = 32)
    private String employeeCode;

    /** Correo corporativo (contacto; el login usa {@link #samAccountName} o {@link #employeeCode}) */
    @Column(nullable = false, unique = true, length = 255)
    private String email;

    /** Hash BCrypt solo para cuentas LOCAL. Cuentas solo-AD pueden dejarse en null (LDAP/SSO). */
    @Column(name = "password_hash")
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(name = "directory_source", nullable = false, length = 32)
    private DirectorySource directorySource = DirectorySource.LOCAL;

    /**
     * objectGUID de AD en forma canónica (UUID string, p. ej. {@code 550e8400-e29b-41d4-a716-446655440000}).
     * Clave estable para upsert en sincronización.
     */
    @Column(name = "external_directory_id", unique = true, length = 64)
    private String externalDirectoryId;

    /** objectSid en formato string (p. ej. {@code S-1-5-21-...}). */
    @Column(name = "security_identifier", length = 128)
    private String securityIdentifier;

    /** Usuario de inicio de sesión (sAMAccountName / logon corto). */
    @Column(name = "sam_account_name", length = 128)
    private String samAccountName;

    /** userPrincipalName en AD (suele ser email-like; puede usarse como login alternativo). */
    @Column(name = "user_principal_name", unique = true, length = 255)
    private String userPrincipalName;

    /** distinguishedName; puede cambiar si el usuario se mueve de OU. */
    @Column(name = "distinguished_name", columnDefinition = "TEXT")
    private String distinguishedName;

    @Column(name = "last_directory_sync_at")
    private LocalDateTime lastDirectorySyncAt;

    @Column(nullable = false, name = "first_name", length = 120)
    private String firstName;

    @Column(name = "second_last_name", length = 120)
    private String secondLastName;

    @Column(nullable = false, name = "last_name", length = 120)
    private String lastName;

    @Column(name = "birth_date")
    private LocalDate birthDate;

    @Enumerated(EnumType.STRING)
    @Column(length = 32)
    private Gender gender;

    @Enumerated(EnumType.STRING)
    @Column(name = "marital_status", length = 32)
    private MaritalStatus maritalStatus;

    /** Opcional hasta que RR.HH. o AD aporten datos (p. ej. sync solo con UPN). */
    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", length = 32)
    private DocumentType documentType;

    @Column(name = "document_number", length = 64)
    private String documentNumber;

    @Column(length = 80)
    private String nationality;

    @Column(name = "phone", length = 40)
    private String phone;

    @Column(name = "mobile_phone", length = 40)
    private String mobilePhone;

    @Column(name = "personal_email", length = 255)
    private String personalEmail;

    @Column(name = "address_line1", length = 255)
    private String addressLine1;

    @Column(name = "address_line2", length = 255)
    private String addressLine2;

    @Column(length = 120)
    private String city;

    @Column(name = "province_or_state", length = 120)
    private String provinceOrState;

    @Column(name = "postal_code", length = 20)
    private String postalCode;

    @Column(length = 80)
    private String country;

    @Column(name = "job_title", length = 160)
    private String jobTitle;

    @Column(length = 120)
    private String department;

    @Column(name = "hire_date", nullable = false)
    private LocalDate hireDate;

    @Column(name = "termination_date")
    private LocalDate terminationDate;

    @Column(name = "probation_end_date")
    private LocalDate probationEndDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "contract_type", nullable = false, length = 32)
    private ContractType contractType = ContractType.INDEFINITE;

    @Column(name = "work_location", length = 160)
    private String workLocation;

    @Column(name = "work_schedule_description", length = 255)
    private String workScheduleDescription;

    @Column(name = "work_hours_per_week")
    private Integer workHoursPerWeek;

    /** NIF/CIF a efectos fiscales (nómina) */
    @Column(name = "tax_id", length = 32)
    private String taxId;

    /** Número de la Seguridad Social */
    @Column(name = "social_security_number", length = 32)
    private String socialSecurityNumber;

    /** Bruto mensual base (opcional; restringir visibilidad en API si aplica) */
    @Column(name = "base_salary_monthly", precision = 14, scale = 2)
    private BigDecimal baseSalaryMonthly;

    @Column(name = "salary_currency", length = 8)
    private String salaryCurrency = "EUR";

    @Column(name = "emergency_contact_name", length = 160)
    private String emergencyContactName;

    @Column(name = "emergency_contact_relation", length = 80)
    private String emergencyContactRelation;

    @Column(name = "emergency_contact_phone", length = 40)
    private String emergencyContactPhone;

    @Column(name = "manager_id")
    private Long managerId;

    @Column(name = "branch_id")
    private Long branchId;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "employee_roles",
            joinColumns = @JoinColumn(name = "employee_id", referencedColumnName = "id"),
            inverseJoinColumns = @JoinColumn(name = "role_id", referencedColumnName = "id"))
    private Set<Role> roles = new HashSet<>();

    @Column(nullable = false)
    private boolean active = true;

    /** true mientras exista un refresh token activo (sesión única). */
    @Column(name = "session_connected", nullable = false)
    private boolean sessionConnected = false;

    @Column(name = "session_client_ip", length = 64)
    private String sessionClientIp;

    @Column(name = "session_client_hostname", length = 255)
    private String sessionClientHostname;

    @Column(name = "session_last_seen_at")
    private Instant sessionLastSeenAt;

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

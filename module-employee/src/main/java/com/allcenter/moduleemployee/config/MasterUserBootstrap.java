package com.allcenter.moduleemployee.config;

import com.allcenter.moduleemployee.model.ContractType;
import com.allcenter.moduleemployee.model.DirectorySource;
import com.allcenter.moduleemployee.model.DocumentType;
import com.allcenter.moduleemployee.model.Employee;
import com.allcenter.moduleemployee.model.Role;
import com.allcenter.moduleemployee.repository.EmployeeRepository;
import com.allcenter.moduleemployee.repository.RoleRepository;
import java.time.LocalDate;
import java.util.HashSet;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Crea un único usuario con rol MASTER cuando la base no tiene empleados y {@code
 * app.master-user.bootstrap=true}. Sirve para la primera instalación: crear roles y demás usuarios
 * antes de desactivar el flag y cambiar la contraseña.
 */
@Component
@ConditionalOnProperty(name = "app.master-user.bootstrap", havingValue = "true")
@Order(2)
@RequiredArgsConstructor
@Slf4j
public class MasterUserBootstrap implements ApplicationRunner {

    private final MasterUserProperties properties;
    private final EmployeeRepository employeeRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(ApplicationArguments args) {
        if (employeeRepository.count() > 0) {
            return;
        }
        Employee master = new Employee();
        master.setEmployeeCode("EMP-MASTER-001");
        master.setEmail(properties.email().trim().toLowerCase());
        master.setDirectorySource(DirectorySource.LOCAL);
        master.setPassword(passwordEncoder.encode(properties.password()));
        master.setFirstName("Master");
        master.setLastName("System");
        master.setDocumentType(DocumentType.DNI);
        master.setDocumentNumber("SYS-MASTER-001");
        master.setHireDate(LocalDate.now());
        master.setContractType(ContractType.INDEFINITE);
        master.setJobTitle("System master");
        master.setDepartment("System");
        master.setWorkHoursPerWeek(40);
        Role masterRole =
                roleRepository
                        .findByNameIgnoreCase("MASTER")
                        .orElseThrow(() -> new IllegalStateException("Role MASTER must exist (RoleBootstrap)"));
        HashSet<Role> roles = new HashSet<>();
        roles.add(masterRole);
        master.setRoles(roles);
        master.setActive(true);
        employeeRepository.save(master);
        log.warn(
                "Usuario MASTER creado: {} — desactive app.master-user.bootstrap y cambie la contraseña tras el primer acceso",
                master.getEmail());
    }
}

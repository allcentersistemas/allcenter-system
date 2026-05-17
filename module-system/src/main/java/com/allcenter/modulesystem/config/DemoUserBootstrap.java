package com.allcenter.modulesystem.config;

import com.allcenter.modulesystem.model.ContractType;
import com.allcenter.modulesystem.model.DirectorySource;
import com.allcenter.modulesystem.model.DocumentType;
import com.allcenter.modulesystem.model.Employee;
import com.allcenter.modulesystem.model.Role;
import com.allcenter.modulesystem.repository.EmployeeRepository;
import com.allcenter.modulesystem.repository.RoleRepository;
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

@Component
@ConditionalOnProperty(name = "app.bootstrap-demo-user", havingValue = "true")
@Order(3)
@RequiredArgsConstructor
@Slf4j
public class DemoUserBootstrap implements ApplicationRunner {

    private final EmployeeRepository employeeRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(ApplicationArguments args) {
        if (employeeRepository.count() > 0) {
            return;
        }
        Employee admin = new Employee();
        admin.setEmployeeCode("EMP-DEMO-ADMIN");
        admin.setSamAccountName("admin");
        admin.setEmail("admin@allcenter.local");
        admin.setDirectorySource(DirectorySource.LOCAL);
        admin.setPassword(passwordEncoder.encode("changeMe"));
        admin.setFirstName("Admin");
        admin.setLastName("User");
        admin.setDocumentType(DocumentType.DNI);
        admin.setDocumentNumber("DEMO-000000-A");
        admin.setHireDate(LocalDate.now().minusYears(1));
        admin.setContractType(ContractType.INDEFINITE);
        admin.setJobTitle("System Administrator");
        admin.setDepartment("IT");
        admin.setWorkHoursPerWeek(40);
        Role adminRole =
                roleRepository
                        .findByNameIgnoreCase("ADMIN")
                        .orElseThrow(() -> new IllegalStateException("Role ADMIN must exist (RoleBootstrap)"));
        HashSet<Role> roles = new HashSet<>();
        roles.add(adminRole);
        admin.setRoles(roles);
        admin.setActive(true);
        employeeRepository.save(admin);
        log.warn(
                "Demo admin created: usuario admin / changeMe — disable app.bootstrap-demo-user after first login");
    }
}

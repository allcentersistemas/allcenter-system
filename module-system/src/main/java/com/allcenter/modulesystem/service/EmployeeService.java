package com.allcenter.modulesystem.service;

import com.allcenter.modulesystem.exception.BadRequestException;
import com.allcenter.modulesystem.exception.ConflictException;
import com.allcenter.modulesystem.exception.NotFoundException;
import com.allcenter.modulesystem.model.ContractType;
import com.allcenter.modulesystem.model.DirectorySource;
import com.allcenter.modulesystem.model.Employee;
import com.allcenter.modulesystem.model.Role;
import com.allcenter.modulesystem.dto.AdminCreateEmployeeRequest;
import com.allcenter.modulesystem.dto.EmployeeAdminPatchRequest;
import com.allcenter.modulesystem.dto.EmployeeResponse;
import com.allcenter.modulesystem.dto.EmployeeCatalogItem;
import com.allcenter.modulesystem.dto.EmployeeSelfPatchRequest;
import com.allcenter.modulesystem.repository.EmployeeRepository;
import com.allcenter.modulesystem.repository.RoleRepository;
import java.time.LocalDate;
import java.time.Year;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class EmployeeService {

    private final EmployeeRepository employeeRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final MailService mailService;

    @Transactional(readOnly = true)
    public EmployeeResponse getById(Long id) {
        return employeeRepository
                .findByIdWithRoles(id)
                .map(EmployeeResponse::from)
                .orElseThrow(() -> new NotFoundException("No existe un empleado con id " + id));
    }

    @Transactional(readOnly = true)
    public List<EmployeeResponse> findAll(boolean activeOnly, String search) {
        String q = search == null ? "" : search.trim();
        List<Employee> rows =
                q.isEmpty()
                        ? employeeRepository.findAllWithRolesActiveOnly(activeOnly)
                        : employeeRepository.searchWithRoles(activeOnly, q);
        return rows.stream().map(EmployeeResponse::from).toList();
    }

    /** Empleados activos con un rol dado (p. ej. CHOFER), para desplegables en apps de campo. */
    @Transactional(readOnly = true)
    public List<EmployeeCatalogItem> listActiveCatalogByRole(String roleName) {
        if (roleName == null || roleName.isBlank()) {
            throw new BadRequestException("Nombre de rol obligatorio");
        }
        return employeeRepository.findAllActiveByRoleName(roleName.trim()).stream()
                .map(e -> new EmployeeCatalogItem(e.getId(), e.getEmail(), buildDisplayName(e)))
                .toList();
    }

    private static String buildDisplayName(Employee e) {
        String fn = e.getFirstName() == null ? "" : e.getFirstName().trim();
        String ln = e.getLastName() == null ? "" : e.getLastName().trim();
        String both = (fn + " " + ln).trim();
        if (!both.isEmpty()) {
            return both;
        }
        if (e.getEmail() != null && !e.getEmail().isBlank()) {
            return e.getEmail().trim();
        }
        return "ID " + e.getId();
    }

    @Transactional
    public EmployeeResponse createByAdmin(AdminCreateEmployeeRequest request) {
        String username = request.username().trim();
        if (username.length() < 2) {
            throw new BadRequestException("El usuario debe tener al menos 2 caracteres");
        }
        if (employeeRepository.existsBySamAccountNameIgnoreCase(username)) {
            throw new ConflictException("El usuario \"" + username + "\" ya está en uso");
        }
        String email = request.email().trim().toLowerCase();
        if (employeeRepository.existsByEmailIgnoreCase(email)) {
            throw new ConflictException("El correo " + email + " ya está registrado");
        }
        Employee employee = new Employee();
        employee.setEmployeeCode(generateUniqueEmployeeCode());
        employee.setSamAccountName(username);
        employee.setEmail(email);
        employee.setDirectorySource(DirectorySource.LOCAL);
        employee.setPassword(passwordEncoder.encode(request.password()));
        employee.setFirstName(request.firstName().trim());
        employee.setBranchId(request.branchId());
        employee.setSecondLastName(
                request.secondLastName() != null ? request.secondLastName().trim() : null);
        employee.setLastName(request.lastName().trim());
        employee.setDocumentType(request.documentType());
        employee.setDocumentNumber(request.documentNumber().trim());
        employee.setMobilePhone(
                request.mobilePhone() != null ? request.mobilePhone().trim() : null);
        employee.setBirthDate(request.birthDate());
        employee.setGender(request.gender());
        employee.setHireDate(LocalDate.now());
        employee.setContractType(ContractType.INDEFINITE);
        employee.setJobTitle("Pendiente asignación");
        employee.setDepartment("General");
        employee.setWorkHoursPerWeek(40);
        employee.setActive(true);
        assignRoles(employee, request.roleIds());
        employeeRepository.save(employee);
        return EmployeeResponse.from(
                employeeRepository.findByIdWithRoles(employee.getId()).orElseThrow());
    }

    @Transactional
    public EmployeeResponse patchSelf(Long employeeId, EmployeeSelfPatchRequest req) {
        Employee e =
                employeeRepository
                        .findByIdWithRoles(employeeId)
                        .orElseThrow(() -> new NotFoundException("No existe un empleado con id " + employeeId));
        if (req.firstName() != null) {
            e.setFirstName(req.firstName().trim());
        }
        if (req.secondLastName() != null) {
            e.setSecondLastName(req.secondLastName().trim().isEmpty() ? null : req.secondLastName().trim());
        }
        if (req.lastName() != null) {
            e.setLastName(req.lastName().trim());
        }
        if (req.phone() != null) {
            e.setPhone(req.phone().trim().isEmpty() ? null : req.phone().trim());
        }
        if (req.mobilePhone() != null) {
            e.setMobilePhone(req.mobilePhone().trim().isEmpty() ? null : req.mobilePhone().trim());
        }
        if (req.personalEmail() != null) {
            e.setPersonalEmail(req.personalEmail().trim().isEmpty() ? null : req.personalEmail().trim());
        }
        if (req.addressLine1() != null) {
            e.setAddressLine1(req.addressLine1().trim().isEmpty() ? null : req.addressLine1().trim());
        }
        if (req.addressLine2() != null) {
            e.setAddressLine2(req.addressLine2().trim().isEmpty() ? null : req.addressLine2().trim());
        }
        if (req.city() != null) {
            e.setCity(req.city().trim().isEmpty() ? null : req.city().trim());
        }
        if (req.provinceOrState() != null) {
            e.setProvinceOrState(
                    req.provinceOrState().trim().isEmpty() ? null : req.provinceOrState().trim());
        }
        if (req.postalCode() != null) {
            e.setPostalCode(req.postalCode().trim().isEmpty() ? null : req.postalCode().trim());
        }
        if (req.country() != null) {
            e.setCountry(req.country().trim().isEmpty() ? null : req.country().trim());
        }
        if (req.emergencyContactName() != null) {
            e.setEmergencyContactName(
                    req.emergencyContactName().trim().isEmpty() ? null : req.emergencyContactName().trim());
        }
        if (req.emergencyContactRelation() != null) {
            e.setEmergencyContactRelation(
                    req.emergencyContactRelation().trim().isEmpty()
                            ? null
                            : req.emergencyContactRelation().trim());
        }
        if (req.emergencyContactPhone() != null) {
            e.setEmergencyContactPhone(
                    req.emergencyContactPhone().trim().isEmpty() ? null : req.emergencyContactPhone().trim());
        }
        if (req.notes() != null) {
            e.setNotes(req.notes().trim().isEmpty() ? null : req.notes().trim());
        }
        employeeRepository.save(e);
        return EmployeeResponse.from(
                employeeRepository.findByIdWithRoles(employeeId).orElseThrow());
    }

    @Transactional
    public EmployeeResponse patchAdmin(Long id, EmployeeAdminPatchRequest req) {
        Employee e =
                employeeRepository
                        .findByIdWithRoles(id)
                        .orElseThrow(() -> new NotFoundException("No existe un empleado con id " + id));
        if (req.email() != null && !req.email().trim().equalsIgnoreCase(e.getEmail())) {
            String ne = req.email().trim().toLowerCase();
            if (employeeRepository.existsByEmailIgnoreCase(ne)) {
                throw new ConflictException("El correo " + ne + " ya está en uso");
            }
            e.setEmail(ne);
        }
        if (req.newPassword() != null && !req.newPassword().isBlank()) {
            e.setPassword(passwordEncoder.encode(req.newPassword()));
        }
        if (req.active() != null) {
            e.setActive(req.active());
        }
        if (req.employeeCode() != null && !req.employeeCode().trim().equals(e.getEmployeeCode())) {
            String code = req.employeeCode().trim();
            if (employeeRepository.existsByEmployeeCode(code)) {
                throw new ConflictException("Ya existe el código de empleado " + code);
            }
            e.setEmployeeCode(code);
        }
        if (req.firstName() != null) {
            e.setFirstName(req.firstName().trim());
        }
        if (req.secondLastName() != null) {
            e.setSecondLastName(
                    req.secondLastName().trim().isEmpty() ? null : req.secondLastName().trim());
        }
        if (req.lastName() != null) {
            e.setLastName(req.lastName().trim());
        }
        if (req.birthDate() != null) {
            e.setBirthDate(req.birthDate());
        }
        if (req.gender() != null) {
            e.setGender(req.gender());
        }
        if (req.maritalStatus() != null) {
            e.setMaritalStatus(req.maritalStatus());
        }
        if (req.documentType() != null) {
            e.setDocumentType(req.documentType());
        }
        if (req.documentNumber() != null) {
            e.setDocumentNumber(req.documentNumber().trim().isEmpty() ? null : req.documentNumber().trim());
        }
        if (req.nationality() != null) {
            e.setNationality(req.nationality().trim().isEmpty() ? null : req.nationality().trim());
        }
        if (req.phone() != null) {
            e.setPhone(req.phone().trim().isEmpty() ? null : req.phone().trim());
        }
        if (req.mobilePhone() != null) {
            e.setMobilePhone(req.mobilePhone().trim().isEmpty() ? null : req.mobilePhone().trim());
        }
        if (req.personalEmail() != null) {
            e.setPersonalEmail(req.personalEmail().trim().isEmpty() ? null : req.personalEmail().trim());
        }
        if (req.addressLine1() != null) {
            e.setAddressLine1(req.addressLine1().trim().isEmpty() ? null : req.addressLine1().trim());
        }
        if (req.addressLine2() != null) {
            e.setAddressLine2(req.addressLine2().trim().isEmpty() ? null : req.addressLine2().trim());
        }
        if (req.city() != null) {
            e.setCity(req.city().trim().isEmpty() ? null : req.city().trim());
        }
        if (req.provinceOrState() != null) {
            e.setProvinceOrState(
                    req.provinceOrState().trim().isEmpty() ? null : req.provinceOrState().trim());
        }
        if (req.postalCode() != null) {
            e.setPostalCode(req.postalCode().trim().isEmpty() ? null : req.postalCode().trim());
        }
        if (req.country() != null) {
            e.setCountry(req.country().trim().isEmpty() ? null : req.country().trim());
        }
        if (req.jobTitle() != null) {
            e.setJobTitle(req.jobTitle().trim().isEmpty() ? null : req.jobTitle().trim());
        }
        if (req.department() != null) {
            e.setDepartment(req.department().trim().isEmpty() ? null : req.department().trim());
        }
        if (req.hireDate() != null) {
            e.setHireDate(req.hireDate());
        }
        if (req.terminationDate() != null) {
            e.setTerminationDate(req.terminationDate());
        }
        if (req.probationEndDate() != null) {
            e.setProbationEndDate(req.probationEndDate());
        }
        if (req.contractType() != null) {
            e.setContractType(req.contractType());
        }
        if (req.workLocation() != null) {
            e.setWorkLocation(req.workLocation().trim().isEmpty() ? null : req.workLocation().trim());
        }
        if (req.workScheduleDescription() != null) {
            e.setWorkScheduleDescription(
                    req.workScheduleDescription().trim().isEmpty()
                            ? null
                            : req.workScheduleDescription().trim());
        }
        if (req.workHoursPerWeek() != null) {
            e.setWorkHoursPerWeek(req.workHoursPerWeek());
        }
        if (req.taxId() != null) {
            e.setTaxId(req.taxId().trim().isEmpty() ? null : req.taxId().trim());
        }
        if (req.socialSecurityNumber() != null) {
            e.setSocialSecurityNumber(
                    req.socialSecurityNumber().trim().isEmpty() ? null : req.socialSecurityNumber().trim());
        }
        if (req.baseSalaryMonthly() != null) {
            e.setBaseSalaryMonthly(req.baseSalaryMonthly());
        }
        if (req.salaryCurrency() != null) {
            e.setSalaryCurrency(req.salaryCurrency().trim().isEmpty() ? "EUR" : req.salaryCurrency().trim());
        }
        if (req.emergencyContactName() != null) {
            e.setEmergencyContactName(
                    req.emergencyContactName().trim().isEmpty() ? null : req.emergencyContactName().trim());
        }
        if (req.emergencyContactRelation() != null) {
            e.setEmergencyContactRelation(
                    req.emergencyContactRelation().trim().isEmpty()
                            ? null
                            : req.emergencyContactRelation().trim());
        }
        if (req.emergencyContactPhone() != null) {
            e.setEmergencyContactPhone(
                    req.emergencyContactPhone().trim().isEmpty() ? null : req.emergencyContactPhone().trim());
        }
        if (req.managerId() != null) {
            e.setManagerId(req.managerId());
        }
        if (req.branchId() != null) {
            e.setBranchId(req.branchId());
        }
        if (req.notes() != null) {
            e.setNotes(req.notes().trim().isEmpty() ? null : req.notes().trim());
        }
        if (req.directorySource() != null) {
            e.setDirectorySource(req.directorySource());
        }
        if (req.externalDirectoryId() != null) {
            e.setExternalDirectoryId(
                    req.externalDirectoryId().trim().isEmpty() ? null : req.externalDirectoryId().trim());
        }
        if (req.securityIdentifier() != null) {
            e.setSecurityIdentifier(
                    req.securityIdentifier().trim().isEmpty() ? null : req.securityIdentifier().trim());
        }
        if (req.samAccountName() != null) {
            e.setSamAccountName(req.samAccountName().trim().isEmpty() ? null : req.samAccountName().trim());
        }
        if (req.userPrincipalName() != null) {
            e.setUserPrincipalName(
                    req.userPrincipalName().trim().isEmpty() ? null : req.userPrincipalName().trim());
        }
        if (req.distinguishedName() != null) {
            e.setDistinguishedName(
                    req.distinguishedName().trim().isEmpty() ? null : req.distinguishedName().trim());
        }
        if (req.lastDirectorySyncAt() != null) {
            e.setLastDirectorySyncAt(req.lastDirectorySyncAt());
        }
        if (req.roleIds() != null) {
            assignRoles(e, req.roleIds());
        }
        employeeRepository.save(e);
        return EmployeeResponse.from(employeeRepository.findByIdWithRoles(id).orElseThrow());
    }

    @Transactional
    public EmployeeResponse replaceRoles(Long id, List<Long> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            throw new BadRequestException("roleIds debe contener al menos un identificador de rol");
        }
        Employee e =
                employeeRepository
                        .findByIdWithRoles(id)
                        .orElseThrow(() -> new NotFoundException("No existe un empleado con id " + id));
        assignRoles(e, roleIds);
        employeeRepository.save(e);
        return EmployeeResponse.from(employeeRepository.findByIdWithRoles(id).orElseThrow());
    }

    @Transactional
    public void softDelete(Long id) {
        Employee e =
                employeeRepository
                        .findById(id)
                        .orElseThrow(() -> new NotFoundException("No existe un empleado con id " + id));
        e.setActive(false);
        employeeRepository.save(e);
    }

    @Transactional
    public void resetPasswordByAdmin(Long id, String newPassword, boolean notifyByEmail) {
        Employee e =
                employeeRepository
                        .findById(id)
                        .orElseThrow(() -> new NotFoundException("No existe un empleado con id " + id));
        if (newPassword == null || newPassword.isBlank()) {
            throw new BadRequestException("La nueva contraseña es obligatoria");
        }
        if (newPassword.length() < 8) {
            throw new BadRequestException("La contraseña debe tener al menos 8 caracteres");
        }
        e.setPassword(passwordEncoder.encode(newPassword.trim()));
        employeeRepository.save(e);
        if (notifyByEmail && e.getEmail() != null && !e.getEmail().isBlank()) {
            mailService.sendHtml(
                    e.getEmail(),
                    "Contraseña restablecida — AllCenter",
                    """
                    <p>Hola %s,</p>
                    <p>Un administrador ha restablecido la contraseña de tu cuenta en AllCenter.</p>
                    <p>Tu nueva contraseña temporal es: <strong>%s</strong></p>
                    <p>Cámbiala en cuanto inicies sesión.</p>
                    """
                            .formatted(
                                    buildDisplayName(e),
                                    escapeHtml(newPassword.trim())));
        }
    }

    private static String escapeHtml(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private void assignRoles(Employee employee, List<Long> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            Role user =
                    roleRepository
                            .findByNameIgnoreCase("USER")
                            .orElseThrow(
                                    () -> new IllegalStateException(
                                            "Rol USER no existe; ejecute RoleBootstrap"));
            employee.setRoles(new HashSet<>(Set.of(user)));
            return;
        }
        List<Role> found = roleRepository.findAllById(roleIds);
        if (found.size() != roleIds.size()) {
            throw new BadRequestException(
                    "Algún id de rol no existe; enviados: " + roleIds.size() + ", encontrados: " + found.size());
        }
        employee.setRoles(new HashSet<>(found));
    }

    /** Genera un código interno único tipo EMP-2026-48291 */
    public String generateUniqueEmployeeCode() {
        int year = Year.now().getValue();
        for (int attempt = 0; attempt < 32; attempt++) {
            String code = String.format("EMP-%d-%05d", year, ThreadLocalRandom.current().nextInt(100_000));
            if (!employeeRepository.existsByEmployeeCode(code)) {
                return code;
            }
        }
        throw new IllegalStateException("Could not generate a unique employee code");
    }
}

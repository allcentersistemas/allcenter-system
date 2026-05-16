package com.allcenter.modulesystem.security;

import com.allcenter.modulesystem.model.Employee;
import com.allcenter.modulesystem.model.Role;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

@RequiredArgsConstructor
@Getter
public class EmployeeUserDetails implements UserDetails {

    private final Employee employee;

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        Set<Role> roles = employee.getRoles();
        if (roles == null || roles.isEmpty()) {
            return List.of(SpringSecurityRoles.asGrantedAuthority("USER"));
        }
        return roles.stream().map(r -> SpringSecurityRoles.asGrantedAuthority(r.getName())).toList();
    }

    @Override
    public String getPassword() {
        return employee.getPassword();
    }

    @Override
    public String getUsername() {
        return employee.getEmail();
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return employee.isActive();
    }
}

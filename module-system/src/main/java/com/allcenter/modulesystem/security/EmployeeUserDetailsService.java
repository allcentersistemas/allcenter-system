package com.allcenter.modulesystem.security;

import com.allcenter.modulesystem.repository.EmployeeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class EmployeeUserDetailsService implements UserDetailsService {

    private final EmployeeRepository employeeRepository;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return employeeRepository
                .findByEmailIgnoreCase(username)
                .or(() -> employeeRepository.findByUserPrincipalNameIgnoreCase(username))
                .map(EmployeeUserDetails::new)
                .orElseThrow(() -> new UsernameNotFoundException("Employee not found: " + username));
    }
}

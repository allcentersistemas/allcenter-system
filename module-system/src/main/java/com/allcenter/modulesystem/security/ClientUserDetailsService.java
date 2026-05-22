package com.allcenter.modulesystem.security;

import com.allcenter.modulesystem.repository.ClientUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ClientUserDetailsService implements UserDetailsService {

    private final ClientUserRepository clientUserRepository;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String login) throws UsernameNotFoundException {
        String key = login == null ? "" : login.trim();
        return clientUserRepository
                .findByEmailIgnoreCase(key)
                .or(() -> clientUserRepository.findByUsernameIgnoreCase(key))
                .map(ClientUserDetails::new)
                .orElseThrow(() -> new UsernameNotFoundException("Client not found: " + key));
    }
}

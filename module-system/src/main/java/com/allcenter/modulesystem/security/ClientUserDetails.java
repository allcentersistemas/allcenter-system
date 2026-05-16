package com.allcenter.modulesystem.security;

import com.allcenter.modulesystem.model.ClientUser;
import java.util.Collection;
import java.util.List;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

@RequiredArgsConstructor
@Getter
public class ClientUserDetails implements UserDetails {

    private final ClientUser clientUser;

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_CLIENT"));
    }

    @Override
    public String getPassword() {
        return clientUser.getPassword();
    }

    @Override
    public String getUsername() {
        return clientUser.getEmail();
    }

    @Override
    public boolean isEnabled() {
        return clientUser.isActive();
    }
}

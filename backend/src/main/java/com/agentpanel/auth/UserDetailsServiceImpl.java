package com.agentpanel.auth;

import com.agentpanel.system.entity.SysUser;
import com.agentpanel.system.repository.SysUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final SysUserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        SysUser user = userRepository.findByUsernameAndDeletedFalse(username)
                .orElseThrow(() -> new UsernameNotFoundException("用户不存在"));
        var authorities = user.getRoles().stream()
                .flatMap(role -> role.getPermissions().stream())
                .map(p -> new SimpleGrantedAuthority(p.getCode()))
                .collect(Collectors.toSet());
        user.getRoles().forEach(role -> authorities.add(new SimpleGrantedAuthority("ROLE_" + role.getCode())));
        return User.builder()
                .username(user.getUsername())
                .password(user.getPassword())
                .disabled(!"enabled".equals(user.getStatus()))
                .authorities(authorities)
                .build();
    }
}

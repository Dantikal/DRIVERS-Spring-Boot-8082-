package com.drivers.shared.util;

import com.drivers.modules.auth.entity.DriverAuth;
import com.drivers.modules.auth.repository.DriverAuthRepository;
import com.drivers.shared.exception.ex.DriverNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class CustomUserDetailsService implements UserDetailsService {

    private final DriverAuthRepository driverAuthRepository;

    @Override
    public UserDetails loadUserByUsername(String phone) throws UsernameNotFoundException {
        DriverAuth driverAuth = driverAuthRepository.findByPhone(phone)
                .orElseThrow(() -> new DriverNotFoundException("Driver with phone: " + phone + " not found"));

        log.debug("Driver authenticated via phone: {}, system assigned ROLE_DRIVER", phone);

        return new User(
                driverAuth.getPhone(),
                driverAuth.getPassword(),
                List.of(new SimpleGrantedAuthority("ROLE_DRIVER"))
        );
    }
}
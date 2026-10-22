package com.mockingbirdbank.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.mockingbirdbank.model.AccountHolder;
import com.mockingbirdbank.model.AppUser;
import com.mockingbirdbank.repository.AppUserRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

@ExtendWith(MockitoExtension.class)
class AppUserDetailsServiceTest {

    @Mock private AppUserRepository appUserRepository;

    private AppUserDetailsService appUserDetailsService;

    @BeforeEach
    void setUp() {
        appUserDetailsService = new AppUserDetailsService(appUserRepository);
    }

    @Test
    void loadUserByUsernameMapsStoredCredentialsWithUserRole() {
        AccountHolder holder = new AccountHolder("Jordan Ellis", "jordan.ellis@example.com");
        AppUser appUser = new AppUser("jordan.ellis", "bcrypt-hash", holder);
        when(appUserRepository.findByUsername("jordan.ellis")).thenReturn(Optional.of(appUser));

        UserDetails details = appUserDetailsService.loadUserByUsername("jordan.ellis");

        assertThat(details.getUsername()).isEqualTo("jordan.ellis");
        assertThat(details.getPassword()).isEqualTo("bcrypt-hash");
        assertThat(details.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_USER");
        assertThat(details.isEnabled()).isTrue();
        assertThat(details.isAccountNonLocked()).isTrue();
    }

    @Test
    void loadUserByUsernameThrowsUsernameNotFoundForUnknownUser() {
        when(appUserRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> appUserDetailsService.loadUserByUsername("ghost"))
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessageContaining("ghost");
    }
}

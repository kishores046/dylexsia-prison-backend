package edu.ai.dyslexiaprisonbackend.security.service;

import edu.ai.dyslexiaprisonbackend.model.user.User;
import edu.ai.dyslexiaprisonbackend.repository.UserRepository;
import edu.ai.dyslexiaprisonbackend.security.domain.MyUserDetails;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;
    @Override
    public @NonNull UserDetails loadUserByUsername(@NonNull String username) throws UsernameNotFoundException {

        Optional<User> user= Optional.ofNullable(userRepository.findByEmail(username).orElseThrow(() -> new UsernameNotFoundException("No user found by the email")));
        return Objects.requireNonNull(user.map(MyUserDetails::new).orElse(null));
    }
}

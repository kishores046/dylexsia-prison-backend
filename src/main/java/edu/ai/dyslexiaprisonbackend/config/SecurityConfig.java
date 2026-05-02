package edu.ai.dyslexiaprisonbackend.config;

import edu.ai.dyslexiaprisonbackend.security.jwt.JwtAuthFilter;
import edu.ai.dyslexiaprisonbackend.security.jwt.RestAuthenticationEntryPoint;
import edu.ai.dyslexiaprisonbackend.security.service.CustomUserDetailsService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.CsrfConfigurer;
import org.springframework.security.config.annotation.web.configurers.FormLoginConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.annotation.AnnotationTemplateExpressionDefaults;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableMethodSecurity
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {


    private final RestAuthenticationEntryPoint restAuthenticationEntryPoint;
    private final CustomUserDetailsService customUserDetailsService;
    private final JwtAuthFilter jwtAuthFilter;

    @Bean
    public SecurityFilterChain studentSecurityFilterChain(HttpSecurity http){

        return http.securityMatcher("/api/student/**")
                .csrf(CsrfConfigurer::disable)
                .formLogin(FormLoginConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                .exceptionHandling(httpSecurityExceptionHandlingConfigurer -> httpSecurityExceptionHandlingConfigurer.authenticationEntryPoint(restAuthenticationEntryPoint))
                .authorizeHttpRequests(auth->
                        auth.requestMatchers("/api/auth/**").permitAll()
                                .requestMatchers("/ws/**").permitAll()
                                .anyRequest().hasRole("STUDENT")
                ).addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class).build();
    }

//    @Bean
//    @Order(3)
//    public SecurityFilterChain parentSecurityFilterChain(HttpSecurity http){
//
//        return http.securityMatcher("/api/parent/**")
//                .csrf(CsrfConfigurer::disable)
//                .formLogin(FormLoginConfigurer::disable)
//                .httpBasic(AbstractHttpConfigurer::disable)
//                .sessionManagement(session ->
//                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
//                )
//                .exceptionHandling(httpSecurityExceptionHandlingConfigurer -> httpSecurityExceptionHandlingConfigurer.authenticationEntryPoint(restAuthenticationEntryPoint))
//                .authorizeHttpRequests(auth->
//                        auth.requestMatchers("/api/auth/**").permitAll()
//                                .anyRequest().hasRole("PARENT")
//                ).addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
//                .build();
//    }
//
//
//
//    @Bean
//    @Order(2)
//    public SecurityFilterChain teacherSecurityFilterChain(HttpSecurity http){
//
//        return http.securityMatcher("/api/teacher/**")
//                .csrf(CsrfConfigurer::disable)
//                .formLogin(FormLoginConfigurer::disable)
//                .httpBasic(AbstractHttpConfigurer::disable)
//                .sessionManagement(session ->
//                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
//                )
//                .exceptionHandling(httpSecurityExceptionHandlingConfigurer -> httpSecurityExceptionHandlingConfigurer.authenticationEntryPoint(restAuthenticationEntryPoint))
//                .authorizeHttpRequests(auth->
//                        auth.requestMatchers("/api/auth/**").permitAll()
//                                .anyRequest().hasRole("TEACHER")
//                ).addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class).build();
//    }
//
//
//    @Bean
//    @Order(1)
//    public SecurityFilterChain adminSecurityFilterChain(HttpSecurity http){
//
//        return http.securityMatcher("/api/admin/**")
//                .csrf(CsrfConfigurer::disable)
//                .formLogin(FormLoginConfigurer::disable)
//                .httpBasic(AbstractHttpConfigurer::disable)
//                .sessionManagement(session ->
//                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
//                )
//                .exceptionHandling(httpSecurityExceptionHandlingConfigurer -> httpSecurityExceptionHandlingConfigurer.authenticationEntryPoint(restAuthenticationEntryPoint))
//                .authorizeHttpRequests(auth-> auth
//                        .requestMatchers("/api/auth/**").permitAll()
//                        .anyRequest().hasRole("ADMIN")
//                ).addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class).build();
//    }



    @Bean
    @Order(0)
    public SecurityFilterChain devTools(HttpSecurity http) throws Exception {
        return http.securityMatcher("/h2-console/**", "/swagger-ui/**", "/api-docs/**")
                .authorizeHttpRequests(auth -> auth.anyRequest().hasRole("ADMIN"))
                .headers(h -> h.frameOptions(HeadersConfigurer.FrameOptionsConfig::disable))
                .csrf(AbstractHttpConfigurer::disable)
                .build();
    }



    @Bean
    public PasswordEncoder passwordEncoder(){
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }


    @Bean
    public RoleHierarchy roleHierarchy(){
        return RoleHierarchyImpl.withDefaultRolePrefix()
                .role("ADMIN").implies("TEACHER","PARENT")
                .role("TEACHER").implies("STUDENT")
                .role("PARENT").implies("STUDENT")
                .build();
    }


    @Bean
    public AnnotationTemplateExpressionDefaults annotationTemplateExpressionDefaults(){
        return new AnnotationTemplateExpressionDefaults();
    }


    @Bean
    public AuthenticationProvider authenticationProvider(){
        DaoAuthenticationProvider authenticationProvider=new DaoAuthenticationProvider(customUserDetailsService);
        authenticationProvider.setPasswordEncoder(passwordEncoder());
        return authenticationProvider;
    }
}

package com.paymentgateway.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paymentgateway.config.PaymentProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfig {

    // La autenticacion la resuelve ApiKeyFilter; Spring Security solo aporta cabeceras y sin sesion
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, PaymentProperties properties, ObjectMapper objectMapper)
            throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a.anyRequest().permitAll())
                .addFilterBefore(new ApiKeyFilter(properties, objectMapper), UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}

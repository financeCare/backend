package com.example.capstone.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;


@Configuration
 @EnableWebSecurity
 public class SecurityConfig {
 
     @Autowired
     private JwtAuthenticationFilter jwtAuthenticationFilter;
 
     @Autowired
     private CustomAuthenticationEntryPoint customAuthenticationEntryPoint;
 
     @Autowired
     private CustomAccessDeniedHandler customAccessDeniedHandler;
 
     @Bean
     public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
         http
                 .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/auth/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/v3/api-docs/**",
                                "/swagger-resources/**",
                                "/webjars/**"
                        ).permitAll()
                        .anyRequest().authenticated()
                )
                 .exceptionHandling(exception -> exception
                         .authenticationEntryPoint(customAuthenticationEntryPoint)
                         .accessDeniedHandler(customAccessDeniedHandler)
                 )
                 .sessionManagement(sess -> sess
                         .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                 )
                 .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
 
         return http.build();
     }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

}

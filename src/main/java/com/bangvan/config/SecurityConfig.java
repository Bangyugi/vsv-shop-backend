package com.bangvan.config;


import com.bangvan.service.impl.CustomUserDetailService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.header.writers.StaticHeadersWriter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;
import java.util.stream.Collectors;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter requestFilter;
    private final CustomUserDetailService customUserDetailService;
    private final JwtAuthenticationEntrypoint jwtAuthEntryPointConfig;
    private final JwtAccessDeniedHandler jwtAccessDeniedHandler;

    private static final String[] PUBLIC_ENDPOINTS = new String[]{
            "/auth/**",
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/verify/**",
            "/api/webhooks/**",
            "/api/payments/vnpay-callback",
            "/api/home",
            "/hello",
            "/api/uploads/**",
            "/ws/**"
    };

    private static final String[] PUBLIC_GET_ENDPOINTS = new String[]{
            "/api/products",
            "/api/products/**",
            "/api/categories",
            "/api/categories/**",
            "/api/reviews*",
            "/api/reviews/**"

    };


    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity httpSecurity) throws Exception {


        RequestMatcher[] publicGetMatchers = List.of(PUBLIC_GET_ENDPOINTS).stream()
                .map(pattern -> new AntPathRequestMatcher(pattern, HttpMethod.GET.name()))
                .toArray(RequestMatcher[]::new);

        httpSecurity

                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .headers(headers -> headers
                        .httpStrictTransportSecurity(hsts -> hsts
                                .includeSubDomains(true)
                                .maxAgeInSeconds(31536000)
                                .preload(true)
                        )
                        .contentTypeOptions(HeadersConfigurer.ContentTypeOptionsConfig::disable)
                        .addHeaderWriter(new StaticHeadersWriter("X-Content-Type-Options", "nosniff"))
                        .frameOptions(HeadersConfigurer.FrameOptionsConfig::deny)
                        .referrerPolicy(referrer -> referrer
                                .policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN)
                        )
                        .contentSecurityPolicy(csp -> csp
                                .policyDirectives("default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; font-src 'self'; connect-src 'self'")
                        )
                )
                .authorizeHttpRequests(request -> request
                        .requestMatchers(PUBLIC_ENDPOINTS).permitAll()
                        .requestMatchers(new OrRequestMatcher(publicGetMatchers)).permitAll()
                        .anyRequest().authenticated())

                .exceptionHandling(exceptionHandling -> exceptionHandling
                        .authenticationEntryPoint(jwtAuthEntryPointConfig)
                        .accessDeniedHandler(jwtAccessDeniedHandler))
                .addFilterBefore(requestFilter, UsernamePasswordAuthenticationFilter.class)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));

        return httpSecurity.build();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception{
        return configuration.getAuthenticationManager();
    }



    @Bean
    public AuthenticationProvider authenticationProvider(){
        DaoAuthenticationProvider authenticationProvider = new DaoAuthenticationProvider();
        authenticationProvider.setPasswordEncoder(passwordEncoder());
        authenticationProvider.setUserDetailsService(customUserDetailService);
        return authenticationProvider;
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        org.springframework.web.cors.CorsConfiguration corsConfiguration = new org.springframework.web.cors.CorsConfiguration();

        corsConfiguration.setAllowedOrigins(List.of("http://localhost:5173", "https://vsv-shop-frontend.vercel.app", "https://vsv-shop.vercel.app"));

        corsConfiguration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));

        corsConfiguration.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept", "X-Requested-With", "Origin"));

        corsConfiguration.setExposedHeaders(List.of("Authorization", "Content-Type"));

        corsConfiguration.setAllowCredentials(true);

        corsConfiguration.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();

        source.registerCorsConfiguration("/**", corsConfiguration);

        return source;
    }
}
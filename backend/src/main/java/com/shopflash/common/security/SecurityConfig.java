package com.shopflash.common.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAccountConverter accounts,
                                            SecurityErrorWriter errors) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .cors(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/api/auth/register", "/api/auth/login").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/health").permitAll()
                        // F3/F4 (module indexing): khách chưa đăng nhập cũng xem, tìm, lọc sản phẩm; chỉ mở GET.
                        .requestMatchers(HttpMethod.GET, "/api/products", "/api/products/**").permitAll()
                        .requestMatchers("/api/auth/me").authenticated()
                        .requestMatchers("/api/admin", "/api/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/cart", "/api/cart/**", "/api/checkout", "/api/checkout/**",
                                "/api/orders", "/api/orders/**").hasRole("CUSTOMER")
                        // F9 (module concurrency): ai cũng xem được chương trình và suất còn lại; giữ suất, đặt đơn cần CUSTOMER.
                        .requestMatchers(HttpMethod.GET, "/api/flash-sales/active", "/api/flash-sales/*/stock").permitAll()
                        .requestMatchers("/api/flash-sales", "/api/flash-sales/**").hasRole("CUSTOMER")
                        .requestMatchers("/api/**").denyAll()
                        .anyRequest().permitAll())
                .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> jwt.jwtAuthenticationConverter(accounts)))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, exception) -> errors.unauthorized(response))
                        .accessDeniedHandler((request, response, exception) -> errors.forbidden(response)));
        return http.build();
    }
}

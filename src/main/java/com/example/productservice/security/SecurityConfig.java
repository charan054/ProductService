package com.example.productservice.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.http.HttpStatus;

/**
 * Product catalog browsing (list/search/lookup) stays public; anything that changes the catalog (add, price,
 * stock, delete) requires a valid X-Service-Key - only trusted backend callers (OrderService) should be able
 * to do that, the same trust boundary Bankapplication and PhonepayService already enforce for their own
 * service-to-service calls.
 */
@Configuration
public class SecurityConfig {

    private final String serviceApiKey;

    public SecurityConfig(@Value("${internal.service.api-key}") String serviceApiKey) {
        this.serviceApiKey = serviceApiKey;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET, "/product/all", "/product/byId", "/product/byName", "/product/byCategory", "/product/search", "/product/lowStock").permitAll()
                        // Reviews are customer-generated content, not a catalog change an admin/backend caller
                        // makes - the same direct-from-customer trust level as the public GET catalog endpoints
                        // above, not the X-Service-Key boundary that guards add/updatePrice/updateStock/delete.
                        .requestMatchers("/product/*/reviews", "/product/*/reviews/*", "/product/*/rating-summary").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                        // A controller-level failure (e.g. a validation error) triggers an internal dispatch to
                        // /error; ServiceKeyAuthenticationFilter doesn't re-run on that dispatch (OncePerRequestFilter
                        // skips ERROR dispatches by default), so without this the real error status gets clobbered
                        // by a spurious 401 from the unauthenticated /error request.
                        .requestMatchers("/error").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(e -> e.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .addFilterBefore(new ServiceKeyAuthenticationFilter(serviceApiKey), UsernamePasswordAuthenticationFilter.class)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable);
        return http.build();
    }
}

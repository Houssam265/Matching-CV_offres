package com.gi3.matchingcv.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {
    @Bean SecurityFilterChain security(HttpSecurity http, ResourceAccess access) throws Exception {
        http.addFilterBefore(new AccountRateLimitFilter(), org.springframework.security.web.csrf.CsrfFilter.class);
        http.authorizeHttpRequests(a->a
                .requestMatchers("/api/auth/csrf", "/api/health").permitAll()
                .requestMatchers(HttpMethod.GET,"/api/auth/check-email").permitAll()
                .requestMatchers(HttpMethod.POST,"/api/auth/connexion","/api/auth/inscription","/api/recruteurs",
                        "/api/auth/password-reset/request","/api/auth/password-reset/confirm","/api/auth/verify-email").permitAll()
                .requestMatchers("/api/**","/ws/**").access((auth,context)-> {
                    var value=auth.get();
                    return new AuthorizationDecision(value!=null && value.getPrincipal() instanceof AccountPrincipal p
                            && access.allowed(p,context.getRequest()));
                }).anyRequest().permitAll())
            .headers(h->h
                // Toutes les ressources (scripts, police, images) sont servies par le site lui-même ; aucun cadre externe.
                .contentSecurityPolicy(c->c.policyDirectives(String.join("; ",
                        "default-src 'self'",
                        "script-src 'self' 'unsafe-inline'",
                        "style-src 'self' 'unsafe-inline'",
                        "font-src 'self' data:",
                        "img-src 'self' data: blob:",
                        "connect-src 'self'",
                        "object-src 'none'",
                        "base-uri 'self'",
                        "form-action 'self'",
                        "frame-ancestors 'none'")))
                .frameOptions(f->f.deny())
                .referrerPolicy(r->r.policy(org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                .addHeaderWriter(new org.springframework.security.web.header.writers.StaticHeadersWriter(
                        "Permissions-Policy","camera=(), microphone=(), geolocation=(), payment=(), usb=()")))
            .csrf(c->c.ignoringRequestMatchers("/ws/**"))
            .requestCache(c->c.disable())
            .exceptionHandling(e->e
                .authenticationEntryPoint((r,s,x)->{s.setStatus(401);s.setContentType("application/json");s.getWriter().write("{\"message\":\"Connexion requise.\"}");})
                .accessDeniedHandler((r,s,x)->{s.setStatus(403);s.setContentType("application/json");s.getWriter().write("{\"message\":\"Accès refusé ou session expirée.\"}");}))
            .logout(l->l.logoutUrl("/api/auth/logout").invalidateHttpSession(true).deleteCookies("JSESSIONID")
                .logoutSuccessHandler((r,s,a)->s.setStatus(204)));
        return http.build();
    }
}

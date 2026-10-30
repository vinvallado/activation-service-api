package com.deroyque.activationserviceapi.security;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class ApiSecurityConfiguration {
    @Bean
    SecurityFilterChain apiSecurity(HttpSecurity http, Converter<Jwt, ? extends org.springframework.security.authentication.AbstractAuthenticationToken> converter) throws Exception {
        return http.csrf(csrf -> csrf.disable()).authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health/**").permitAll()
                .requestMatchers("/actuator/prometheus").hasAuthority("SCOPE_activation:metrics")
                .requestMatchers(org.springframework.http.HttpMethod.POST,"/api/v1/requests").hasAuthority("SCOPE_activation:write")
                .requestMatchers("/api/v1/requests/**").hasAuthority("SCOPE_activation:read")
                .anyRequest().authenticated())
            .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> jwt.jwtAuthenticationConverter(converter))).build();
    }
    @Bean
    JwtDecoder jwtDecoder(@Value("${security.jwt.jwk-set-uri}") String jwkSetUri, @Value("${security.jwt.issuer}") String issuer,
                          @Value("${security.jwt.audience}") String audience) {
        NimbusJwtDecoder decoder=NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
        OAuth2TokenValidator<Jwt> audienceValidator=token -> token.getAudience().contains(audience)
                ? OAuth2TokenValidatorResult.success() : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token","Audiência inválida",null));
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefaultWithIssuer(issuer),audienceValidator)); return decoder;
    }
    @Bean
    Converter<Jwt, ? extends org.springframework.security.authentication.AbstractAuthenticationToken> jwtAuthenticationConverter() {
        var scopes=new JwtGrantedAuthoritiesConverter(); scopes.setAuthorityPrefix("SCOPE_"); scopes.setAuthoritiesClaimName("scope");
        var converter=new JwtAuthenticationConverter(); converter.setJwtGrantedAuthoritiesConverter(scopes); converter.setPrincipalClaimName("client_id"); return converter;
    }
}

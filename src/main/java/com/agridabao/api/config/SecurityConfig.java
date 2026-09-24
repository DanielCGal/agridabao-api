package com.agridabao.api.config;

import com.agridabao.api.security.JwtService;
import com.agridabao.api.user.AppUserRepository;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.OctetSequenceKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.web.SecurityFilterChain;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;
import java.util.UUID;

@Configuration
public class SecurityConfig {
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http,
                                            SecretKey secretKey,
                                            ObjectProvider<AppUserRepository> users) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/**", "/actuator/health", "/actuator/info", "/error")
                        .permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(resourceServer ->
                        resourceServer.jwt(jwt -> jwt.decoder(accessTokenDecoder(secretKey, users))));

        return http.build();
    }

    private static JwtDecoder accessTokenDecoder(SecretKey secretKey,
                                                 ObjectProvider<AppUserRepository> users) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(secretKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();

        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefault(),
                SecurityConfig::requireAccessPurpose,
                jwt -> requireCurrentTokenVersion(jwt, users)));

        return decoder;
    }

    private static OAuth2TokenValidatorResult requireAccessPurpose(Jwt jwt) {
        String purpose = jwt.getClaimAsString(JwtService.PURPOSE_CLAIM);

        if (purpose == null || JwtService.PURPOSE_ACCESS.equals(purpose)) {
            return OAuth2TokenValidatorResult.success();
        }

        return OAuth2TokenValidatorResult.failure(new OAuth2Error(
                "invalid_token", "This token cannot be used to call the API.", null));
    }

    private static OAuth2TokenValidatorResult requireCurrentTokenVersion(
            Jwt jwt, ObjectProvider<AppUserRepository> users) {
        UUID userId;
        try {
            userId = UUID.fromString(jwt.getSubject());
        } catch (IllegalArgumentException | NullPointerException ex) {
            return staleSession();
        }

        AppUserRepository repository = users.getIfAvailable();
        if (repository == null) {
            return OAuth2TokenValidatorResult.success();
        }

        Integer tokenVersion = jwt.getClaim(JwtService.VERSION_CLAIM) instanceof Number version
                ? version.intValue()
                : 0;

        return repository.findById(userId)
                .filter(user -> user.getTokenVersion() == tokenVersion)
                .map(user -> OAuth2TokenValidatorResult.success())
                .orElseGet(SecurityConfig::staleSession);
    }

    private static OAuth2TokenValidatorResult staleSession() {
        return OAuth2TokenValidatorResult.failure(new OAuth2Error(
                "invalid_token", "This session has ended. Please sign in again.", null));
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    SecretKey jwtSecretKey(@Value("${app.jwt.secret-base64}") String secretBase64) {
        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(secretBase64);
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("APP_JWT_SECRET_BASE64 must be valid Base64.", ex);
        }

        if (decoded.length < 32) {
            throw new IllegalStateException("APP_JWT_SECRET_BASE64 must decode to at least 32 bytes.");
        }

        return new SecretKeySpec(decoded, "HmacSHA256");
    }

    @Bean
    JwtEncoder jwtEncoder(SecretKey secretKey) {
        OctetSequenceKey jwk = new OctetSequenceKey.Builder(secretKey.getEncoded())
                .algorithm(JWSAlgorithm.HS256)
                .build();
        JWKSource<SecurityContext> source = new ImmutableJWKSet<>(new JWKSet(jwk));
        return new NimbusJwtEncoder(source);
    }

    @Bean
    JwtDecoder jwtDecoder(SecretKey secretKey) {
        return NimbusJwtDecoder.withSecretKey(secretKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
    }
}

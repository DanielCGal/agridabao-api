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

    /**
     * The decoder the Authorization header is checked with.
     *
     * Deliberately not the {@link JwtDecoder} bean below. The password-reset
     * ticket is signed with the same key as an access token, so a plain decoder
     * would happily accept one as a bearer token and hand a half-finished reset
     * the run of the API. This one additionally insists the token says it is an
     * access token; JwtService keeps the unrestricted decoder so it can still
     * read the ticket on the reset endpoint itself.
     */
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

        // Absent counts as an access token: every token issued before the claim
        // existed lacks it, and they stay valid for their full 30 days.
        if (purpose == null || JwtService.PURPOSE_ACCESS.equals(purpose)) {
            return OAuth2TokenValidatorResult.success();
        }

        return OAuth2TokenValidatorResult.failure(new OAuth2Error(
                "invalid_token", "This token cannot be used to call the API.", null));
    }

    /**
     * Refuses a token that was issued before the account's last password change.
     *
     * This is the one thing a stateless token cannot do on its own. Everything
     * else about a token is settled by its signature and its expiry, but "has
     * this session been revoked since" can only be answered by the account, so
     * this costs one lookup by primary key per authenticated request. That is
     * the price of being able to end a session at all, and it is worth paying:
     * without it, changing a password locks an intruder out of signing in again
     * but leaves the session they already have running for up to thirty days.
     *
     * The repository is resolved lazily. The decoder is built while the security
     * filter chain is being assembled, which is earlier than the JPA layer is
     * ready, and asking for the bean then would fail.
     */
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
            // Nothing to check against. Refusing every request would take the
            // whole API down over a startup ordering problem, which is a far
            // worse failure than one session outliving a password change.
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

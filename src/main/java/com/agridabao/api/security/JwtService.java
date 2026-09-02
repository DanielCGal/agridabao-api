package com.agridabao.api.security;

import com.agridabao.api.error.UnauthorizedException;
import com.agridabao.api.user.AppUser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Service
public class JwtService {
    /**
     * Names what a token may be used for. Access tokens issued before this
     * claim existed carry nothing, which {@link #PURPOSE_ACCESS} stands in for -
     * see AccessTokenPurposeValidator, which is what actually keeps a
     * single-purpose ticket out of the ordinary Authorization header.
     */
    public static final String PURPOSE_CLAIM = "purpose";
    public static final String PURPOSE_ACCESS = "access";
    public static final String PURPOSE_PASSWORD_RESET = "password_reset";

    /**
     * How long the player has to choose a new password after their emailed code
     * checks out. Short on purpose: the ticket is proof of a passed check, so it
     * is worth as much as the code was, and it only has to survive one screen.
     */
    private static final Duration RESET_TICKET_DURATION = Duration.ofMinutes(15);

    private final JwtEncoder jwtEncoder;
    private final JwtDecoder jwtDecoder;
    private final String issuer;
    private final Duration accessTokenDuration;

    public JwtService(JwtEncoder jwtEncoder,
                      JwtDecoder jwtDecoder,
                      @Value("${app.jwt.issuer}") String issuer,
                      @Value("${app.jwt.access-token-minutes}") long accessTokenMinutes) {
        this.jwtEncoder = jwtEncoder;
        this.jwtDecoder = jwtDecoder;
        this.issuer = issuer;
        this.accessTokenDuration = Duration.ofMinutes(accessTokenMinutes);
    }

    public IssuedToken issue(AppUser user) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(accessTokenDuration);

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .issuedAt(now)
                .expiresAt(expiresAt)
                .subject(user.getId().toString())
                .claim("email", user.getEmail())
                .claim(PURPOSE_CLAIM, PURPOSE_ACCESS)
                .build();

        return new IssuedToken(encode(claims), expiresAt);
    }

    /**
     * Proof that this player answered the emailed reset code, handed to them so
     * the next screen can set a new password without asking for the code again.
     *
     * It is signed with the same key as an access token, so the resource server
     * would otherwise accept it as one; the purpose claim above is what stops
     * that, and it is the reason this ticket is safe to hand out at all.
     */
    public String issueResetTicket(AppUser user) {
        Instant now = Instant.now();

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .issuedAt(now)
                .expiresAt(now.plus(RESET_TICKET_DURATION))
                .subject(user.getId().toString())
                .claim(PURPOSE_CLAIM, PURPOSE_PASSWORD_RESET)
                .build();

        return encode(claims);
    }

    /** The account a reset ticket belongs to, or a 401 if it is not one. */
    public UUID readResetTicket(String token) {
        if (token == null || token.isBlank()) {
            throw new UnauthorizedException("Your password reset session has expired. Please start again.");
        }

        Jwt jwt;
        try {
            jwt = jwtDecoder.decode(token);
        } catch (JwtException ex) {
            throw new UnauthorizedException("Your password reset session has expired. Please start again.");
        }

        if (!PURPOSE_PASSWORD_RESET.equals(jwt.getClaimAsString(PURPOSE_CLAIM))) {
            throw new UnauthorizedException("Your password reset session is not valid. Please start again.");
        }

        try {
            return UUID.fromString(jwt.getSubject());
        } catch (IllegalArgumentException ex) {
            throw new UnauthorizedException("Your password reset session is not valid. Please start again.");
        }
    }

    private String encode(JwtClaimsSet claims) {
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    public record IssuedToken(String value, Instant expiresAt) {
    }
}

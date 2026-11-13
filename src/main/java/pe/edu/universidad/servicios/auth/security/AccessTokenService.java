package pe.edu.universidad.servicios.auth.security;

import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import pe.edu.universidad.servicios.shared.config.AppProperties;

/**
 * Emite y verifica el JWT de acceso (HS256). El token referencia la sesión mediante el claim {@code sid}.
 */
@Component
public class AccessTokenService {

    private static final String ISSUER = "gestal-backend";

    private final byte[] secret;
    private final Clock clock;
    private final long ttlSeconds;

    public AccessTokenService(AppProperties properties, Clock clock) {
        String configured = properties.security().jwtSecret();
        if (configured == null || configured.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("APP_JWT_SECRET debe tener al menos 32 bytes.");
        }
        this.secret = configured.getBytes(StandardCharsets.UTF_8);
        this.clock = clock;
        this.ttlSeconds = properties.security().accessTokenTtl().toSeconds();
    }

    public long ttlSeconds() {
        return ttlSeconds;
    }

    public String issue(long userId, long sessionId) {
        Instant now = clock.instant();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(ISSUER)
                .subject(Long.toString(userId))
                .claim("sid", sessionId)
                .jwtID(UUID.randomUUID().toString())
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(ttlSeconds)))
                .build();
        try {
            SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
            jwt.sign(new MACSigner(secret));
            return jwt.serialize();
        } catch (JOSEException ex) {
            throw new IllegalStateException("No se pudo firmar el token de acceso", ex);
        }
    }

    public Optional<AccessTokenClaims> verify(String token) {
        try {
            SignedJWT jwt = SignedJWT.parse(token);
            if (!JWSAlgorithm.HS256.equals(jwt.getHeader().getAlgorithm()) || !jwt.verify(new MACVerifier(secret))) {
                return Optional.empty();
            }
            JWTClaimsSet claims = jwt.getJWTClaimsSet();
            Date exp = claims.getExpirationTime();
            if (!ISSUER.equals(claims.getIssuer()) || exp == null || !exp.toInstant().isAfter(clock.instant())) {
                return Optional.empty();
            }
            Object sid = claims.getClaim("sid");
            if (!(sid instanceof Number number) || claims.getSubject() == null) {
                return Optional.empty();
            }
            return Optional.of(new AccessTokenClaims(Long.parseLong(claims.getSubject()), number.longValue()));
        } catch (ParseException | JOSEException | NumberFormatException ex) {
            return Optional.empty();
        }
    }

    public record AccessTokenClaims(long userId, long sessionId) {
    }
}

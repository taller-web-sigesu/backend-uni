package pe.edu.universidad.servicios.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

import pe.edu.universidad.servicios.support.IntegrationTest;

/**
 * HU-27: el auto-registro está limitado por IP. Contexto propio con un límite bajo para no interferir con otras clases.
 */
@TestPropertySource(properties = "app.registration.max-per-ip-per-hour=3")
class RegistrationRateLimitIT extends IntegrationTest {

    @Test
    void registroLimitadoPorIp() {
        for (int i = 0; i < 3; i++) {
            assertThat(api.post("/api/v1/auth/register", null, RegistrationIT.body(RegistrationIT.institutionalEmail())).status())
                    .isEqualTo(201);
        }
        assertThat(api.post("/api/v1/auth/register", null, RegistrationIT.body(RegistrationIT.institutionalEmail())).status())
                .isEqualTo(429);
    }
}

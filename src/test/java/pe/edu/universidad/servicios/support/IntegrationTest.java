package pe.edu.universidad.servicios.support;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.mysql.MySQLContainer;

import tools.jackson.databind.json.JsonMapper;

/**
 * Base de pruebas de integración: la aplicación completa en un puerto aleatorio contra MySQL real
 * (misma versión que Compose) en un contenedor compartido por todas las clases.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(TestClockConfiguration.class)
public abstract class IntegrationTest {

    static final MySQLContainer MYSQL = new MySQLContainer(System.getProperty("mysql.image", "mysql:8.4.13"))
            .withDatabaseName("uni_services")
            .withUsername("uni_app")
            .withPassword("uni_test_app")
            .withReuse(false);

    static {
        MYSQL.start();
    }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> MYSQL.getJdbcUrl() + "?allowPublicKeyRetrieval=true&sslMode=DISABLED");
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }

    @Autowired
    protected Environment environment;

    @Autowired
    protected JsonMapper jsonMapper;

    @Autowired
    protected TestData data;

    @Autowired
    protected MutableClock clock;

    protected Api api;

    @BeforeEach
    void setUpApi() {
        clock.reset();
        api = new Api("http://localhost:" + environment.getProperty("local.server.port"), jsonMapper);
    }
}

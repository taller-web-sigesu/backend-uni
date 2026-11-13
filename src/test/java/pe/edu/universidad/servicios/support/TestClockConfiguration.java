package pe.edu.universidad.servicios.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration(proxyBeanMethods = false)
public class TestClockConfiguration {

    @Bean
    @Primary
    MutableClock testClock() {
        return new MutableClock();
    }

    @Bean
    TestData testData(org.springframework.jdbc.core.simple.JdbcClient jdbc,
            org.springframework.security.crypto.password.PasswordEncoder encoder) {
        return new TestData(jdbc, encoder);
    }
}

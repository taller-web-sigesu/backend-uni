package pe.edu.universidad.servicios.shared.config;

import java.util.List;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import pe.edu.universidad.servicios.shared.security.CurrentActorArgumentResolver;
import pe.edu.universidad.servicios.shared.web.CorrelationIdFilter;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AppProperties.class)
public class WebConfiguration implements WebMvcConfigurer {

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new CurrentActorArgumentResolver());
    }

    /** Se registra antes de la cadena de seguridad para que también los 401 lleven correlación. */
    @Bean
    FilterRegistrationBean<CorrelationIdFilter> correlationIdFilter() {
        FilterRegistrationBean<CorrelationIdFilter> registration = new FilterRegistrationBean<>(new CorrelationIdFilter());
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }
}

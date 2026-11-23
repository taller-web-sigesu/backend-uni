package pe.edu.universidad.servicios;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import com.sun.net.httpserver.HttpServer;
import io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceRequest;
import io.opentelemetry.proto.trace.v1.Span;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import pe.edu.universidad.servicios.status.StatusController;
import pe.edu.universidad.servicios.support.Api.Response;
import pe.edu.universidad.servicios.support.IntegrationTest;
import tools.jackson.databind.JsonNode;

@ExtendWith(OutputCaptureExtension.class)
@DirtiesContext
class TelemetryIT extends IntegrationTest {

    private static final String COLLECTOR_PASSWORD = UUID.randomUUID().toString();
    private static final String PRIVATE_MARKER = "dato-ficticio-que-no-se-exporta";
    private static final BlockingQueue<Span> SPANS = new LinkedBlockingQueue<>();
    private static final HttpServer COLLECTOR = startCollector();

    @MockitoSpyBean
    StatusController statusController;

    @DynamicPropertySource
    static void telemetry(DynamicPropertyRegistry registry) {
        String hash = new BCryptPasswordEncoder().encode(COLLECTOR_PASSWORD);
        registry.add("app.telemetry.password-hash", () -> hash);
        registry.add("management.tracing.sampling.probability", () -> "1.0");
        registry.add("management.tracing.export.otlp.enabled", () -> "true");
        registry.add("management.opentelemetry.tracing.export.schedule-delay", () -> "100ms");
        registry.add("management.opentelemetry.tracing.export.otlp.endpoint",
                () -> "http://127.0.0.1:" + COLLECTOR.getAddress().getPort() + "/v1/traces");
    }

    @AfterAll
    static void stopCollector() {
        COLLECTOR.stop(0);
    }

    @Test
    void healthEsPublicoYLaTelemetriaSoloAceptaLaCuentaRecolectora() {
        Response health = api.get("/actuator/health", null);
        assertThat(health.status()).isEqualTo(200);
        assertThat(health.text("status")).isEqualTo("UP");
        assertThat(health.json().has("details")).isFalse();
        assertThat(health.json().has("components")).isFalse();
        assertThat(health.header("X-Correlation-ID")).isPresent();

        String admin = api.token(data.user("ADMINISTRADOR", null));
        String student = api.token(data.user("ESTUDIANTE", null));
        for (String path : new String[] {"/actuator/prometheus", "/actuator/info", "/actuator/metrics",
                "/actuator/metrics/jvm.memory.used"}) {
            Response anonymous = api.get(path, null);
            assertThat(anonymous.status()).isEqualTo(401);
            assertThat(anonymous.header("WWW-Authenticate").orElseThrow()).startsWith("Basic");
            assertThat(anonymous.header("X-Correlation-ID")).isPresent();
            assertThat(api.get(path, admin).status()).isEqualTo(401);
            assertThat(api.get(path, student).status()).isEqualTo(401);
            assertThat(withBasic("GET", path, "clave-incorrecta").status()).isEqualTo(401);
            assertThat(withBasic("GET", path, COLLECTOR_PASSWORD).status()).isEqualTo(200);
        }
        Response write = withBasic("POST", "/actuator/prometheus", COLLECTOR_PASSWORD);
        assertThat(write.status()).isEqualTo(403);
        assertThat(write.headers().allValues("Set-Cookie"))
                .anyMatch(cookie -> cookie.startsWith("XSRF-TOKEN=") && cookie.contains("Path=/actuator"))
                .noneMatch(cookie -> cookie.startsWith("JSESSIONID="));
        assertThat(withBasic("GET", "/actuator/env", COLLECTOR_PASSWORD).status()).isEqualTo(403);
        assertThat(withBasic("GET", "/actuator", COLLECTOR_PASSWORD).status()).isEqualTo(403);
        assertThat(withBasic("HEAD", "/actuator/prometheus", COLLECTOR_PASSWORD).status()).isEqualTo(403);
        assertThat(withBasic("OPTIONS", "/actuator/prometheus", COLLECTOR_PASSWORD).status()).isEqualTo(403);
        assertThat(withBasic("GET", "/api/v1/requests", COLLECTOR_PASSWORD).status()).isEqualTo(401);
    }

    @Test
    void scrapeIncluyeHistogramaCincoXXJvmPoolYResultadosDeNegocio(CapturedOutput output) {
        String correo = "ficticio-" + UUID.randomUUID() + "@universidad.example";
        for (int i = 0; i < 5; i++) {
            assertThat(api.login(correo, PRIVATE_MARKER).status()).isEqualTo(401);
        }
        assertThat(api.login(correo, PRIVATE_MARKER).status()).isEqualTo(429);
        doThrow(new IllegalStateException(PRIVATE_MARKER)).when(statusController).status();
        assertThat(api.get("/api/v1/status", null).status()).isEqualTo(500);
        String student = api.token(data.user("ESTUDIANTE", null));
        long category = data.category(data.area(), 2);
        assertThat(api.post("/api/v1/requests", student, Map.of("categoriaId", Long.toString(category),
                "ubicacion", "Sala ficticia", "descripcion", "Solicitud ficticia de telemetría")).status())
                .isEqualTo(201);

        Response scrape = withBasic("GET", "/actuator/prometheus", COLLECTOR_PASSWORD);
        assertThat(scrape.status()).isEqualTo(200);
        assertThat(scrape.body().lines()).anyMatch(line -> line.startsWith("http_server_requests_seconds_count{")
                && line.contains("status=\"500\"") && line.contains("uri=\"/api/v1/status\""));
        assertThat(scrape.body().lines()).anyMatch(line -> line.startsWith("http_server_requests_seconds_bucket{")
                && line.contains("le=\"2.0\""));
        assertThat(scrape.body()).contains("jvm_memory_used_bytes{", "jvm_threads_live_threads",
                "process_cpu_usage", "hikaricp_connections_active{pool=", "hikaricp_connections_max{pool=",
                "gestal_business_operations_total{operation=\"request_created\"} 1.0",
                "gestal_business_operations_total{operation=\"reservation_created\"} 0.0",
                "gestal_auth_login_rejections_total{reason=\"invalid_credentials\"} 5.0",
                "gestal_auth_login_rejections_total{reason=\"rate_limited\"} 1.0")
                .doesNotContain(correo, PRIVATE_MARKER);
        assertThat(output.getOut()).doesNotContain(correo, PRIVATE_MARKER);
        scrape.body().lines().filter(line -> line.startsWith("gestal_")
                || line.startsWith("hikaricp_connections_active{")
                || line.startsWith("hikaricp_connections_max{")
                || line.startsWith("jvm_threads_live_threads ")
                || line.startsWith("process_cpu_usage ")
                || (line.startsWith("jvm_memory_used_bytes{") && line.contains("area=\"heap\""))
                || (line.startsWith("http_server_requests_seconds_") && line.contains("status=\"500\"")
                        && (line.startsWith("http_server_requests_seconds_count{")
                                || line.startsWith("http_server_requests_seconds_sum{") || line.contains("le=\"2.0\""))))
                .forEach(line -> System.out.println("Scrape Prometheus " + line));
    }

    @Test
    void respuestaLogJsonYTrazaOtlpCompartenCorrelacion(CapturedOutput output) throws InterruptedException {
        String correlation = "fase-iv-" + UUID.randomUUID().toString().substring(0, 12);
        String traceId = UUID.randomUUID().toString().replace("-", "");
        String parentId = "1234567890abcdef";
        Response response = api.send("GET", "/api/v1/status?probe=" + PRIVATE_MARKER, null, null,
                Map.of("X-Correlation-ID", correlation, "traceparent", "00-" + traceId + "-" + parentId + "-01"));
        assertThat(response.status()).isEqualTo(200);
        assertThat(response.header("X-Correlation-ID")).contains(correlation);
        String logLine = output.getOut().lines().filter(line -> line.startsWith("{") && line.contains(correlation)
                && line.contains("Petición HTTP completada")).findFirst().orElseThrow();
        JsonNode log = jsonMapper.readTree(logLine);
        assertThat(log.path("timestamp").asString()).isNotBlank();
        assertThat(log.path("logger").asString()).endsWith("CorrelationIdFilter");
        assertThat(log.path("correlationId").asString()).isEqualTo(correlation);
        assertThat(log.path("traceId").asString()).isEqualTo(traceId);
        String spanId = log.path("spanId").asString();
        assertThat(spanId).matches("[0-9a-f]{16}");
        assertThat(log.path("status").asInt()).isEqualTo(200);
        assertThat(logLine).doesNotContain(PRIVATE_MARKER);

        Span span = awaitSpan(spanId);
        assertThat(span.getName()).contains("/api/v1/status");
        assertThat(span.getKind()).isEqualTo(Span.SpanKind.SPAN_KIND_SERVER);
        assertThat(hex(span.getTraceId().toByteArray())).isEqualTo(traceId);
        assertThat(hex(span.getParentSpanId().toByteArray())).isEqualTo(parentId);
        assertThat(span.getAttributesList().toString()).doesNotContain(PRIVATE_MARKER, correlation);

        System.out.println("Telemetría HTTP/OTLP " + jsonMapper.writeValueAsString(Map.of(
                "response", Map.of("status", response.status(), "X-Correlation-ID", correlation),
                "log", log,
                "span", Map.of("name", span.getName(), "traceId", hex(span.getTraceId().toByteArray()),
                        "spanId", hex(span.getSpanId().toByteArray()), "transport", "OTLP/HTTP"))));
    }

    private Response withBasic(String method, String path, String password) {
        String credentials = Base64.getEncoder().encodeToString(
                ("prometheus:" + password).getBytes(StandardCharsets.UTF_8));
        return api.send(method, path, null, null, Map.of("Authorization", "Basic " + credentials));
    }

    private static String hex(byte[] bytes) {
        return HexFormat.of().formatHex(bytes);
    }

    private static Span awaitSpan(String spanId) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            Span span = SPANS.poll(100, TimeUnit.MILLISECONDS);
            if (span != null && hex(span.getSpanId().toByteArray()).equals(spanId)) {
                return span;
            }
        }
        throw new AssertionError("El colector no recibió por OTLP el span " + spanId);
    }

    private static HttpServer startCollector() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/v1/traces", exchange -> {
                try (exchange) {
                    ExportTraceServiceRequest batch = ExportTraceServiceRequest.parseFrom(exchange.getRequestBody());
                    batch.getResourceSpansList().forEach(resource -> resource.getScopeSpansList()
                            .forEach(scope -> SPANS.addAll(scope.getSpansList())));
                    exchange.getResponseHeaders().set("Content-Type", "application/x-protobuf");
                    exchange.sendResponseHeaders(200, -1);
                }
            });
            server.start();
            return server;
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

}

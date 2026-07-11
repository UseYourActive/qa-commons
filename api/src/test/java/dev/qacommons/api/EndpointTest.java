package dev.qacommons.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import dev.qacommons.core.config.QaConfig;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class EndpointTest {

    private static HttpServer server;
    private static QaConfig config;

    private record Req(String name) {
    }

    private record Res(String id, String name) {
    }

    private record Err(String code) {
    }

    private static final class WidgetsEndpoint extends Endpoint<Req, Res, Err> {
        WidgetsEndpoint(QaConfig config) {
            super(config, "/widgets", Res.class, Err.class);
        }

        ApiResult<Res, Err> create(Req body) {
            return post(body);
        }

        ApiResult<Res, Err> getById(String id) {
            return get("/{id}", id);
        }

        ApiResult<Res, Err> update(String id, Req body) {
            return put("/{id}", body, id);
        }

        ApiResult<Res, Err> remove(String id) {
            return delete("/{id}", id);
        }
    }

    @BeforeAll
    static void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/widgets", EndpointTest::handle);
        server.start();
        config = new QaConfig("http://localhost:" + server.getAddress().getPort(), 1L, Duration.ofSeconds(5));
    }

    @AfterAll
    static void stopServer() {
        server.stop(0);
    }

    private static void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();

        if ("POST".equals(method) && path.equals("/widgets")) {
            respond(exchange, 201, "{\"id\":\"new-id\",\"name\":\"created\"}");
        } else if ("GET".equals(method) && path.equals("/widgets/known")) {
            respond(exchange, 200, "{\"id\":\"known\",\"name\":\"Widget\"}");
        } else if ("GET".equals(method) && path.equals("/widgets/missing")) {
            respond(exchange, 404, "{\"code\":\"NOT_FOUND\"}");
        } else if ("GET".equals(method) && path.equals("/widgets/broken")) {
            respond(exchange, 200, "not-json");
        } else if ("PUT".equals(method) && path.startsWith("/widgets/")) {
            String id = path.substring("/widgets/".length());
            respond(exchange, 200, "{\"id\":\"" + id + "\",\"name\":\"updated\"}");
        } else if ("DELETE".equals(method) && path.startsWith("/widgets/")) {
            String id = path.substring("/widgets/".length());
            respond(exchange, 200, "{\"id\":\"" + id + "\",\"name\":\"deleted\"}");
        } else {
            respond(exchange, 500, "{\"code\":\"UNEXPECTED_REQUEST\"}");
        }
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    @Test
    void post_returnsSuccessForJsonBody() {
        WidgetsEndpoint endpoint = new WidgetsEndpoint(config);

        ApiResult<Res, Err> result = endpoint.create(new Req("widget"));

        assertThat(result).isInstanceOf(ApiResult.Success.class);
        assertThat(result.status()).isEqualTo(201);
        assertThat(result.expectSuccess()).isEqualTo(new Res("new-id", "created"));
    }

    @Test
    void get_withPathParam_returnsSuccess() {
        WidgetsEndpoint endpoint = new WidgetsEndpoint(config);

        ApiResult<Res, Err> result = endpoint.getById("known");

        assertThat(result.status()).isEqualTo(200);
        assertThat(result.expectSuccess()).isEqualTo(new Res("known", "Widget"));
    }

    @Test
    void get_nonExistentId_returnsTypedFailure() {
        WidgetsEndpoint endpoint = new WidgetsEndpoint(config);

        ApiResult<Res, Err> result = endpoint.getById("missing");

        assertThat(result).isInstanceOf(ApiResult.Failure.class);
        assertThat(result.status()).isEqualTo(404);
        assertThat(result.expectFailure()).isEqualTo(new Err("NOT_FOUND"));
    }

    @Test
    void get_unparseableBody_returnsUnparsed() {
        WidgetsEndpoint endpoint = new WidgetsEndpoint(config);

        ApiResult<Res, Err> result = endpoint.getById("broken");

        assertThat(result).isInstanceOf(ApiResult.Unparsed.class);
        assertThat(((ApiResult.Unparsed<Res, Err>) result).raw()).isEqualTo("not-json");
    }

    @Test
    void put_withPathParam_returnsSuccess() {
        WidgetsEndpoint endpoint = new WidgetsEndpoint(config);

        ApiResult<Res, Err> result = endpoint.update("abc", new Req("renamed"));

        assertThat(result.status()).isEqualTo(200);
        assertThat(result.expectSuccess()).isEqualTo(new Res("abc", "updated"));
    }

    @Test
    void delete_withPathParam_returnsSuccess() {
        WidgetsEndpoint endpoint = new WidgetsEndpoint(config);

        ApiResult<Res, Err> result = endpoint.remove("abc");

        assertThat(result.status()).isEqualTo(200);
        assertThat(result.expectSuccess()).isEqualTo(new Res("abc", "deleted"));
    }
}

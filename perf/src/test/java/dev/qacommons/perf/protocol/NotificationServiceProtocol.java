package dev.qacommons.perf.protocol;

import static io.gatling.javaapi.http.HttpDsl.http;

import io.gatling.javaapi.http.HttpProtocolBuilder;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.util.function.Function;
import java.util.function.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Resolves the perf target's base URL and refuses to build a protocol at all
 * if it can't be resolved to something verified - never a silent default
 * that could point load at an unintended host.
 *
 * <p>Deliberately does not reuse {@code QaConfig.fromEnv()}: that method
 * always substitutes a default when the env var is unset and gives the
 * caller no way to distinguish "explicitly configured" from "defaulted",
 * which is exactly the silent-default risk this class exists to rule out.
 */
public final class NotificationServiceProtocol {

    private static final Logger LOGGER = LoggerFactory.getLogger(NotificationServiceProtocol.class);

    private static final String BASE_URL_ENV = "QA_BASE_URL";
    private static final String LOCALHOST_DEFAULT = "http://localhost:8080";
    private static final int REACHABILITY_TIMEOUT_MS = 500;

    private NotificationServiceProtocol() {
    }

    public static HttpProtocolBuilder httpProtocol() {
        return httpProtocol(System::getenv, NotificationServiceProtocol::isReachable);
    }

    static HttpProtocolBuilder httpProtocol(Function<String, String> envLookup, Predicate<String> reachabilityProbe) {
        String baseUrl = resolveBaseUrl(envLookup, reachabilityProbe);
        return http.baseUrl(baseUrl)
                .acceptHeader("application/json")
                .contentTypeHeader("application/json");
    }

    static String resolveBaseUrl(Function<String, String> envLookup, Predicate<String> reachabilityProbe) {
        String configured = blankToNull(envLookup.apply(BASE_URL_ENV));
        if (configured != null) {
            return configured;
        }

        if (reachabilityProbe.test(LOCALHOST_DEFAULT)) {
            LOGGER.warn("{} is not set; falling back to {} because it is reachable. "
                            + "Confirm this is really the intended perf target before trusting these results.",
                    BASE_URL_ENV, LOCALHOST_DEFAULT);
            return LOCALHOST_DEFAULT;
        }

        throw new IllegalStateException(BASE_URL_ENV + " is not set and " + LOCALHOST_DEFAULT
                + " is not reachable. Refusing to run perf tests against an unverified target - set "
                + BASE_URL_ENV + " explicitly.");
    }

    private static boolean isReachable(String url) {
        URI uri = URI.create(url);
        int port = uri.getPort() != -1 ? uri.getPort() : "https".equals(uri.getScheme()) ? 443 : 80;
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(uri.getHost(), port), REACHABILITY_TIMEOUT_MS);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }
}

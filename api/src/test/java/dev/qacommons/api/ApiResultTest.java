package dev.qacommons.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;

class ApiResultTest {

    private record Body(String value) {
    }

    private record Error(String code) {
    }

    @Test
    void success_expectSuccessReturnsBody() {
        ApiResult<Body, Error> result = new ApiResult.Success<>(200, Map.of(), new Body("ok"));

        assertThat(result.expectSuccess()).isEqualTo(new Body("ok"));
    }

    @Test
    void success_expectFailureThrows() {
        ApiResult<Body, Error> result = new ApiResult.Success<>(200, Map.of(), new Body("ok"));

        assertThatThrownBy(result::expectFailure).isInstanceOf(AssertionError.class);
    }

    @Test
    void failure_expectFailureReturnsError() {
        ApiResult<Body, Error> result = new ApiResult.Failure<>(409, Map.of(), new Error("NOTIF_081"));

        assertThat(result.expectFailure()).isEqualTo(new Error("NOTIF_081"));
    }

    @Test
    void failure_expectSuccessThrows() {
        ApiResult<Body, Error> result = new ApiResult.Failure<>(409, Map.of(), new Error("NOTIF_081"));

        assertThatThrownBy(result::expectSuccess).isInstanceOf(AssertionError.class);
    }

    @Test
    void unparsed_expectSuccessAndExpectFailureBothThrow() {
        ApiResult<Body, Error> result = new ApiResult.Unparsed<>(502, Map.of(), "<html>bad gateway</html>", null);

        assertThatThrownBy(result::expectSuccess).isInstanceOf(AssertionError.class);
        assertThatThrownBy(result::expectFailure).isInstanceOf(AssertionError.class);
    }

    @Test
    void unparsed_exposesRawBodyForDiagnosis() {
        ApiResult.Unparsed<Body, Error> result =
                new ApiResult.Unparsed<>(502, Map.of(), "<html>bad gateway</html>", null);

        assertThat(result.raw()).contains("bad gateway");
    }
}

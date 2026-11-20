package com.jobmate.profile.infrastructure;

import com.jobmate.common.exception.CustomException;
import com.jobmate.profile.domain.exception.ProfileErrorCode;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.time.Duration;
import org.springframework.stereotype.Component;

@Component
public class GithubReadmeClient {

    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER).build();

    public String canonicalUrl(String url) {
        if (url == null || !url.matches("https://github\\.com/[A-Za-z0-9][A-Za-z0-9-]{0,38}/[A-Za-z0-9_.-]{1,100}/?")) {
            throw new CustomException(ProfileErrorCode.INVALID_PROFILE_INPUT);
        }
        String canonical = url.replaceAll("/$", "");
        String repository = canonical.substring(canonical.lastIndexOf('/') + 1);
        if (repository.equals(".") || repository.equals("..") || repository.endsWith(".git")) {
            throw new CustomException(ProfileErrorCode.INVALID_PROFILE_INPUT);
        }
        return canonical;
    }

    public String read(String registeredUrl) {
        String path = canonicalUrl(registeredUrl).substring("https://github.com/".length());
        URI uri = URI.create("https://raw.githubusercontent.com/" + path + "/HEAD/README.md");
        var request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(5))
                .header("Accept", "text/plain").GET().build();
        var pending = client.sendAsync(request, info -> new LimitedBodySubscriber());
        try {
            var response = pending.get(5, TimeUnit.SECONDS);
            String mime = response.headers().firstValue("Content-Type").orElse("");
            if (response.statusCode() != 200 || !mime.startsWith("text/plain") || response.body().length == 0) {
                throw new IllegalStateException();
            }
            return new String(response.body(), StandardCharsets.UTF_8);
        } catch (Exception exception) {
            pending.cancel(true);
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new CustomException(ProfileErrorCode.PORTFOLIO_READ_FAILED);
        }
    }

    // 헤더 수신 뒤 본문이 끝없이 이어지더라도 전체 응답의 시간과 크기를 제한한다.
    private static final class LimitedBodySubscriber implements HttpResponse.BodySubscriber<byte[]> {

        private final CompletableFuture<byte[]> body = new CompletableFuture<>();
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        private Flow.Subscription subscription;

        @Override
        public CompletionStage<byte[]> getBody() {
            return body;
        }

        @Override
        public void onSubscribe(Flow.Subscription value) {
            subscription = value;
            value.request(1);
        }

        @Override
        public void onNext(List<ByteBuffer> buffers) {
            for (ByteBuffer buffer : buffers) {
                if ((long) bytes.size() + buffer.remaining() > 1024 * 1024) {
                    subscription.cancel();
                    body.completeExceptionally(new IllegalStateException("README size limit"));
                    return;
                }
                byte[] part = new byte[buffer.remaining()];
                buffer.get(part);
                bytes.writeBytes(part);
            }
            subscription.request(1);
        }

        @Override
        public void onError(Throwable exception) {
            body.completeExceptionally(exception);
        }

        @Override
        public void onComplete() {
            body.complete(bytes.toByteArray());
        }
    }

}

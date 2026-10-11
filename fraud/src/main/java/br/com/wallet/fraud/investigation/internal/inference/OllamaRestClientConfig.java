package br.com.wallet.fraud.investigation.internal.inference;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * Spring Configuration providing an optimized RestClient.Builder tuned for local Ollama inference:
 * - HTTP/2 transport via JdkClientHttpRequestFactory (virtual-thread non-blocking)
 * - Configured connection timeout (3s) and read timeout (30s)
 * - Default JSON media type headers
 */
@Configuration
public class OllamaRestClientConfig {

    @Bean
    @ConditionalOnMissingBean(RestClient.Builder.class)
    public RestClient.Builder restClientBuilder(
        @Value("${fraud.investigation.ollama.connect-timeout:3s}") final Duration connectTimeout,
        @Value("${fraud.investigation.ollama.read-timeout:30s}") final Duration readTimeout
    ) {
        HttpClient httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_2)
            .connectTimeout(connectTimeout)
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(readTimeout);

        return RestClient.builder()
            .requestFactory(requestFactory)
            .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
    }
}

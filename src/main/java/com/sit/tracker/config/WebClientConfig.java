package com.sit.tracker.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import reactor.netty.transport.ProxyProvider;

@Configuration
public class WebClientConfig {

    @Value("${app.steam.api-base-url:https://steamcommunity.com}")
    private String steamApiBaseUrl;

    @Value("${app.price-aggregator.base-url:https://api.pricempire.com}")
    private String priceAggregatorBaseUrl;

    // Rotating proxy gateway credentials. Leave proxy-host blank to disable
    // (local/dev runs direct — you don't want to burn paid proxy bandwidth
    // testing on localhost). Only the Steam client goes through the proxy;
    // Telegram and the price aggregator have no rate-limit problem and stay
    // direct.
    @Value("${app.steam.proxy-host:}")
    private String proxyHost;

    @Value("${app.steam.proxy-port:0}")
    private int proxyPort;

    @Value("${app.steam.proxy-username:}")
    private String proxyUsername;

    @Value("${app.steam.proxy-password:}")
    private String proxyPassword;

    @Bean
    public WebClient steamWebClient(WebClient.Builder sharedBuilder) {
        // Увеличиваем буфер памяти до 10 МБ, чтобы парсить большие JSON без DataBufferLimitException
        ExchangeStrategies strategies = ExchangeStrategies.builder()
                .codecs(codecs -> codecs.defaultCodecs().maxInMemorySize(10 * 1024 * 1024))
                .build();

        WebClient.Builder builder = sharedBuilder.clone()
                .baseUrl(steamApiBaseUrl)
                .exchangeStrategies(strategies)
                .defaultHeader(HttpHeaders.USER_AGENT, "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");

        if (proxyHost != null && !proxyHost.isBlank()) {
            HttpClient httpClient = HttpClient.create()
                    .proxy(proxySpec -> proxySpec
                            .type(ProxyProvider.Proxy.HTTP)
                            .host(proxyHost)
                            .port(proxyPort)
                            .username(proxyUsername)
                            .password(user -> proxyPassword));

            builder.clientConnector(new ReactorClientHttpConnector(httpClient));
        }

        return builder.build();
    }

    @Bean
    public WebClient priceAggregatorWebClient(WebClient.Builder sharedBuilder) {
        return sharedBuilder.clone()
                .baseUrl(priceAggregatorBaseUrl)
                .build();
    }
}
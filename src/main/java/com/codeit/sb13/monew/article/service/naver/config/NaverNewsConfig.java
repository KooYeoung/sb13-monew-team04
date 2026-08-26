package com.codeit.sb13.monew.article.service.naver.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(NaverNewsProperties.class)
public class NaverNewsConfig {

    private static final String CLIENT_ID_HEADER = "X-NCP-APIGW-API-KEY-ID";
    private static final String CLIENT_SECRET_HEADER = "X-NCP-APIGW-API-KEY";

    @Bean
    public RestClient naverNewsRestClient(NaverNewsProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.connectTimeout());
        requestFactory.setReadTimeout(properties.readTimeout());

        return RestClient.builder()
                .baseUrl(properties.baseUrl())
                .defaultHeader(CLIENT_ID_HEADER, properties.clientId())
                .defaultHeader(CLIENT_SECRET_HEADER, properties.clientSecret())
                .requestFactory(requestFactory)
                .build();
    }
}

package com.codeit.sb13.monew.global.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI openApi(
            @Value("${monew.openapi.server-url:http://localhost:8080}") String serverUrl,
            @Value("${monew.openapi.server-description:로컬 개발 서버}") String serverDescription
    ) {
        Server server = new Server()
                .url(serverUrl)
                .description(serverDescription);

        Info info = new Info()
                .title("Monew API 문서")
                .description("Monew 프로젝트의 Swagger API 문서입니다.")
                .version("v1");

        return new OpenAPI()
                .info(info)
                .servers(List.of(server));
    }
}

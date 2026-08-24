package com.codeit.sb13.monew.article.s3.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.auth.credentials.AnonymousCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;

import java.net.URI;

@Configuration
@EnableConfigurationProperties(S3Properties.class)
@Slf4j
public class S3Config {
    @Bean
    public S3Client s3Client(S3Properties props) {
        S3ClientBuilder builder = S3Client.builder()
                .region(Region.of(props.region()));

        return initializeS3Client(props, builder).build();
    }

    private S3ClientBuilder initializeS3Client(S3Properties props, S3ClientBuilder builder) {
        if (isEndpointPresent(props)) {
            S3ClientBuilder s3ClientBuilder = builder.endpointOverride(URI.create(props.endpoint()))
                    .forcePathStyle(true)
                    .credentialsProvider(AnonymousCredentialsProvider.create());
            log.info("S3Client - 로컬 Mock 사용: {}", props.endpoint());
            return s3ClientBuilder;
        }

        S3ClientBuilder s3ClientBuilder = builder.forcePathStyle(false)
                .credentialsProvider(DefaultCredentialsProvider.builder().build());
        log.info("S3Client - 실제 AWS(region={})", props.region());

        return s3ClientBuilder;
    }

    private boolean isEndpointPresent(S3Properties props) {
        return StringUtils.hasText(props.endpoint());
    }
}

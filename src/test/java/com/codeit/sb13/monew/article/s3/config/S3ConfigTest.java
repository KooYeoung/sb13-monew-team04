package com.codeit.sb13.monew.article.s3.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import software.amazon.awssdk.services.s3.S3Client;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("S3 설정 단위 테스트")
class S3ConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(S3Config.class);

    @Test
    @DisplayName("S3 설정 값을 바인딩한다")
    void bindsS3Properties() {
        contextRunner
                .withPropertyValues(
                        "monew.s3.bucket=monew-backup",
                        "monew.s3.region=us-east-1",
                        "monew.s3.endpoint=http://localhost:9090",
                        "monew.s3.prefix=article-backups",
                        "monew.s3.enabled=true",
                        "monew.s3.cron=0 0 1 * * *"
                )
                .run(context -> {
                    S3Properties properties = context.getBean(S3Properties.class);

                    assertThat(properties.bucket()).isEqualTo("monew-backup");
                    assertThat(properties.region()).isEqualTo("us-east-1");
                    assertThat(properties.endpoint()).isEqualTo("http://localhost:9090");
                    assertThat(properties.prefix()).isEqualTo("article-backups");
                    assertThat(properties.enabled()).isTrue();
                    assertThat(properties.cron()).isEqualTo("0 0 1 * * *");
                });
    }

    @Test
    @DisplayName("S3Client Bean을 생성한다")
    void createsS3Client() {
        contextRunner
                .withPropertyValues(
                        "monew.s3.bucket=monew-backup",
                        "monew.s3.region=us-east-1",
                        "monew.s3.endpoint=http://localhost:9090"
                )
                .run(context -> assertThat(context.getBean(S3Client.class)).isNotNull());
    }
}

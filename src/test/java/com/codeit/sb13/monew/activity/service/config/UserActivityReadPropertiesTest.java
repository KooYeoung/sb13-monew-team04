package com.codeit.sb13.monew.activity.service.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.codeit.sb13.monew.activity.service.UserActivityReadSourceType;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class UserActivityReadPropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(UserActivityReadSourceConfig.class);

    @Test
    void defaultsToRdbWhenSourceIsMissing() {
        contextRunner.run(context -> assertThat(
                context.getBean(UserActivityReadProperties.class).readSource()
        ).isEqualTo(UserActivityReadSourceType.RDB));
    }

    @Test
    void bindsMongoDbSourceFromConfiguration() {
        contextRunner
                .withPropertyValues("monew.activity.read-source=MONGODB")
                .run(context -> assertThat(
                        context.getBean(UserActivityReadProperties.class).readSource()
                ).isEqualTo(UserActivityReadSourceType.MONGODB));
    }
}

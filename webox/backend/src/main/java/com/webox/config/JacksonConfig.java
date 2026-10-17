package com.webox.config;

import java.math.BigDecimal;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.fasterxml.jackson.databind.module.SimpleModule;

/**
 * Money is serialized as a plain decimal literal ({@code 22.50}, never {@code 2.25E+1}),
 * which keeps the value exact all the way to the browser where it is parsed into integer cents.
 */
@Configuration
public class JacksonConfig {

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer bigDecimalPlainCustomizer() {
        // Rendering BigDecimal through ToStringSerializer emits an exact decimal literal
        // ("22.50"), which the SPA parses into integer cents at the boundary.
        return builder -> {
            SimpleModule module = new SimpleModule();
            module.addSerializer(BigDecimal.class, ToStringSerializer.instance);
            builder.modulesToInstall(module);
        };
    }
}

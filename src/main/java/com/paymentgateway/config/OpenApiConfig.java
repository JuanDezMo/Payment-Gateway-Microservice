package com.paymentgateway.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI paymentGatewayOpenApi() {
        return new OpenAPI()
                .info(new Info().title("Payment Gateway").version("1.0.0")
                        .description("Simulated payment gateway. Card ending in 0000 is rejected, 9999 simulates an error, any other is approved."))
                .components(new Components().addSecuritySchemes("apiKey",
                        new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer")));
    }
}

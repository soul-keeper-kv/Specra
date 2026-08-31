package dev.specra.api.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The web app generates its TypeScript types from the schema this exposes
 * (`npm run gen:api` in apps/web), so the two sides cannot drift.
 */
@Configuration
public class OpenApiConfig {

  @Bean
  public OpenAPI specraOpenAPI() {
    return new OpenAPI()
        .info(
            new Info()
                .title("Specra API")
                .version("0.1.0")
                .description(
                    "Notes CRUD on JPA/PostgreSQL plus provider-agnostic AI chat and pgvector RAG.")
                .license(new License().name("MIT")))
        .servers(List.of(new Server().url("http://localhost:8080").description("Local")));
  }
}

package dev.specra.api;

import dev.specra.api.support.TestcontainersConfiguration;
import org.springframework.boot.SpringApplication;

/**
 * Run the API against a throwaway pgvector container instead of your own Postgres:
 *
 * <pre>./mvnw spring-boot:test-run</pre>
 */
public class TestSpecraApiApplication {

  public static void main(String[] args) {
    SpringApplication.from(SpecraApiApplication::main)
        .with(TestcontainersConfiguration.class)
        .run(args);
  }
}

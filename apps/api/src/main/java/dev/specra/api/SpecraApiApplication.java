package dev.specra.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication
@EnableJpaAuditing
public class SpecraApiApplication {

  public static void main(String[] args) {
    SpringApplication.run(SpecraApiApplication.class, args);
  }
}

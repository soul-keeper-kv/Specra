package dev.specra.api.config;

import dev.specra.api.core.error.ApiProblem;
import dev.specra.api.core.error.ErrorCode;
import dev.specra.api.core.i18n.SupportedLocale;
import dev.specra.api.core.logging.MdcKeys;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.HeaderParameter;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.tags.Tag;
import java.util.List;
import java.util.Map;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The OpenAPI document, which is a build input rather than only documentation: the web app
 * generates its TypeScript from it ({@code pnpm gen:api} in apps/web), so the two sides cannot
 * drift.
 *
 * <p>Two things are added here that annotations on controllers cannot express well. The error
 * responses are attached to every operation from one place, because they come from a global
 * exception handler and are identical everywhere. And the {@code Accept-Language} header is
 * documented once, because every endpoint honours it.
 */
@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {

  private static final String PROBLEM_SCHEMA = "ApiProblem";
  private static final String PROBLEM_JSON = "application/problem+json";

  @Bean
  public OpenAPI specraOpenAPI(@Value("${server.port:8080}") int port) {
    return new OpenAPI()
        .info(
            new Info()
                .title("Specra API")
                .version("0.1.0")
                .description(
                    """
                    Notes CRUD on JPA/PostgreSQL, plus provider-agnostic AI chat and pgvector RAG.

                    **Errors** are RFC 9457 problem documents (`application/problem+json`). Switch \
                    on the `code` field — `title` and `detail` are translated and will differ \
                    between callers.

                    **Languages**: send `Accept-Language: vi` on any request. There is no `?lang=` \
                    query parameter — the language is content negotiation, not part of the URL.

                    **Tracing**: every response carries `X-Request-Id`, and `X-Trace-Id` when \
                    tracing is enabled. Both appear in the error body and in the server log.
                    """)
                .license(new License().name("MIT"))
                .contact(new Contact().name("Specra").url("https://specra.dev")))
        .servers(
            List.of(
                new Server().url("http://localhost:" + port).description("Local"),
                new Server().url("https://api.specra.dev").description("Production")))
        .tags(
            List.of(
                new Tag().name("Notes").description("CRUD over PostgreSQL via Spring Data JPA."),
                new Tag()
                    .name("AI")
                    .description(
                        "Chat and RAG. The provider behind these is configuration, not code.")));
  }

  /**
   * Adds what belongs on every operation: the language header, and the error bodies the global
   * exception handler can return. Doing it here rather than with {@code @ApiResponse} on each
   * method is the only way the two stay in step as endpoints are added.
   */
  @Bean
  public OpenApiCustomizer specraProblemResponses() {
    return openApi -> {
      Components components =
          openApi.getComponents() == null ? new Components() : openApi.getComponents();
      components.addSchemas(PROBLEM_SCHEMA, problemSchema());
      openApi.setComponents(components);

      if (openApi.getPaths() == null) {
        return;
      }
      openApi
          .getPaths()
          .values()
          .forEach(path -> path.readOperations().forEach(OpenApiConfig::describeFailures));
    };
  }

  private static void describeFailures(Operation operation) {
    operation.addParametersItem(acceptLanguageHeader());

    ApiResponses responses =
        operation.getResponses() == null ? new ApiResponses() : operation.getResponses();
    problem(responses, ErrorCode.VALIDATION_FAILED, "Request body or parameters failed validation");
    problem(responses, ErrorCode.RESOURCE_NOT_FOUND, "No such resource");
    problem(
        responses,
        ErrorCode.CONFLICT,
        "The resource changed under you, or a constraint refused the write");
    problem(responses, ErrorCode.AI_PROVIDER_ERROR, "The model provider rejected the request");
    problem(
        responses,
        ErrorCode.INTERNAL_ERROR,
        "Unexpected failure; quote the traceId when reporting it");
    operation.setResponses(responses);
  }

  /** Only fills a slot that is still empty, so an explicit {@code @ApiResponse} always wins. */
  private static void problem(ApiResponses responses, ErrorCode code, String description) {
    String status = String.valueOf(code.status().value());
    if (responses.containsKey(status)) {
      return;
    }
    responses.addApiResponse(
        status,
        new ApiResponse()
            .description(description)
            .content(
                new Content()
                    .addMediaType(
                        PROBLEM_JSON,
                        new MediaType()
                            .schema(
                                new Schema<>().$ref("#/components/schemas/" + PROBLEM_SCHEMA)))));
  }

  private static Parameter acceptLanguageHeader() {
    return new HeaderParameter()
        .name("Accept-Language")
        .description(
            "Language for error messages and validation text. Supported: "
                + String.join(", ", SupportedLocale.tags())
                + ". Responses echo "
                + MdcKeys.REQUEST_ID_HEADER
                + ".")
        .required(false)
        .schema(new StringSchema()._enum(SupportedLocale.tags()).example(SupportedLocale.VI.tag()));
  }

  /**
   * {@link ApiProblem} never appears on a method signature — the handler returns Spring's {@code
   * ProblemDetail} — so its schema has to be resolved explicitly rather than discovered.
   */
  @SuppressWarnings("rawtypes")
  private static Schema problemSchema() {
    Map<String, Schema> resolved = ModelConverters.getInstance().read(ApiProblem.class);
    Schema schema = resolved.get(PROBLEM_SCHEMA);
    return schema == null ? new Schema<>() : schema;
  }
}

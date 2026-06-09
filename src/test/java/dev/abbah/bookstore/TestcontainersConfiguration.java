package dev.abbah.bookstore;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.grafana.LgtmStackContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

  private static final PostgreSQLContainer postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:18"));

  private static final LgtmStackContainer grafana = new LgtmStackContainer(DockerImageName.parse("grafana/otel-lgtm:0.28.0"));

  @Bean
  @ServiceConnection
  LgtmStackContainer grafanaLgtmContainer() {
    return grafana;
  }

  @Bean
  @ServiceConnection
  PostgreSQLContainer postgresContainer() {
    return postgres;
  }

}

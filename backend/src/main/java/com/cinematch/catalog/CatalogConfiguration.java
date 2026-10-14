package com.cinematch.catalog;

import java.nio.file.Path;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class CatalogConfiguration implements WebMvcConfigurer {
  private final String posterDirectory;

  public CatalogConfiguration(
      @Value("${app.catalog.poster-directory:./.data/catalog-posters}") String posterDirectory) {
    this.posterDirectory = posterDirectory;
  }

  @Override
  public void addResourceHandlers(ResourceHandlerRegistry registry) {
    String location = Path.of(posterDirectory).toAbsolutePath().normalize().toUri().toString();
    registry
        .addResourceHandler("/images/catalog/**")
        .addResourceLocations(location.endsWith("/") ? location : location + "/")
        .setCachePeriod(86400);
  }

  @Bean
  ApplicationRunner importCatalog(
      CatalogImportService importer, @Value("${app.catalog.import-file:}") String input) {
    return args -> {
      if (input.isBlank()) return;
      var result = importer.importFile(Path.of(input));
      LoggerFactory.getLogger(CatalogConfiguration.class)
          .info(
              "Catalog import complete: created={}, updated={}, skipped={}",
              result.created(),
              result.updated(),
              result.skipped());
    };
  }
}

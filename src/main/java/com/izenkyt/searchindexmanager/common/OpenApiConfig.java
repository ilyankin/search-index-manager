package com.izenkyt.searchindexmanager.common;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI searchIndexManagerOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Search Index Manager")
                        .description("Строит и версионирует Lucene-индексы из загруженных документов, "
                                + "хранит артефакты сборки в MinIO.")
                        .version("v1"));
    }
}

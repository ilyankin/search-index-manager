package com.izenkyt.searchindexmanager.indexbuild;

import com.izenkyt.searchindexmanager.index.api.dto.IndexVersionResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping(path = "/indexes", version = "v1")
@Tag(name = "Сборка индекса", description = "Запуск асинхронной сборки новой версии индекса из NDJSON-документов.")
public class IndexBuildController {

    private static final Logger log = LoggerFactory.getLogger(IndexBuildController.class);

    private final IndexBuildService buildService;

    public IndexBuildController(IndexBuildService buildService) {
        this.buildService = buildService;
    }

    @Operation(summary = "Запустить сборку новой версии индекса",
            description = "Принимает поток NDJSON (по одному JSON-объекту документа на строку), соответствующий схеме "
                    + "полей индекса. Сборка выполняется асинхронно: эндпоинт сразу отвечает 202 Accepted, создав новую "
                    + "версию в статусе CREATED, а сам процесс — построение Lucene-индекса, упаковка в tar.gz и выгрузка "
                    + "в MinIO — происходит в фоне. Прогресс отслеживается через GET /indexes/{id}/versions/{version}: "
                    + "статус проходит путь CREATED → BUILDING → BUILT → UPLOADED → READY, либо FAILED при ошибке "
                    + "на любом из шагов.")
    @RequestBody(description = "NDJSON-поток документов: один JSON-объект на строку, поля должны соответствовать "
            + "схеме индекса.", required = true, content = @Content(mediaType = "application/x-ndjson"))
    @ApiResponse(responseCode = "202", description = "Сборка принята в работу, создана версия в статусе CREATED; "
            + "заголовок Location указывает на ресурс новой версии")
    @ApiResponse(responseCode = "404", description = "Индекс с таким id не найден",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "409", description = "У индекса уже есть активная (незавершённая) сборка",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @PostMapping("/{id}/build")
    public ResponseEntity<IndexVersionResponse> build(@Parameter(description = "Идентификатор индекса") @PathVariable UUID id,
                                                      HttpServletRequest request) throws IOException {
        log.debug("Received build request for index {}", id);
        try (InputStream ndjson = request.getInputStream()) {
            IndexVersionResponse response = buildService.startBuild(id, ndjson);
            URI location = ServletUriComponentsBuilder.fromCurrentContextPath()
                    .pathSegment("api", "v1", "indexes", id.toString(),
                            "versions", String.valueOf(response.version()))
                    .build()
                    .toUri();
            log.info("Accepted build for index {}: version {}", id, response.version());
            return ResponseEntity.accepted().location(location).body(response);
        }
    }
}

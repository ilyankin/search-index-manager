package com.izenkyt.searchindexmanager.index.api;

import com.izenkyt.searchindexmanager.index.IndexCatalogService;
import com.izenkyt.searchindexmanager.index.api.dto.ArtifactDownloadResponse;
import com.izenkyt.searchindexmanager.index.api.dto.CreateIndexRequest;
import com.izenkyt.searchindexmanager.index.api.dto.IndexResponse;
import com.izenkyt.searchindexmanager.index.api.dto.IndexVersionResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping(path = "/indexes", version = "v1")
@Tag(name = "Индексы", description = "Реестр индексов и их версий: описание схемы, список версий, скачивание готового артефакта. "
        + "Построением индексов занимается отдельный эндпоинт build (см. тег «Сборка индекса»).")
public class IndexController {

    private final IndexCatalogService service;

    public IndexController(IndexCatalogService service) {
        this.service = service;
    }

    @Operation(summary = "Зарегистрировать новый индекс",
            description = "Создаёт запись об индексе с заданной схемой полей. Сам Lucene-индекс на этом шаге не строится "
                    + "и версий у индекса ещё нет — первая версия появится после вызова POST /indexes/{id}/build.")
    @ApiResponse(responseCode = "201", description = "Индекс успешно создан")
    @ApiResponse(responseCode = "400", description = "Ошибка валидации запроса (пустое имя, недопустимые символы, "
            + "дублирующиеся имена полей и т.п.)", content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "409", description = "Индекс с таким именем уже существует",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @PostMapping
    public ResponseEntity<IndexResponse> create(@Valid @RequestBody CreateIndexRequest request) {
        IndexResponse created = service.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.id())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    @Operation(summary = "Получить список индексов", description = "Возвращает постраничный список всех зарегистрированных индексов.")
    @ApiResponse(responseCode = "200", description = "Страница с индексами")
    @GetMapping
    public Page<IndexResponse> list(Pageable pageable) {
        return service.list(pageable);
    }

    @Operation(summary = "Получить индекс по идентификатору")
    @ApiResponse(responseCode = "200", description = "Индекс найден")
    @ApiResponse(responseCode = "404", description = "Индекс с таким id не найден",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @GetMapping("/{id}")
    public IndexResponse get(@Parameter(description = "Идентификатор индекса") @PathVariable UUID id) {
        return service.getById(id);
    }

    @Operation(summary = "Получить список версий индекса",
            description = "Возвращает все версии индекса по возрастанию номера, независимо от их статуса "
                    + "(включая ещё строящиеся и упавшие с ошибкой).")
    @ApiResponse(responseCode = "200", description = "Список версий")
    @ApiResponse(responseCode = "404", description = "Индекс с таким id не найден",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @GetMapping("/{id}/versions")
    public List<IndexVersionResponse> listVersions(@Parameter(description = "Идентификатор индекса") @PathVariable UUID id) {
        return service.listVersions(id);
    }

    @Operation(summary = "Получить конкретную версию индекса",
            description = "Используется, в частности, для отслеживания прогресса сборки: "
                    + "статус версии проходит путь CREATED → BUILDING → BUILT → UPLOADED → READY (либо FAILED на любом шаге).")
    @ApiResponse(responseCode = "200", description = "Версия найдена")
    @ApiResponse(responseCode = "404", description = "Индекс или версия с таким номером не найдены",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @GetMapping("/{id}/versions/{version}")
    public IndexVersionResponse getVersion(@Parameter(description = "Идентификатор индекса") @PathVariable UUID id,
                                            @Parameter(description = "Номер версии", example = "1") @PathVariable int version) {
        return service.getVersion(id, version);
    }

    @Operation(summary = "Получить ссылку на скачивание артефакта версии",
            description = "Возвращает короткоживущую presigned-ссылку на tar.gz с сегментами Lucene-индекса в MinIO. "
                    + "Доступно только для версий в статусе READY.")
    @ApiResponse(responseCode = "200", description = "Ссылка на скачивание сформирована")
    @ApiResponse(responseCode = "404", description = "Индекс или версия с таким номером не найдены",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "409", description = "Артефакт ещё не готов: версия не находится в статусе READY",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @GetMapping("/{id}/versions/{version}/artifact")
    public ArtifactDownloadResponse getArtifact(@Parameter(description = "Идентификатор индекса") @PathVariable UUID id,
                                                 @Parameter(description = "Номер версии", example = "1") @PathVariable int version) {
        return service.getArtifactDownloadUrl(id, version);
    }
}
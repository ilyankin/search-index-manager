package com.izenkyt.searchindexmanager.indexload;

import com.izenkyt.searchindexmanager.indexload.LoadedIndexRegistry.BeginLoadResult;
import com.izenkyt.searchindexmanager.indexload.LoadedIndexRegistry.LoadedIndex;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping(path = "/indexes", version = "v1")
@Tag(name = "Загрузка индекса", description = "Обратная загрузка готового артефакта версии из MinIO в живой "
        + "Lucene-индекс этого инстанса: скачивание, проверка целостности, безопасная распаковка, открытый "
        + "DirectoryReader. Реестр загруженных индексов — in-memory, локален для инстанса.")
public class IndexLoadController {

    private final IndexLoadService service;

    public IndexLoadController(IndexLoadService service) {
        this.service = service;
    }

    @Operation(summary = "Загрузить версию индекса из MinIO в память",
            description = "Асинхронно скачивает артефакт версии, проверяет checksum/размер, безопасно распаковывает "
                    + "и открывает DirectoryReader. Доступно только для версий со статусом UPLOADED или READY. "
                    + "Повторный вызов для версии, уже загружающейся или загруженной — no-op с текущим состоянием.")
    @ApiResponse(responseCode = "202", description = "Принято в работу, запись реестра в статусе LOADING")
    @ApiResponse(responseCode = "200", description = "Версия уже загружается или загружена — состояние не изменилось")
    @ApiResponse(responseCode = "404", description = "Индекс или версия с таким номером не найдены",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "409", description = "У версии нет готового артефакта (статус не UPLOADED/READY)",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @PostMapping("/{id}/versions/{version}/load")
    public ResponseEntity<LoadedIndexResponse> load(
            @Parameter(description = "Идентификатор индекса") @PathVariable UUID id,
            @Parameter(description = "Номер версии", example = "1") @PathVariable int version) {
        BeginLoadResult result = service.startLoad(id, version);
        LoadedIndexResponse body = toResponse(result.entry());
        HttpStatus status = result.isNew() ? HttpStatus.ACCEPTED : HttpStatus.OK;
        return ResponseEntity.status(status).body(body);
    }

    @Operation(summary = "Список загруженных в этот инстанс индексов",
            description = "Возвращает все записи реестра, включая ещё загружающиеся (LOADING) и упавшие (FAILED).")
    @ApiResponse(responseCode = "200", description = "Список записей реестра")
    @GetMapping("/loaded")
    public List<LoadedIndexResponse> listLoaded() {
        return service.list().stream().map(this::toResponse).toList();
    }

    @Operation(summary = "Выгрузить версию индекса из памяти",
            description = "Закрывает DirectoryReader и удаляет распакованные файлы с диска. "
                    + "Загрузку в процессе (LOADING) выгрузить нельзя.")
    @ApiResponse(responseCode = "204", description = "Выгружено")
    @ApiResponse(responseCode = "404", description = "Версия не найдена в реестре загруженных",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "409", description = "Версия ещё загружается (LOADING)",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @DeleteMapping("/{id}/versions/{version}/load")
    public ResponseEntity<Void> unload(
            @Parameter(description = "Идентификатор индекса") @PathVariable UUID id,
            @Parameter(description = "Номер версии", example = "1") @PathVariable int version) {
        service.unload(id, version);
        return ResponseEntity.noContent().build();
    }

    private LoadedIndexResponse toResponse(LoadedIndex entry) {
        LoadedIndexRegistry.LoadKey key = entry.key();
        LoadedIndexRegistry.Snapshot snapshot = entry.snapshot();
        return new LoadedIndexResponse(
                key.indexId(),
                key.version(),
                snapshot.state().name(),
                snapshot.numDocs(),
                snapshot.sizeOnDiskBytes(),
                snapshot.error(),
                snapshot.loadedAt()
        );
    }
}

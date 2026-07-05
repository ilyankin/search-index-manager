# Search Index Manager

Данный сервис принимает документы, сам строит из них полнотекстовый индекс
(Apache Lucene), версионирует результат и кладёт готовый артефакт в S3-совместимое
хранилище (MinIO). Дополнительно умеет забрать готовую версию обратно и открыть её как
Lucene `DirectoryReader` у себя в памяти — но сам поисковых запросов не выполняет, это
вне скоупа.

## Как это работает

1. `POST /api/v1/indexes` создаёт индекс — запись в каталоге со схемой полей, без Lucene.
2. `POST /api/v1/indexes/{id}/build` принимает NDJSON-документы, отвечает `202 Accepted`
   и создаёт `IndexVersion`. Сборка идёт в фоне.
3. В фоне: документы пишутся в Lucene `IndexWriter` во временной директории → индекс
   паковается в `index.tar.gz` → архив с checksum'ом уходит в MinIO → приложение
   публикует в Kafka событие "версия загружена".
4. Оно же читает это событие и переводит версию в финальный статус.
5. Статус версии: `CREATED → BUILDING → BUILT → UPLOADED → READY` (или `FAILED` на любом
   шаге). Когда `READY` — `GET /api/v1/indexes/{id}/versions/{version}/artifact` отдаёт
   presigned-ссылку на скачивание из MinIO напрямую.
6. `POST /api/v1/indexes/{id}/versions/{version}/load` скачивает артефакт, сверяет
   checksum и размер, безопасно распаковывает и открывает `DirectoryReader`. Загруженные
   версии видны в `GET /api/v1/indexes/loaded` (in-memory реестр, не переживает рестарт)
   и выгружаются через `DELETE .../load`.

## Роли компонентов

- **PostgreSQL** — каталог метаданных: индексы, схемы полей, версии и их статусы, ключ
  артефакта в MinIO. Сам индекс в базе не хранится.
- **MinIO** — хранит готовые артефакты (`{index}/{version}/index.tar.gz`) отдельно от
  builder'а: переживает пересборку/рестарт приложения, доступен по presigned-ссылке, и
  задел на горизонтальное масштабирование — несколько узлов поиска смогут скачать один
  артефакт и держать локальную копию. Старые версии остаются под своим ключом — откат не
  требует пересборки.
- **Kafka** — не очередь задач (сборка синхронная по HTTP через `@Async`). Событие
  "версия загружена в MinIO" разъединяет "артефакт физически загружен" от "версия
  признана готовой" — точка расширения для будущих независимых потребителей (например,
  сервиса поиска, который перезагрузит у себя индекс по этому сигналу).

## Назначение

Сервис — control-plane для построения и версионирования индексов. Но поисковые запросы не выполняет. 
Отдельный сервис поиска сможет:

1. узнать о новой `READY`-версии (Kafka-событие или поллинг каталога);
2. получить готовый индекс — скачать `index.tar.gz` из MinIO самому, либо вызвать
   `POST .../load` на этом приложении и получить открытый `DirectoryReader` без своей
   реализации скачивания/проверки/распаковки.

Несколько таких узлов масштабируются независимо от сборки, каждый со своей локальной
копией индекса — а этот сервис остаётся единственным источником правды о состоянии
индексов и версий.

Стек: Spring Boot 4.1 / Java 25, PostgreSQL + Flyway, MinIO, Kafka, JUnit 5 +
Testcontainers.

## Запуск через Docker Compose

Креды PostgreSQL/MinIO не хранятся в `docker-compose.yaml` — только в `.env`
(в `.gitignore`, не коммитится). Перед первым запуском:

```bash
cp .env.example .env
```

```bash
docker compose up --build
```

Поднимает `app` + `postgres` + `minio` + `kafka` + `kafka-ui`, с healthcheck'ами и
порядком старта (`app` — после `postgres`/`minio`/`kafka` healthy). Первый запуск дольше:
собирается образ.

```bash
docker compose ps
```

`postgres`, `minio`, `kafka`, `app` — `healthy`; `kafka-ui` — `running` (без healthcheck).

## Доступные интерфейсы

| Что               | URL                                          | Заметки                                                     |
|-------------------|-----------------------------------------------|--------------------------------------------------------------|
| REST API          | http://localhost:8080/api/v1                 | базовый путь всех эндпоинтов                                  |
| Swagger UI        | http://localhost:8080/swagger-ui/index.html  | автогенерация из контроллеров/DTO (springdoc-openapi)         |
| OpenAPI-документ  | http://localhost:8080/v3/api-docs            | сырой JSON                                                    |
| MinIO Console     | http://localhost:9001                        | `minioadmin` / `minioadmin`; S3 API — порт `9000`             |
| Kafka UI          | http://localhost:8090                        | топики, консьюмер-группы кластера `kafka:29092`               |
| PostgreSQL        | `localhost:5432`                             | БД и логин/пароль — `searchindex`                             |

## Ручная проверка сквозного сценария

`create index → build → READY → скачать артефакт` — HTTP Client файл IDEA
[`http/indexes.http`](http/indexes.http), окружение
[`http/http-client.env.json`](http/http-client.env.json) (`dev`). Выполняй запросы по
порядку:

1. `createIndex` — создаёт индекс `products`, сохраняет `indexId`.
2. `listIndexes`, `getIndex` — опционально, проверить создание.
3. `buildIndex` — отправляет NDJSON, сохраняет `indexVersion`.
4. `getVersion` — статус версии; повторяй, пока не станет `READY`.
5. `getArtifact` — presigned URL на `.tar.gz` (годен `SEARCH_INDEX_STORAGE_PRESIGN_TTL`,
   по умолчанию 15 минут); скачивается напрямую из MinIO, не через приложение.

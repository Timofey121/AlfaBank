# Go Crypto Service

Тот же функционал (подпись, шифрование, хэш, HTTPS-fetch, аудит-история операций), своя Go-идиоматичная реализация. 
Без  Web UI - только REST API. 

## Стек

Go 1.25, стандартная библиотека для HTTP (`net/http`, Go 1.22+ маршрутизация по методу) и крипты
(`crypto/rsa`, `crypto/aes`, `crypto/x509`), `modernc.org/sqlite` (чистый Go-драйвер SQLite, без
cgo), `golang.org/x/crypto/scrypt` для keystore, `github.com/google/uuid`. Ни фреймворка, ни ORM -
только `database/sql` и ручные SQL-запросы. Собирается штатным `go build`, есть Dockerfile и
docker-compose.

## Что нужно для запуска

Go 1.25+, либо просто Docker.

## Запуск

Пароль от keystore берётся из переменной окружения - в коде и конфигах его нет:

```bash
export KEYSTORE_PASSWORD=changeit
go run ./cmd/server
```

Или через Docker Compose:

```bash
KEYSTORE_PASSWORD=changeit docker-compose up --build
```

Поднимается на `http://localhost:8080`. Если директории keystore ещё нет  сервис
стартует с пустым хранилищем (создаёт директорию), ключ можно сгенерировать позже через API.

Сгенерировать ключ:

```bash
curl -s -X POST http://localhost:8080/api/v1/admin/generate-keystore \
  -H "Content-Type: application/json" \
  -d '{"alias":"crypto-key","cn":"CryptoService","validityDays":365}' | jq .
```

В ответе `certBase64` - DER-сертификат, он же потом идёт как `recipientCertificate` при
шифровании.

`/api/v1/admin/...` так же, как в Java-версии, оставлен без аутентификации - нормально для
тестового задания, не для прода.

## API и эндпоинты

| Метод | URL                               | Что делает                                         |
|-------|-----------------------------------|----------------------------------------------------|
| POST  | `/api/v1/admin/generate-keystore` | генерит RSA-2048 ключ + самоподписанный сертификат |
| POST  | `/api/v1/crypto/sign`             | подпись (свой конверт, ATTACHED/DETACHED)          |
| POST  | `/api/v1/crypto/verify`           | проверка подписи                                   |
| POST  | `/api/v1/crypto/encrypt`          | шифрование RSA-OAEP + AES-256-GCM                  |
| POST  | `/api/v1/crypto/decrypt`          | расшифровка                                        |
| POST  | `/api/v1/crypto/hash`             | SHA-256                                            |
| POST  | `/api/v1/fetch/document`          | скачать документ по HTTPS                          |
| GET   | `/api/v1/operations`              | история операций (пагинация, фильтр по типу)       |
| GET   | `/api/v1/operations/{id}`         | одна операция по id                                |

`convert/encode` и `convert/decode` из Java-версии не перенесены - это были чисто UI-хелперы
(base64 туда-обратно для веб-формы), а UI тут нет.

Пример полного цикла curl'ом:

```bash
# 1. Сгенерировать ключ (см. выше), сохранить certBase64

# 2. Зашифровать
curl -s -X POST localhost:8080/api/v1/crypto/encrypt \
  -d '{"plaintext":"aGVsbG8=","recipientCertificate":"<certBase64>","filename":"hi.txt"}'

# 3. Расшифровать тем же alias
curl -s -X POST localhost:8080/api/v1/crypto/decrypt \
  -d '{"ciphertext":"<ciphertext из ответа>","keyAlias":"crypto-key"}'

# 4. Подписать / проверить
curl -s -X POST localhost:8080/api/v1/crypto/sign \
  -d '{"data":"aGVsbG8=","keyAlias":"crypto-key","mode":"ATTACHED"}'
curl -s -X POST localhost:8080/api/v1/crypto/verify \
  -d '{"signature":"<signature из ответа>","mode":"ATTACHED"}'
```

## Как тестировать

```bash
go build ./...
go vet ./...
go test ./...
```

Тесты: `internal/crypto` (round-trip encrypt/decrypt, sign/verify в обоих режимах, известные
SHA-256 векторы, порча данных/просроченный сертификат → `valid: false`), `internal/keystore`
(генерация/сохранение/перезагрузка алиаса с диска, дубликат алиаса, неверный пароль),
`internal/httpapi` (HTTP-уровень целиком: `httptest` + настоящая sqlite во временном файле -
generate-keystore → sign/verify, encrypt/decrypt round-trip, hash, коды ошибок 400/422,
`operations` list/detail). Крипто- и keystore-тесты бьют по настоящим `crypto/rsa` реализациям,
моков нет.

Руками - тот же curl-сценарий, что выше, либо `/api/v1/fetch/document` с любым HTTPS URL
(`https://httpbin.org/json` подходит).

## Структура

```
cmd/server/            # main.go: конфиг, sqlite, keystore, роутер, ListenAndServe
internal/
├── config/             # переменные окружения
├── httpapi/             # маршрутизация, handler'ы, маппинг ошибок → HTTP-статус
├── service/              # бизнес-логика, аудит (audit.Run), генерация ключей
├── crypto/                # чистые крипто-функции на байтах, без I/O
├── keystore/               # PEM+AES-GCM хранилище ключей на диске
├── repository/              # database/sql поверх sqlite, руками написанные запросы
└── model/                    # доменные типы (CryptoOperation, OperationType, ...)
migrations/
└── schema.sql                # DDL, встраивается в бинарник через go:embed
Dockerfile                     # multi-stage: golang:1.25-alpine build → alpine:3.20 runtime
docker-compose.yml
```

## БД

SQLite-файл (`DB_PATH`, по умолчанию `./data/cryptodb.sqlite`). Таблицы те же четыре, что в
Java: `crypto_operations` - общий лог всех операций, плюс детали по типу - `signature_details`,
`encryption_details`, `fetch_details`. Схема накатывается один раз при старте (`CREATE TABLE IF
NOT EXISTS`), отдельного инструмента миграций нет - для такого масштаба избыточно.

## Архитектура

**Слоистая архитектура**, тот же принцип, что в Java: `httpapi -> service -> (crypto | keystore |
repository) -> model`, зависимость всегда вниз, никакой бизнес-логики в handler'ах.

## Форматы данных

Три байт-маркера — везде один и тот же приём: тип/версия в первом байте блоба, чтобы читающая
сторона знала, как разбирать остальное, без внешней схемы.

- **`filenamePackMarker = 0x01`** (`internal/service/encryption_service.go`) — тот же приём, что в
  Java-версии (`FORMAT_MARKER_PACKED_FILENAME`): `[1B marker][4B BE uint32 name length][name][content]`.
  Упаковывается в `packWithFilename` перед `crypto.Encrypt`, распаковывается в `unpackFilename` после
  `crypto.Decrypt`. Если первый байт не `0x01` — считается, что имени не было, весь payload отдаётся
  как content
- **`envelopeVersion = 0x01`** (`internal/crypto/envelope.go`) — версия формата шифр-конверта:
  `[1B version][2B len(wrappedKey)][wrappedKey][12B nonce][ciphertext+tag]`. `Decrypt` сверяет
  первый байт, при несовпадении — `ErrMalformedEnvelope`
- **`SignModeAttached = 0x00` / `SignModeDetached = 0x01`** (`internal/crypto/signature.go`) —
  режим в первом байте конверта подписи: `[1B mode][2B len(certDER)][certDER][2B len(sig)][sig][data,
  только если ATTACHED]`. `Verify` читает этот байт вместо отдельного параметра — конверт сам
  описывает, в каком режиме он был создан

В Java маркер только один — для filename, потому что это единственное, что CMS `EnvelopedData`/
`SignedData` (BouncyCastle) сами по себе не хранят.

## Переменные окружения

| Переменная          | Что                        | Обязательна                                |
|---------------------|----------------------------|--------------------------------------------|
| `KEYSTORE_PASSWORD` | пароль от keystore         | да                                         |
| `KEYSTORE_PATH`     | путь к директории keystore | нет, по умолчанию `./certs`                |
| `DB_PATH`           | путь к sqlite-файлу        | нет, по умолчанию `./data/cryptodb.sqlite` |
| `PORT`              | HTTP-порт                  | нет, по умолчанию `8080`                   |

## Диаграммы

Компонентная схема.

```mermaid
classDiagram
    namespace httpapi {
        class AdminHandler
        class SignatureHandler
        class EncryptionHandler
        class HashHandler
        class FetchHandler
        class OperationsHandler
    }
    namespace service {
        class KeyGenerationService
        class SignatureService
        class EncryptionService
        class HashService
        class FetchService
        class OperationsService
        class AuditService
    }
    namespace crypto {
        class Envelope["envelope.go (Encrypt/Decrypt)"]
        class Signature["signature.go (Sign/Verify)"]
        class Digest["digest.go (SHA256Hex)"]
    }
    namespace keystore {
        class Manager
    }
    namespace repository {
        class OperationsRepository
        class EncryptionDetailRepository
        class SignatureDetailRepository
        class FetchDetailRepository
    }

    AdminHandler --> KeyGenerationService
    SignatureHandler --> SignatureService
    EncryptionHandler --> EncryptionService
    HashHandler --> HashService
    FetchHandler --> FetchService
    OperationsHandler --> OperationsService

    EncryptionService --> Envelope
    EncryptionService --> Manager : Decrypt() only
    SignatureService --> Signature
    SignatureService --> Manager : Sign() only
    HashService --> Digest
    KeyGenerationService --> Manager : StoreKeyEntry()

    KeyGenerationService --> AuditService : audit.Run
    EncryptionService --> AuditService : audit.Run
    SignatureService --> AuditService : audit.Run
    HashService --> AuditService : audit.Run
    FetchService --> AuditService : audit.Run
    AuditService --> OperationsRepository

    EncryptionService --> EncryptionDetailRepository : Encrypt() only
    SignatureService --> SignatureDetailRepository
    FetchService --> FetchDetailRepository
    OperationsService --> OperationsRepository
    OperationsService --> SignatureDetailRepository
    OperationsService --> EncryptionDetailRepository
    OperationsService --> FetchDetailRepository
```

### POST /api/v1/admin/generate-keystore

```mermaid
sequenceDiagram
    actor Client
    participant Handler as AdminHandler
    participant Service as KeyGenerationService
    participant Audit as AuditService
    participant Keystore as keystore.Manager
    participant DB

    Client->>Handler: POST /api/v1/admin/generate-keystore
    Handler->>Service: Generate(ctx, alias, cn, validityDays)
    Service->>Audit: CreatePending(KEY_GENERATION, alias)
    Audit->>DB: INSERT crypto_operations (PENDING)
    Service->>Service: RSA-2048 + самоподписанный x509.Certificate
    Service->>Keystore: StoreKeyEntry(alias, privKey, cert)
    Keystore->>Keystore: mu.Lock()
    alt alias уже существует
        Keystore-->>Service: ErrAliasExists
        Service->>Audit: MarkFailed(op, "KEY_GENERATION_FAILED")
        Audit->>DB: UPDATE crypto_operations (FAILED)
        Service-->>Handler: *CryptoOperationError
        Handler-->>Client: 422 CRYPTO_OPERATION_FAILED
    else alias свободен
        Keystore->>Keystore: writeEntryFile() - temp-файл + os.Rename
        Keystore-->>Service: nil
        Service->>Audit: MarkSuccess(op, certDER)
        Audit->>DB: UPDATE crypto_operations (SUCCESS)
        Service-->>Handler: GenerateKeystoreResult
        Handler-->>Client: 201 Created
    end
```

### POST /api/v1/crypto/sign

```mermaid
sequenceDiagram
    actor Client
    participant Handler as SignatureHandler
    participant Service as SignatureService
    participant Audit as AuditService
    participant Keystore as keystore.Manager
    participant Crypto as crypto.Sign
    participant DetailRepo as SignatureDetailRepository
    participant DB

    Client->>Handler: POST /api/v1/crypto/sign
    Handler->>Service: Sign(ctx, data, keyAlias, mode)
    Service->>Audit: CreatePending(SIGN, keyAlias)
    Audit->>DB: INSERT crypto_operations (PENDING)
    Service->>Keystore: GetPrivateKey(alias) + GetCertificate(alias)
    alt alias не найден
        Keystore-->>Service: ErrKeyAliasNotFound
        Service->>Audit: MarkFailed(op, "SIGN_FAILED")
        Audit->>DB: UPDATE crypto_operations (FAILED)
        Service-->>Handler: ErrKeyAliasNotFound
        Handler-->>Client: 422 KEY_ALIAS_NOT_FOUND
    else alias найден
        Keystore-->>Service: PrivateKey + Certificate
        Service->>Crypto: Sign(data, privKey, cert, attached)
        Crypto-->>Service: envelope (mode+cert+sig[+data])
        Service->>DetailRepo: Save(SignatureDetail)
        DetailRepo->>DB: INSERT signature_details
        Service->>Audit: MarkSuccess(op, envelope)
        Audit->>DB: UPDATE crypto_operations (SUCCESS)
        Service-->>Handler: SignResult
        Handler-->>Client: 200 OK
    end
```

### POST /api/v1/crypto/verify

```mermaid
sequenceDiagram
    actor Client
    participant Handler as SignatureHandler
    participant Service as SignatureService
    participant Audit as AuditService
    participant Crypto as crypto.Verify
    participant DetailRepo as SignatureDetailRepository
    participant DB

    Client->>Handler: POST /api/v1/crypto/verify
    Handler->>Service: Verify(ctx, signature, data, mode)
    Service->>Audit: CreatePending(VERIFY)
    Audit->>DB: INSERT crypto_operations (PENDING)
    Service->>Crypto: Verify(envelope, data?, attached)
    alt конверт повреждён / DETACHED без data
        Crypto-->>Service: ErrMalformedEnvelope / ErrDetachedDataRequired
        Service->>Audit: MarkFailed(op, "VERIFY_FAILED")
        Audit->>DB: UPDATE crypto_operations (FAILED)
        Service-->>Handler: *CryptoOperationError
        Handler-->>Client: 422 CRYPTO_OPERATION_FAILED
    else конверт разобран (Valid true или false)
        Crypto-->>Service: VerifyResult{Valid, Cert}
        Service->>DetailRepo: Save(SignatureDetail)
        DetailRepo->>DB: INSERT signature_details
        Service->>Audit: MarkSuccess(op, nil)
        Audit->>DB: UPDATE crypto_operations (SUCCESS)
        Service-->>Handler: VerifyResult
        Handler-->>Client: 200 OK
    end
```

`valid: false` - тоже 200, не ошибка (просроченный сертификат подписанта или несходящаяся подпись).
FAILED - только для действительно битого конверта. Сертификат подписанта берётся из тела конверта,
`keystore.Manager` не участвует.

### POST /api/v1/crypto/encrypt

```mermaid
sequenceDiagram
    actor Client
    participant Handler as EncryptionHandler
    participant Service as EncryptionService
    participant Audit as AuditService
    participant Crypto as crypto.Encrypt
    participant DetailRepo as EncryptionDetailRepository
    participant DB

    Client->>Handler: POST /api/v1/crypto/encrypt
    Handler->>Service: Encrypt(ctx, plaintext, recipientCert, filename)
    Service->>Audit: CreatePending(ENCRYPT)
    Audit->>DB: INSERT crypto_operations (PENDING)
    Service->>Service: x509.ParseCertificate + проверка NotBefore/NotAfter
    alt сертификат просрочен / ещё не валиден / не парсится
        Service->>Audit: MarkFailed(op, "ENCRYPT_FAILED")
        Audit->>DB: UPDATE crypto_operations (FAILED)
        Service-->>Handler: *CryptoOperationError
        Handler-->>Client: 422 CRYPTO_OPERATION_FAILED
    else всё ок
        Service->>Service: packWithFilename(plaintext, filename)
        Service->>Crypto: Encrypt(packed, cert) - RSA-OAEP + AES-256-GCM
        Crypto-->>Service: envelope
        Service->>DetailRepo: Save(EncryptionDetail)
        DetailRepo->>DB: INSERT encryption_details
        Service->>Audit: MarkSuccess(op, envelope)
        Audit->>DB: UPDATE crypto_operations (SUCCESS)
        Service-->>Handler: EncryptResult
        Handler-->>Client: 200 OK
    end
```

### POST /api/v1/crypto/decrypt

```mermaid
sequenceDiagram
    actor Client
    participant Handler as EncryptionHandler
    participant Service as EncryptionService
    participant Audit as AuditService
    participant Keystore as keystore.Manager
    participant Crypto as crypto.Decrypt
    participant DB

    Client->>Handler: POST /api/v1/crypto/decrypt
    Handler->>Service: Decrypt(ctx, ciphertext, keyAlias)
    Service->>Audit: CreatePending(DECRYPT, keyAlias)
    Audit->>DB: INSERT crypto_operations (PENDING)
    Service->>Keystore: GetPrivateKey(keyAlias)
    alt alias не найден
        Keystore-->>Service: ErrKeyAliasNotFound
        Service->>Audit: MarkFailed(op, "DECRYPT_FAILED")
        Audit->>DB: UPDATE crypto_operations (FAILED)
        Service-->>Handler: ErrKeyAliasNotFound
        Handler-->>Client: 422 KEY_ALIAS_NOT_FOUND
    else alias найден
        Keystore-->>Service: PrivateKey
        Service->>Crypto: Decrypt(envelope, privKey)
        Crypto-->>Service: packed (RSA-OAEP unwrap + AES-GCM open)
        Service->>Service: unpackFilename() - достаёт исходное имя файла
        Service->>Audit: MarkSuccess(op, content)
        Audit->>DB: UPDATE crypto_operations (SUCCESS)
        Service-->>Handler: DecryptResult
        Handler-->>Client: 200 OK
    end
```

В отличие от `encrypt`, `decrypt` не пишет в `EncryptionDetailRepository` - деталь сохраняется один
раз, на шаге шифрования.

### POST /api/v1/crypto/hash

```mermaid
sequenceDiagram
    actor Client
    participant Handler as HashHandler
    participant Service as HashService
    participant Audit as AuditService
    participant DB

    Client->>Handler: POST /api/v1/crypto/hash
    Handler->>Service: Hash(ctx, data)
    Service->>Audit: CreatePending(HASH)
    Audit->>DB: INSERT crypto_operations (PENDING)
    Service->>Service: crypto.SHA256Hex(data)
    Service->>Audit: MarkSuccess(op, hash-bytes)
    Audit->>DB: UPDATE crypto_operations (SUCCESS)
    Service-->>Handler: HashResult
    Handler-->>Client: 200 OK
```

Своей detail-таблицы у `HASH` нет - результат целиком в `crypto_operations.output_hash`.

### POST /api/v1/fetch/document

```mermaid
sequenceDiagram
    actor Client
    participant Handler as FetchHandler
    participant Service as FetchService
    participant Audit as AuditService
    participant HTTPClient as net/http.Client
    participant DetailRepo as FetchDetailRepository
    participant DB
    participant External as внешний HTTPS-сервер

    Client->>Handler: POST /api/v1/fetch/document
    Handler->>Service: Fetch(ctx, url, timeoutSeconds)
    Service->>Service: validateFetchURL() - https-схема / net.ResolveIPAddr / private-IP
    alt URL невалиден
        Service-->>Handler: *ValidationError
        Handler-->>Client: 400 INVALID_REQUEST
        note over Service,Audit: аудит-запись не создаётся - падает до audit.Run
    else URL валиден
        Service->>Audit: CreatePending(FETCH, url-bytes)
        Audit->>DB: INSERT crypto_operations (PENDING)
        Service->>HTTPClient: GET (CheckRedirect: не следовать)
        HTTPClient->>External: HTTPS GET
        External-->>HTTPClient: response
        alt редирект (3xx) или превышен Content-Length
            Service->>Audit: MarkFailed(op, "FETCH_FAILED")
            Audit->>DB: UPDATE crypto_operations (FAILED)
            Service-->>Handler: *NetworkError
            Handler-->>Client: 502 NETWORK_ERROR
        else обычный ответ
            Service->>DetailRepo: Save(FetchDetail)
            DetailRepo->>DB: INSERT fetch_details
            Service->>Audit: MarkSuccess(op, body)
            Audit->>DB: UPDATE crypto_operations (SUCCESS)
            Service-->>Handler: FetchResult
            Handler-->>Client: 200 OK
        end
    end
```

### GET /api/v1/operations

```mermaid
sequenceDiagram
    actor Client
    participant Handler as OperationsHandler
    participant Service as OperationsService
    participant Repo as OperationsRepository
    participant DB

    Client->>Handler: GET /api/v1/operations?page&size&type
    Handler->>Service: List(ctx, page, size, type)
    alt type не из допустимого набора
        Service-->>Handler: *ValidationError
        Handler-->>Client: 400 INVALID_REQUEST
    else type валиден (или не передан)
        Service->>Repo: List(ctx, type, page, cappedSize)
        Repo->>DB: SELECT ... ORDER BY created_at DESC LIMIT/OFFSET + COUNT(*)
        DB-->>Repo: страница строк + total
        Repo-->>Service: []CryptoOperation, total
        Service-->>Handler: []OperationRecord, total
        Handler-->>Client: 200 OK
    end
```

Read-only ручка над аудит-логом - сама новых записей не создаёт, через `audit.Run` не идёт.

### GET /api/v1/operations/{id}

```mermaid
sequenceDiagram
    actor Client
    participant Handler as OperationsHandler
    participant Service as OperationsService
    participant Repo as OperationsRepository
    participant DetailRepo as Signature/Encryption/FetchDetailRepository
    participant DB

    Client->>Handler: GET /api/v1/operations/{id}
    Handler->>Service: GetByID(ctx, id)
    Service->>Repo: FindByID(ctx, id)
    Repo->>DB: SELECT crypto_operations WHERE id = ?
    alt не найдено
        DB-->>Repo: sql.ErrNoRows
        Repo-->>Service: nil, nil
        Service-->>Handler: nil, nil
        Handler-->>Client: 404 Not Found
    else найдено
        DB-->>Repo: строка
        Repo-->>Service: *CryptoOperation
        Service->>Service: switch op.Type - выбор detail-репозитория
        Service->>DetailRepo: FindByID(ctx, id)
        DetailRepo->>DB: SELECT signature_details / encryption_details / fetch_details
        DB-->>DetailRepo: строка или sql.ErrNoRows
        DetailRepo-->>Service: *Detail или nil
        Service-->>Handler: *OperationDetail
        Handler-->>Client: 200 OK
    end
```

Для `HASH`/`KEY_GENERATION` detail-репозиторий не опрашивается (нет ветки в `switch`) - только
общие поля.

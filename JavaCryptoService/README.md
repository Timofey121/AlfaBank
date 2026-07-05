# Java Crypto Service

Тестовое задание: REST-сервис на Java + Spring Boot, который умеет подписывать документы (PKCS#7),
шифровать/расшифровывать (RSA+AES-GCM), считать SHA-256 и тянуть файлы по HTTPS. Сверху - простой Web UI,
чтобы всё это можно было потыкать руками, не собирая curl-команды вручную.

## Стек

Java 17, Spring Boot 3.3.5 (Tomcat из коробки), Bouncy Castle 1.78.1 для крипты, H2 в файловом режиме +
JPA + Flyway под миграции, Swagger UI сверху. Собирается Maven'ом, есть Dockerfile и docker-compose.

## Что нужно для запуска

Java 17+ и Maven 3.8+, либо просто Docker.

## Запуск

Пароль от keystore берётся из переменной окружения - в коде и конфигах его нет:

```bash
export KEYSTORE_PASSWORD=changeit
mvn spring-boot:run
```

Или через Docker Compose:

```bash
KEYSTORE_PASSWORD=changeit docker-compose up --build
```

Поднимается на `http://localhost:8080`. Если файла keystore ещё нет - сервис стартует с
пустым хранилищем, ключ можно сгенерировать позже через API (или прямо из UI).

Сгенерировать ключ можно так:

```bash
curl -s -X POST http://localhost:8080/api/v1/admin/generate-keystore \
  -H "Content-Type: application/json" \
  -d '{"alias":"crypto-key","cn":"CryptoService","validityDays":365}' | jq .
```

В ответе будет `certBase64` - DER-сертификат, он же потом идёт как `recipientCertificate` при шифровании.

Важный момент: `/api/v1/admin/...` специально оставлен без какой-либо аутентификации. Для тестового
задания это нормально, но не для прода - там ключи генерятся офлайн (keytool/openssl), хранятся в
чём-то вроде Vault и никогда не показываются в теле ответа.

## Web UI

По адресу `http://localhost:8080/` находится простая страничка на чистом HTML/JS - без сборки, без npm.
На ней вкладки под каждую операцию (Encrypt, Decrypt, Sign, Verify, Hash, Fetch, Keystore), в каждую можно либо
загрузить файл, либо просто вставить текст - base64 руками кодировать не надо, этим занимается отдельная ручка
`/api/v1/convert/encode`. Результат (шифртекст, подпись и т.д.) можно сразу скачать файлом через
`/api/v1/convert/decode`.

Пара оговорок по этим convert-ручкам:

- в аудит (`crypto_operations`) они не попадают - это не крипто-операция, а просто конвертация для
  удобства UI-
- на проде в `encode` логично было бы ещё сохранять оригинальный файл в S3 (или другое объектное
  хранилище) со ссылкой на операцию, а `decode` - отдавать presigned URL, а не сырые байты. Для теста
  это явно лишнее, поэтому это не было реализовано
- и самое главное - сейчас они, как и всё остальное, открыты без авторизации. На проде так нельзя:
  через `encode` можно закачать что угодно и прогнать в base64 без всякого контроля, так что эти ручки
  нужно закрывать той же аутентификацией, что и весь остальной API, а не оставлять открытыми

## API и эндпоинты

Swagger: `http://localhost:8080/swagger-ui.html`
H2-консоль: `http://localhost:8080/h2-console` (JDBC URL `jdbc:h2:file:./data/cryptodb`)

| Метод | URL                               | Что делает                                         |
|-------|-----------------------------------|----------------------------------------------------|
| POST  | `/api/v1/admin/generate-keystore` | генерит RSA-2048 ключ + самоподписанный сертификат |
| POST  | `/api/v1/crypto/sign`             | подпись PKCS#7                                     |
| POST  | `/api/v1/crypto/verify`           | проверка подписи                                   |
| POST  | `/api/v1/crypto/encrypt`          | шифрование RSA+AES-GCM                             |
| POST  | `/api/v1/crypto/decrypt`          | расшифровка                                        |
| POST  | `/api/v1/crypto/hash`             | SHA-256                                            |
| POST  | `/api/v1/fetch/document`          | скачать документ по HTTPS                          |
| GET   | `/api/v1/operations`              | история операций (пагинация, фильтр по типу)       |
| GET   | `/api/v1/operations/{id}`         | одна операция по id                                |
| POST  | `/api/v1/convert/encode`          | файл/текст → base64, для UI                        |
| POST  | `/api/v1/convert/decode`          | base64 → файл на скачивание, для UI                |

## Как тестировать

Автотесты гоняются обычным `mvn test` - контроллеры проверены через MockMvc, сервисы замоканы Mockito,
а для крипто-провайдеров есть отдельные тесты (например для SHA-256).

Руками проще всего проверить через сам Web UI. Зайти на `http://localhost:8080/` и по порядку:

1. Вкладка **Keystore** - генеришь ключ (alias, скажем, `crypto-key`), из ответа сохранять `certBase64`,
   он ещё понадобится. Можно сразу скачать сертификат кнопкой.
2. **Encrypt** - текст произвольный, сертификат вставляешь как base64 (или файлом, если скачал на шаге 1).
   Получаешь ciphertext.
3. **Decrypt** - тот же ciphertext, тот же alias - должен вернуться исходный текст.
4. **Sign** - подписываешь текст или файл тем же alias, режим ATTACHED или DETACHED.
5. **Verify** - подпись из прошлого шага, для DETACHED ещё и исходный документ - должно вернуть `valid: true`.
6. **Hash** - просто текст или файл, получаешь SHA-256.
7. **Fetch** - любой HTTPS URL (`jsonplaceholder.typicode.com/todos/1` вполне подойдёт: первый запрос к нему - выдаст
   ошибку, последующие сработают как надо - такой url был выбран для показа обработки такой ошибки) - видно контент,
   тип, статус, можно скачать.

Единственное, что реально важно по порядку - alias должен существовать до того, как будут дергаться
Encrypt/Decrypt/Sign. Остальное можно тыкать в любой последовательности.

## Структура

```
src/main/java/com/alfabank/crypto/
├── config/          # BC-провайдер, keystore, OpenAPI
├── controller/      # REST-контроллеры
├── service/         # бизнес-логика, аудит, генерация ключей
├── crypto/          # чистые крипто-провайдеры на байтах, без I/O
├── keystore/        # загрузка ключей из P12
├── model/           # JPA-сущности
├── repository/      # Spring Data репозитории
├── dto/             # request/response объекты
└── exception/       # исключения + глобальный handler

src/main/resources/
├── application.yml
├── static/          # Web UI (index.html, style.css, app.js)
└── db/migration/    # Flyway
```

## БД

H2 в файле, `./data/cryptodb`. Таблиц четыре: `crypto_operations` - общий лог всех операций, плюс
детали по каждому типу - `signature_details`, `encryption_details`, `fetch_details`.

## Архитектура

**Слоистая архитектура**, паттерн **Controller-Service-Repository**

**Паттерны, которые тут есть:**

- **Strategy** в `crypto/` - у каждой крипто-операции (encrypt/decrypt/sign/verify/hash)
  свой маленький `@Component` с одним публичным методом, без состояния, помеченный маркер-интерфейсом
  `CryptoProvider`. Роль везде одна: взять байты на входе, отдать байты (или
  небольшой результат) на выходе, ничего не знать о HTTP или БД
- **`OperationType` как единый источник правды** - семь типов операций (`ENCRYPT`/`DECRYPT`/`SIGN`/
  `VERIFY`/`HASH`/`FETCH`/`KEY_GENERATION`) объявлены один раз в `model/OperationType.java`.
  `AuditedOperation.run(...)` принимает `OperationType`, а не голую строку, поэтому опечатка в
  типе операции не компилируется. `OperationsService` тоже валидирует `?type=` и диспетчерит
  detail-репозиторий через `OperationType.valueOf(...)`, а не через отдельно поддерживаемый набор строк
- **Единственная точка записи в keystore** - `KeystoreManager.storeKeyEntry(...)` (под тем же
  write-lock'ом, что и чтения). `KeyGenerationService` генерит ключевую пару и сертификат (BC), но
  сам `KeyStore`-бин и файл больше не трогает
- **DTO через статическую фабрику** - response-объекты (Java record'ы) собираются методом
  `from(...)`, который превращает JPA-сущность в плоский DTO. Запросы - тоже record'ы с
  Bean Validation, некоторые сами проставляют дефолты в компактном конструкторе (`SignRequest`
  дефолтит `mode` в `ATTACHED`, `DecodeRequest` - `filename` в `result.bin`).
- **Repository** - обычные `JpaRepository<T, String>`, почти без кастомных запросов
- **Единая обработка ошибок** - один `@RestControllerAdvice` (`GlobalExceptionHandler`) на все
  контроллеры, у каждого типа исключения свой `@ExceptionHandler` и свой HTTP-статус, но формат тела
  ответа всегда один и тот же (`code`/`message`/`timestamp`). Иерархия исключений плоская:
  `CryptoAppException` (RuntimeException) как корень, от неё - `CryptoOperationException`,
  `KeystoreException` (и её частный случай `KeyAliasNotFoundException`), `NetworkException`

## Форматы данных

Байт-маркер **`FORMAT_MARKER_PACKED_FILENAME = 0x01`** (`EncryptionService.packWithFilename`/
`unpackFilename`) — CMS `EnvelopedData` сам по себе не хранит оригинальное имя файла, поэтому
перед шифрованием сервис упаковывает `[1B marker][4B int name length][name UTF-8][content]` и
только это отдаёт в `CmsEncryptionProvider`. При расшифровке `unpackFilename` смотрит на первый
байт: `0x01` — значит имя есть, распаковывает; иначе (или контент короче заголовка) — отдаёт
контент как есть, без имени. Сам `EnvelopedData` про этот формат ничего не знает — упаковка
целиком на уровне `EncryptionService`, до и после вызова крипто-провайдера.

Больше маркеров тут нет и не нужно: и `EnvelopedData` (encrypt), и `SignedData` (sign) — готовые
ASN.1-структуры из BouncyCastle, самоописывающиеся сами по себе (ATTACHED/DETACHED в `SignedData`
определяется наличием encapsulated content, а не отдельным полем). В [GoCryptoService](../GoCryptoService)
своего формата CMS нет, поэтому там такими же маркерами размечены ещё и сам конверт шифрования, и
конверт подписи — см. `GoCryptoService/README.md`, раздел «Форматы данных».

## Диаграммы

Компонентная схема.

```mermaid
classDiagram
    namespace controller {
        class AdminController
        class ConvertController
        class EncryptionController
        class FetchController
        class HashController
        class OperationsController
        class SignatureController
    }
    namespace service {
        class KeyGenerationService
        class ConvertService
        class EncryptionService
        class FetchService
        class HashService
        class SignatureService
        class OperationsService
    }
    namespace audit {
        class AuditedOperation
        class AuditService
    }
    namespace crypto {
        class CmsEncryptionProvider
        class CmsDecryptionProvider
        class CmsSignatureProvider
        class CmsVerificationProvider
        class DigestProvider
    }
    namespace keystore {
        class KeystoreManager
    }
    namespace repository {
        class CryptoOperationRepository
        class EncryptionDetailRepository
        class SignatureDetailRepository
        class FetchDetailRepository
    }

    AdminController --> KeyGenerationService
    ConvertController --> ConvertService
    EncryptionController --> EncryptionService
    FetchController --> FetchService
    HashController --> HashService
    OperationsController --> OperationsService
    SignatureController --> SignatureService

    EncryptionService --> CmsEncryptionProvider
    EncryptionService --> CmsDecryptionProvider
    EncryptionService --> KeystoreManager : decrypt() only
    SignatureService --> CmsSignatureProvider
    SignatureService --> CmsVerificationProvider
    SignatureService --> KeystoreManager : sign() only
    HashService --> DigestProvider
    KeyGenerationService --> KeystoreManager : storeKeyEntry()

    KeyGenerationService --> AuditedOperation
    EncryptionService --> AuditedOperation
    SignatureService --> AuditedOperation
    HashService --> AuditedOperation
    FetchService --> AuditedOperation
    AuditedOperation --> AuditService
    AuditService --> CryptoOperationRepository

    EncryptionService --> EncryptionDetailRepository : encrypt() only
    SignatureService --> SignatureDetailRepository
    FetchService --> FetchDetailRepository
    OperationsService --> CryptoOperationRepository
    OperationsService --> SignatureDetailRepository
    OperationsService --> EncryptionDetailRepository
    OperationsService --> FetchDetailRepository
```

### POST /api/v1/admin/generate-keystore

```mermaid
sequenceDiagram
    actor Client
    participant Controller as AdminController
    participant Service as KeyGenerationService
    participant AO as AuditedOperation
    participant Audit as AuditService
    participant Keystore as KeystoreManager
    participant KeyStore as KeyStore (P12, память + файл)
    participant DB

    Client->>Controller: POST /api/v1/admin/generate-keystore
    Controller->>Service: generate(request)
    Service->>AO: run(OperationType.KEY_GENERATION, alias, action)
    AO->>Audit: createPending("KEY_GENERATION", keyAlias=alias)
    Audit->>DB: INSERT crypto_operation (PENDING)
    AO->>Service: execute action(op)
    Service->>Service: RSA-2048 keypair + самоподписанный сертификат (BC)
    Service->>Keystore: storeKeyEntry(alias, privateKey, cert)
    Keystore->>Keystore: writeLock()
    alt alias уже существует
        Keystore-->>Service: throw CryptoOperationException
        Service-->>AO: propagate
        AO->>Audit: markFailed(op, "KEY_GENERATION_FAILED")
        Audit->>DB: UPDATE crypto_operation (FAILED)
        AO-->>Service: rethrow
        Service-->>Controller: throw CryptoOperationException
        Controller-->>Client: 422 UNPROCESSABLE_ENTITY
    else alias свободен
        Keystore->>KeyStore: setKeyEntry(alias, privateKey, cert)
        Keystore->>KeyStore: persistAtomically() - temp-файл + atomic move
        Keystore-->>Service: void
        Service-->>AO: Outcome(response, certBytes)
        AO->>Audit: markSuccess(op, certBytes)
        Audit->>DB: UPDATE crypto_operation (SUCCESS)
        AO-->>Service: response
        Service-->>Controller: GenerateKeystoreResponse
        Controller-->>Client: 201 CREATED
    end
```

### POST /api/v1/crypto/sign

```mermaid
sequenceDiagram
    actor Client
    participant Controller as SignatureController
    participant Service as SignatureService
    participant AO as AuditedOperation
    participant Audit as AuditService
    participant Keystore as KeystoreManager
    participant Provider as CmsSignatureProvider
    participant DetailRepo as SignatureDetailRepository
    participant DB

    Client->>Controller: POST /api/v1/crypto/sign
    Controller->>Service: sign(request)
    Service->>AO: run(OperationType.SIGN, keyAlias, action)
    AO->>Audit: createPending("SIGN", keyAlias)
    Audit->>DB: INSERT crypto_operation (PENDING)
    AO->>Service: execute action(op)
    Service->>Keystore: getPrivateKey(keyAlias) + getCertificate(keyAlias)
    alt alias не найден
        Keystore-->>Service: throw KeyAliasNotFoundException
        Service-->>AO: propagate
        AO->>Audit: markFailed(op, "SIGN_FAILED")
        Audit->>DB: UPDATE crypto_operation (FAILED)
        AO-->>Service: rethrow
        Service-->>Controller: throw KeyAliasNotFoundException
        Controller-->>Client: 422 UNPROCESSABLE_ENTITY
    else alias найден
        Keystore-->>Service: PrivateKey + X509Certificate
        Service->>Provider: sign(data, privateKey, cert, attached)
        Provider-->>Service: signature (CMS SignedData)
        Service->>DetailRepo: save(SignatureDetail)
        DetailRepo->>DB: INSERT signature_detail
        Service-->>AO: Outcome(response, signature)
        AO->>Audit: markSuccess(op, signature)
        Audit->>DB: UPDATE crypto_operation (SUCCESS)
        AO-->>Service: response
        Service-->>Controller: SignResponse
        Controller-->>Client: 200 OK
    end
```

### POST /api/v1/crypto/verify

```mermaid
sequenceDiagram
    actor Client
    participant Controller as SignatureController
    participant Service as SignatureService
    participant AO as AuditedOperation
    participant Audit as AuditService
    participant Provider as CmsVerificationProvider
    participant DetailRepo as SignatureDetailRepository
    participant DB

    Client->>Controller: POST /api/v1/crypto/verify
    Controller->>Service: verify(request)
    Service->>AO: run(OperationType.VERIFY, action)
    AO->>Audit: createPending("VERIFY")
    Audit->>DB: INSERT crypto_operation (PENDING)
    AO->>Service: execute action(op)
    Service->>Provider: verify(signature, data?, attached)
    alt CMS-конверт повреждён / не парсится
        Provider-->>Service: throw exception
        Service-->>AO: propagate
        AO->>Audit: markFailed(op, "VERIFY_FAILED")
        Audit->>DB: UPDATE crypto_operation (FAILED)
        AO-->>Service: rethrow
        Service-->>Controller: throw CryptoOperationException
        Controller-->>Client: 422 UNPROCESSABLE_ENTITY
    else подпись разобрана (valid true или false)
        Provider-->>Service: VerifyResult(valid, signerCert)
        Service->>DetailRepo: save(SignatureDetail)
        DetailRepo->>DB: INSERT signature_detail
        Service-->>AO: Outcome(response)
        AO->>Audit: markSuccess(op, null)
        Audit->>DB: UPDATE crypto_operation (SUCCESS)
        AO-->>Service: response
        Service-->>Controller: VerifyResponse (valid: true/false)
        Controller-->>Client: 200 OK
    end
```

`valid: false` - тоже 200, не ошибка (подпись просто не сошлась). FAILED - только для битого
CMS-конверта. Сертификат подписанта берётся из тела CMS, `KeystoreManager` тут не участвует.

### POST /api/v1/crypto/encrypt

```mermaid
sequenceDiagram
    actor Client
    participant Controller as EncryptionController
    participant Service as EncryptionService
    participant AO as AuditedOperation
    participant Audit as AuditService
    participant Provider as CmsEncryptionProvider
    participant DetailRepo as EncryptionDetailRepository
    participant DB

    Client->>Controller: POST /api/v1/crypto/encrypt
    Controller->>Service: encrypt(request)
    Service->>AO: run(OperationType.ENCRYPT, action)
    AO->>Audit: createPending("ENCRYPT")
    Audit->>DB: INSERT crypto_operation (PENDING)
    AO->>Service: execute action(op)
    Service->>Service: parseCert(recipientCertificate) + checkValidity()
    alt сертификат просрочен / ещё не валиден
        Service-->>AO: throw CryptoOperationException
        AO->>Audit: markFailed(op, "ENCRYPT_FAILED")
        Audit->>DB: UPDATE crypto_operation (FAILED)
        AO-->>Service: rethrow
        Service-->>Controller: throw CryptoOperationException
        Controller-->>Client: 422 UNPROCESSABLE_ENTITY
    else всё ок
        Service->>Provider: encrypt(packed, recipientCert)
        Provider-->>Service: ciphertext (CMS EnvelopedData, AES-256-GCM)
        Service->>DetailRepo: save(EncryptionDetail)
        DetailRepo->>DB: INSERT encryption_detail
        Service-->>AO: Outcome(response, ciphertext)
        AO->>Audit: markSuccess(op, ciphertext)
        Audit->>DB: UPDATE crypto_operation (SUCCESS)
        AO-->>Service: response
        Service-->>Controller: EncryptResponse
        Controller-->>Client: 200 OK
    end
```

### POST /api/v1/crypto/decrypt

```mermaid
sequenceDiagram
    actor Client
    participant Controller as EncryptionController
    participant Service as EncryptionService
    participant AO as AuditedOperation
    participant Audit as AuditService
    participant Keystore as KeystoreManager
    participant Provider as CmsDecryptionProvider
    participant DB

    Client->>Controller: POST /api/v1/crypto/decrypt
    Controller->>Service: decrypt(request)
    Service->>AO: run(OperationType.DECRYPT, keyAlias, action)
    AO->>Audit: createPending("DECRYPT", keyAlias)
    Audit->>DB: INSERT crypto_operation (PENDING)
    AO->>Service: execute action(op)
    Service->>Keystore: getPrivateKey(keyAlias)
    alt alias не найден
        Keystore-->>Service: throw KeyAliasNotFoundException
        Service-->>AO: propagate
        AO->>Audit: markFailed(op, "DECRYPT_FAILED")
        Audit->>DB: UPDATE crypto_operation (FAILED)
        AO-->>Service: rethrow
        Service-->>Controller: throw KeyAliasNotFoundException
        Controller-->>Client: 422 UNPROCESSABLE_ENTITY
    else alias найден
        Keystore-->>Service: PrivateKey
        Service->>Provider: decrypt(ciphertext, privateKey)
        Provider-->>Service: содержимое (CMS EnvelopedData вскрыт)
        Service->>Service: unpackFilename() - достаёт исходное имя файла
        Service-->>AO: Outcome(response, plaintext)
        AO->>Audit: markSuccess(op, plaintext)
        Audit->>DB: UPDATE crypto_operation (SUCCESS)
        AO-->>Service: response
        Service-->>Controller: DecryptResponse
        Controller-->>Client: 200 OK
    end
```

В отличие от `encrypt`, `decrypt` не пишет в `EncryptionDetailRepository` - деталь сохраняется
только один раз, на шаге шифрования.

### POST /api/v1/crypto/hash

```mermaid
sequenceDiagram
    actor Client
    participant Controller as HashController
    participant Service as HashService
    participant AO as AuditedOperation
    participant Audit as AuditService
    participant Provider as DigestProvider
    participant DB

    Client->>Controller: POST /api/v1/crypto/hash
    Controller->>Service: hash(request)
    Service->>AO: run(OperationType.HASH, action)
    AO->>Audit: createPending("HASH")
    Audit->>DB: INSERT crypto_operation (PENDING)
    AO->>Service: execute action(op)
    Service->>Provider: sha256Hex(data)
    Provider-->>Service: hex-строка хэша
    Service-->>AO: Outcome(response, hashBytes)
    AO->>Audit: markSuccess(op, hashBytes)
    Audit->>DB: UPDATE crypto_operation (SUCCESS)
    AO-->>Service: response
    Service-->>Controller: HashResponse
    Controller-->>Client: 200 OK
```

Своей detail-таблицы у `HASH` нет - результат целиком в `crypto_operations.output_hash`.

### POST /api/v1/fetch/document

```mermaid
sequenceDiagram
    actor Client
    participant Controller as FetchController
    participant Service as FetchService
    participant AO as AuditedOperation
    participant Audit as AuditService
    participant HttpClient as java.net.http.HttpClient
    participant DetailRepo as FetchDetailRepository
    participant DB
    participant External as внешний HTTPS-сервер

    Client->>Controller: POST /api/v1/fetch/document
    Controller->>Service: fetch(request)
    Service->>Service: validateUrl() - схема/DNS/private-IP
    alt URL невалиден
        Service-->>Controller: throw IllegalArgumentException
        Controller-->>Client: 400 BAD_REQUEST
        note over Service,AO: аудит-запись не создаётся - падает до AuditedOperation.run
    else URL валиден
        Service->>AO: run(OperationType.FETCH, url, action)
        AO->>Audit: createPending("FETCH", url)
        Audit->>DB: INSERT crypto_operation (PENDING)
        AO->>Service: execute action(op)
        Service->>HttpClient: GET (Redirect.NEVER)
        HttpClient->>External: HTTPS GET
        External-->>HttpClient: response
        alt редирект (3xx) или превышен размер
            Service-->>AO: throw NetworkException
            AO->>Audit: markFailed(op, "FETCH_FAILED")
            Audit->>DB: UPDATE crypto_operation (FAILED)
            AO-->>Service: rethrow
            Service-->>Controller: throw NetworkException
            Controller-->>Client: 502 BAD_GATEWAY
        else обычный ответ
            Service->>DetailRepo: save(FetchDetail)
            DetailRepo->>DB: INSERT fetch_detail
            Service-->>AO: Outcome(response, body)
            AO->>Audit: markSuccess(op, body)
            Audit->>DB: UPDATE crypto_operation (SUCCESS)
            AO-->>Service: response
            Service-->>Controller: FetchResponse
            Controller-->>Client: 200 OK
        end
    end
```

### GET /api/v1/operations

```mermaid
sequenceDiagram
    actor Client
    participant Controller as OperationsController
    participant Service as OperationsService
    participant Repo as CryptoOperationRepository
    participant DB

    Client->>Controller: GET /api/v1/operations?page&size&type
    Controller->>Service: list(page, size, type)
    alt type не из допустимого набора
        Service-->>Controller: throw IllegalArgumentException
        Controller-->>Client: 400 BAD_REQUEST
    else type валиден (или не передан)
        Service->>Repo: findByType(type, pageable) / findAll(pageable)
        Repo->>DB: SELECT ... ORDER BY created_at DESC LIMIT/OFFSET
        DB-->>Repo: страница строк
        Repo-->>Service: Page<CryptoOperation>
        Service-->>Controller: Page<OperationRecordResponse>
        Controller-->>Client: 200 OK
    end
```

Read-only ручка над аудит-логом - сама новых записей не создаёт, через `AuditedOperation` не идёт.

### GET /api/v1/operations/{id}

```mermaid
sequenceDiagram
    actor Client
    participant Controller as OperationsController
    participant Service as OperationsService
    participant Repo as CryptoOperationRepository
    participant DetailRepo as Signature/Encryption/FetchDetailRepository
    participant DB

    Client->>Controller: GET /api/v1/operations/{id}
    Controller->>Service: getById(id)
    Service->>Repo: findById(id)
    Repo->>DB: SELECT crypto_operation
    alt не найдено
        DB-->>Repo: пусто
        Repo-->>Service: Optional.empty()
        Service-->>Controller: Optional.empty()
        Controller-->>Client: 404 NOT_FOUND
    else найдено
        DB-->>Repo: строка
        Repo-->>Service: CryptoOperation
        Service->>Service: toDetailResponse() - выбор detail-репозитория по op.type
        Service->>DetailRepo: findById(id)
        DetailRepo->>DB: SELECT signature_detail / encryption_detail / fetch_detail
        DB-->>DetailRepo: строка или пусто
        DetailRepo-->>Service: Optional<Detail>
        Service-->>Controller: OperationDetailResponse
        Controller-->>Client: 200 OK
    end
```

Для `HASH`/`KEY_GENERATION` detail-репозиторий не опрашивается (`default -> {}`) - только общие поля.

### POST /api/v1/convert/encode

```mermaid
sequenceDiagram
    actor Client
    participant Controller as ConvertController
    participant Service as ConvertService

    Client->>Controller: POST /api/v1/convert/encode (multipart: file или text)
    Controller->>Service: encode(file, text)
    alt переданы оба параметра или ни одного
        Service-->>Controller: throw IllegalArgumentException
        Controller-->>Client: 400 BAD_REQUEST
    else передан ровно один
        Service->>Service: прочитать байты (file.getBytes() либо text.getBytes(UTF-8))
        Service->>Service: Base64.encode(bytes)
        Service-->>Controller: EncodeResponse
        Controller-->>Client: 200 OK
    end
```

### POST /api/v1/convert/decode

```mermaid
sequenceDiagram
    actor Client
    participant Controller as ConvertController
    participant Service as ConvertService

    Client->>Controller: POST /api/v1/convert/decode
    Controller->>Service: decode(base64)
    alt base64 невалиден
        Service-->>Controller: throw IllegalArgumentException
        Controller-->>Client: 400 BAD_REQUEST
    else base64 валиден
        Service->>Service: Base64.decode(base64)
        Controller->>Service: sanitizeFilename(filename)
        Service->>Service: убрать "/", "\\", ".." - дефолт "result.bin"
        Service-->>Controller: bytes + safeFilename
        Controller-->>Client: 200 OK (application/octet-stream, Content-Disposition: attachment)
    end
```

## Переменные окружения

| Переменная          | Что                    | Обязательна                              |
|---------------------|------------------------|------------------------------------------|
| `KEYSTORE_PASSWORD` | пароль от keystore.p12 | да                                       |
| `KEYSTORE_PATH`     | путь к keystore.p12    | нет, по умолчанию `./certs/keystore.p12` |

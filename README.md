# AlfaBank Crypto Service

Тестовое задание на REST-сервис, умеющий подписывать документы, шифровать/расшифровывать,
считать SHA-256 и тянуть файлы по HTTPS, с аудит-историей всех операций в БД. Реализован дважды,
двумя независимыми стеками:

|               | [`JavaCryptoService/`](JavaCryptoService) | [`GoCryptoService/`](GoCryptoService)                  |
|---------------|-------------------------------------------|--------------------------------------------------------|
| Стек          | Java 17, Spring Boot 3.3.5, Bouncy Castle | Go 1.25, стандартная библиотека                        |
| Крипто-формат | CMS EnvelopedData / SignedData (PKCS#7)   | свой конверт (не CMS, не byte-совместим)               |
| Keystore      | PKCS12 (`.p12`)                           | PEM + AES-GCM (свой формат, scrypt KDF)                |
| БД            | H2 (файл) + JPA + Flyway                  | SQLite (файл) + `database/sql`, схема через `go:embed` |
| Web UI        | есть (статический HTML/JS)                | нет — только REST                                      |
| Docker        | есть (Dockerfile + compose)               | есть (Dockerfile + compose)                            |

> Что и почему устроено так, как устроено — в README каждого проекта:
> - [JavaCryptoService/README.md](JavaCryptoService/README.md)
> - [GoCryptoService/README.md](GoCryptoService/README.md).
>
> Там же — как запускать, эндпоинты, диаграммы, паттерны.

## API-контракт (общий для обеих версий)

| Метод | URL                               | Что делает                                         |
|-------|-----------------------------------|----------------------------------------------------|
| POST  | `/api/v1/admin/generate-keystore` | генерит RSA-2048 ключ + самоподписанный сертификат |
| POST  | `/api/v1/crypto/sign`             | подпись документа                                  |
| POST  | `/api/v1/crypto/verify`           | проверка подписи                                   |
| POST  | `/api/v1/crypto/encrypt`          | гибридное шифрование (RSA + AES)                   |
| POST  | `/api/v1/crypto/decrypt`          | расшифровка                                        |
| POST  | `/api/v1/crypto/hash`             | SHA-256                                            |
| POST  | `/api/v1/fetch/document`          | скачать документ по HTTPS (с SSRF-фильтром)        |
| GET   | `/api/v1/operations`              | история операций (пагинация, фильтр по типу)       |
| GET   | `/api/v1/operations/{id}`         | одна операция по id, с деталями по типу            |

Java-версия сверху добавляет `/api/v1/convert/encode` и `/api/v1/convert/decode` — вспомогательные
ручки для своего Web UI (файл/текст ↔ base64), в Go-версии их нет за ненадобностью (UI нет).

## Запуск

Java:

```bash
cd JavaCryptoService
export KEYSTORE_PASSWORD=changeit
mvn spring-boot:run          # или: docker-compose up --build
```

Go:

```bash
cd GoCryptoService
export KEYSTORE_PASSWORD=changeit
go run ./cmd/server     # или: docker-compose up --build
```

Оба поднимаются на `http://localhost:8080` (не одновременно — порт общий, если нужно оба сразу, переопределить `PORT` у одного из них).

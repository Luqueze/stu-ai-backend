# AI Exam Platform — Back-end

> Criei esse projeto para estudar e praticar
> microsserviços com Spring Boot, mensageria assíncrona, integração com LLMs e boas práticas de
> engenharia backend em geral. Não é um produto em produção, não passou por hardening de segurança
> completo e decisões aqui priorizam valor didático sobre robustez de negócio. Por isso é possível que haja um bom 
> overengineering. A ideia é aprendizado não eficiência.

Backend de uma plataforma de provas assistida por IA: um professor/admin pede a geração de uma
prova sobre um tema, a IA gera as questões de forma assíncrona, e os alunos respondem e recebem
correção automática.

## Stack

- **Java 21** + **Spring Boot 3.3.5**
- **Gradle (Kotlin DSL)**, multi-módulo, com wrapper (`./gradlew`)
- **Spring Cloud Gateway** (roteamento), **Spring Security** + **JWT** (`jjwt`), **Spring Data JPA**,
  **Spring Data Redis**, **Spring AI** (`1.0.0-M6`, starter OpenAI)
- **PostgreSQL** (uma base por serviço), **Redis** (estado de sessão/timer de prova),
  **RabbitMQ** (mensageria entre `exam-service` e `ai-generator-service`)
- **Flyway** para versionamento de schema
- **springdoc-openapi** (Swagger UI) em cada serviço
- **Docker Compose** para o ambiente local completo

## Arquitetura

Monorepo Gradle com módulos flat (não aninhados) mapeados para diretórios físicos em `services/*`
e `shared/*` via `settings.gradle.kts`:

| Módulo Gradle | Diretório | Papel |
|---|---|---|
| `:api-gateway` | `services/api-gateway` | Ponto de entrada único; roteia para os demais serviços via Spring Cloud Gateway |
| `:auth-service` | `services/auth-service` | Cadastro/login, emissão de JWT, gestão de API keys do usuário |
| `:exam-service` | `services/exam-service` | Criação de provas, sessão/timer (Redis), submissão e correção de respostas |
| `:ai-generator-service` | `services/ai-generator-service` | Consome eventos de geração, chama a LLM (OpenAI ou Ollama) com Structured Outputs e publica o resultado |
| `:common-events` | `shared/common-events` | Contratos de evento (records Java puros) compartilhados entre `exam-service` e `ai-generator-service`; sem dependência de Spring Boot |

### Fluxo de geração de prova (assíncrono)

1. `exam-service` recebe o pedido de criação de prova, publica um evento na fila e responde
   imediatamente `202 Accepted` com a prova em estado `PENDING`.
2. `ai-generator-service` consome o evento, chama o modelo de IA configurado e publica um evento
   de conclusão (com retry e fila de erro configurados no listener do RabbitMQ).
3. `exam-service` escuta o evento de conclusão e transiciona a prova para `READY`.

Nenhuma chamada à IA é feita de forma síncrona a partir de um controller.

### Isolamento de dados

Cada serviço com persistência tem seu próprio banco (`auth_db`, `exam_db`); não há acesso direto
de um serviço às tabelas de outro — a comunicação entre eles é feita via eventos ou API.

## Endpoints principais

Expostos publicamente através do `api-gateway` (porta padrão `8086`), sob `/api/v1/...`.

**auth-service**
- `POST /api/v1/auth/register` — cadastro de usuário
- `POST /api/v1/auth/login` — autenticação, retorna JWT
- `POST /api/v1/users/me/api-keys` — cadastra API key própria (ex.: para IA)
- `GET /api/v1/users/me/api-keys` — lista API keys do usuário autenticado

**exam-service**
- `POST /api/v1/exams` — cria uma prova (dispara geração assíncrona via IA)
- `GET /api/v1/exams/{id}` — detalhes de uma prova
- `GET /api/v1/exams` — lista provas
- `POST /api/v1/exams/{examId}/session` — inicia a sessão/timer de uma prova (Redis)
- `GET /api/v1/exams/{examId}/session` — consulta estado da sessão
- `POST /api/v1/exams/{examId}/submission` — envia respostas para correção
- `GET /api/v1/exams/{examId}/submission` — consulta a própria submissão
- `GET /api/v1/exams/{examId}/submissions` — lista submissões (admin)

Existem apenas dois papéis na plataforma: **ADMIN** e **STUDENT** (sem papel de professor).

Documentação interativa via Swagger UI em cada serviço (`/swagger-ui.html`), agregada no gateway.

## Rodando localmente

### Pré-requisitos

- JDK 21
- Docker + Docker Compose (para Postgres, Redis, RabbitMQ e, opcionalmente, os serviços)

### 1. Configurar variáveis de ambiente

```bash
cp .env.example .env
# edite .env: defina ao menos OPENAI_API_KEY se for usar o provider OpenAI
```

### 2. Subir a infraestrutura + serviços via Docker Compose

```bash
docker compose up --build
```

Isso sobe Postgres, Redis, RabbitMQ e os 4 serviços Spring Boot na rede `ai-exam-network`.
O `docker-compose.override.yml` já expõe portas de debug remoto (`5086`–`5089`) para cada serviço.

Portas padrão:

| Serviço | Porta |
|---|---|
| api-gateway | 8086 |
| auth-service | 8087 |
| exam-service | 8088 |
| ai-generator-service | 8089 |
| PostgreSQL | 5433 (host) → 5432 (container) |
| Redis | 6379 |
| RabbitMQ | 5672 (AMQP) / 15672 (management UI) |

### 3. Alternativa: rodar um serviço fora do Docker

Suba só a infraestrutura (`postgres`, `redis`, `rabbitmq`) via Compose e rode o serviço com Gradle:

```bash
./gradlew :exam-service:bootRun
```

## Build e testes

```bash
./gradlew build                 # build de todos os módulos (compila + testa + gera jar)
./gradlew build -x test         # build sem rodar testes
./gradlew test                  # roda todos os testes
./gradlew :exam-service:test    # testes de um módulo específico
./gradlew projects              # lista todos os módulos
```

Os task paths seguem os nomes flat dos projetos Gradle (`:exam-service`), não o caminho físico
(`services/exam-service`).

## Convenções de código

O projeto segue um conjunto de convenções documentadas em [`CLAUDE.md`](./CLAUDE.md), entre elas:

- Controllers sem `try/catch` — erros tratados via `@RestControllerAdvice` por serviço
- Entidades JPA usam Lombok `@Builder` (nunca setters públicos soltos)
- DTOs e contratos de evento são `record`s imutáveis, nunca `@Entity`/Lombok
- Migrações de schema só via Flyway, nunca `ddl-auto=update`
- Dockerfiles multi-stage, imagem final `jre` (não `jdk`), usuário não-root

## Estrutura do repositório

```
.
├── build.gradle.kts          # configuração raiz (Java 21, JUnit, etc.)
├── settings.gradle.kts       # mapeamento flat dos módulos Gradle
├── gradle/libs.versions.toml # catálogo de versões
├── docker-compose.yml        # infraestrutura + serviços
├── docker-compose.override.yml # portas de debug remoto
├── services/
│   ├── api-gateway/
│   ├── auth-service/
│   ├── exam-service/
│   └── ai-generator-service/
├── shared/
│   └── common-events/        # contratos de evento compartilhados
└── infra/
    ├── local/                # infra de dev local (ex.: init de bancos)
    └── terraform/            # placeholder para IaC
```

## Status / limitações conhecidas

Projeto em desenvolvimento contínuo para fins de estudo. Áreas propositalmente simplificadas ou
ainda não cobertas incluem: observabilidade (tracing/metrics), testes de carga, políticas de
segurança de produção (rotação de segredos, rate limiting) e infraestrutura como código real em
`infra/terraform`.

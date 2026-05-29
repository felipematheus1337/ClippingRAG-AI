<h1 align="center">ClippingRAG-AI</h1>

<p align="center">
  <strong>A scheduled, RAG-powered daily news digest engine built with Spring AI.</strong><br/>
  Ingests documents, retrieves relevant context via vector similarity, generates AI-powered summaries and publishes them to AWS SNS.
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Java-21-007396?style=for-the-badge&logo=openjdk&logoColor=white"/>
  <img src="https://img.shields.io/badge/Spring_Boot-3.5-6DB33F?style=for-the-badge&logo=springboot&logoColor=white"/>
  <img src="https://img.shields.io/badge/Spring_AI-1.0-6DB33F?style=for-the-badge&logo=spring&logoColor=white"/>
  <img src="https://img.shields.io/badge/OpenAI-GPT-412991?style=for-the-badge&logo=openai&logoColor=white"/>
  <img src="https://img.shields.io/badge/PostgreSQL-pgvector-4169E1?style=for-the-badge&logo=postgresql&logoColor=white"/>
  <img src="https://img.shields.io/badge/AWS-SNS-FF9900?style=for-the-badge&logo=amazonwebservices&logoColor=white"/>
  <img src="https://img.shields.io/badge/Docker-Compose-2496ED?style=for-the-badge&logo=docker&logoColor=white"/>
  <img src="https://img.shields.io/badge/Maven-Build-C71A36?style=for-the-badge&logo=apachemaven&logoColor=white"/>
</p>

---

## Overview

**ClippingRAG-AI** is a production-style RAG (Retrieval-Augmented Generation) pipeline that runs autonomously on a cron schedule. On each execution it:

1. Ingests a source PDF into a vector database
2. Performs a similarity search to retrieve the most relevant chunks for a given topic
3. Injects the retrieved context into a grounded prompt sent to OpenAI
4. Publishes the AI-generated digest to an AWS SNS topic
5. Cleans up the ingested vectors to keep the store lean

The project's purpose is to practice the two hard parts of a real RAG system end-to-end: **retrieval** (chunking, embeddings, similarity search) and **grounding** (building a prompt that forces the model to answer *only* from the retrieved context, preventing hallucinations).

---

## Architecture

<p align="center">
  <img src="https://github.com/felipematheus1337/ClippingRAG-AI/blob/dev/assets/architecture.png?raw=true" alt="ClippingRAG-AI Architecture" width="800"/>
</p>

### Flow

| Step | Component | Action |
|------|-----------|--------|
| 1 | **Scheduler** (`@Scheduled`) | Fires on cron — triggers the pipeline |
| 2 | **RagNotificationService** | Reads the source PDF and stores paragraphs as embeddings in PgVector |
| 3 | **RagNotificationService** | Runs a similarity search (`topK=5`) against the vector store |
| 4 | **OpenAIImpl** | Formats the retrieved chunks into a grounded prompt and calls GPT |
| 5 | **SnsClient** | Publishes the generated digest to an AWS SNS topic |
| 6 | **RagNotificationService** | Deletes the ingested vectors to avoid accumulation |

---

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Language / Build | Java 21 · Maven |
| Framework | Spring Boot 3.5.x |
| AI Orchestration | Spring AI 1.0 |
| LLM & Embeddings | OpenAI (GPT · `text-embedding-3-small`) |
| Vector Store | PostgreSQL + `pgvector` extension |
| Document Ingestion | Spring AI PDF Document Reader |
| Messaging | AWS SNS via AWS SDK v2 |
| Scheduling | Spring `@Scheduled` (cron expression) |
| Boilerplate Reduction | Lombok |
| Local Infrastructure | Docker · Docker Compose |

---

## Project Structure

```
src/
└── main/java/scheduler_rag/clipping/
    ├── ClippingApplication.java              # Entry point
    ├── config/
    │   └── SnsConfig.java                   # AWS SNS client bean
    ├── constants/
    │   └── PromptConstants.java             # RAG prompt template
    ├── llms/
    │   ├── LLMGenericInterface.java         # Generic LLM abstraction
    │   └── OpenAIImpl.java                  # OpenAI ChatClient implementation
    ├── rag/
    │   └── RagNotificationService.java      # PDF ingestion + vector search
    └── scheduler/
        └── NotificationTecAndArchScheduler.java  # Orchestrates the full pipeline
```

---

## Getting Started

### Prerequisites

- Java 21+
- Docker & Docker Compose
- An OpenAI API key
- An AWS account (or LocalStack for local dev)

### 1. Clone the repository

```bash
git clone https://github.com/felipematheus1337/ClippingRAG-AI.git
cd ClippingRAG-AI
```

### 2. Place your source document

Drop the PDF you want to digest at:

```
src/main/resources/docs/boletim_diario.pdf
```

### 3. Start the local infrastructure

```bash
docker compose up -d
```

This spins up:
- **PostgreSQL** with the `pgvector` extension pre-installed
- **LocalStack** (optional) for SNS/SQS without touching a real AWS account

### 4. Set environment variables

```bash
export OPENAI_API_KEY=sk-...
export AWS_ACCESS_KEY_ID=...
export AWS_SECRET_ACCESS_KEY=...
export SNS_TOPIC_ARN=arn:aws:sns:us-east-1:123456789012:clipping-ai-digest
```

> **Using LocalStack?** Point `spring.cloud.aws.endpoint` at `http://localhost:4566` in `application.yml` and use dummy credentials.

### 5. Run the application

```bash
./mvnw spring-boot:run
```

The scheduler fires according to the configured cron (`0 0 7 * * *` by default — every day at 07:00). You can trigger it immediately by adjusting the cron expression for testing.

---

## Configuration

### `application.yml`

```yaml
spring:
  ai:
    openai:
      api-key: ${OPENAI_API_KEY}
      chat:
        options:
          model: gpt-5.2
          temperature: 0.99
          top-p: 0.95
      embedding:
        options:
          model: text-embedding-3-small
    vectorstore:
      pgvector:
        initialize-schema: true
        table-name: vector_store
  datasource:
    url: jdbc:postgresql://localhost:5432/clipping
    username: clipping
    password: clipping

spring.cloud.aws:
  region:
    static: us-east-1
  credentials:
    access-key: ${AWS_ACCESS_KEY_ID}
    secret-key: ${AWS_SECRET_ACCESS_KEY}

clipping:
  sns-topic-arn: ${SNS_TOPIC_ARN}
  schedule-cron: "0 0 7 * * *"
```

### Environment Variables

| Variable | Description |
|----------|-------------|
| `OPENAI_API_KEY` | OpenAI API key (used for chat completions and embeddings) |
| `AWS_ACCESS_KEY_ID` | IAM access key with `sns:Publish` permission |
| `AWS_SECRET_ACCESS_KEY` | IAM secret key |
| `SNS_TOPIC_ARN` | ARN of the SNS topic to publish digests to |

---

## AWS Setup

### Create an SNS Topic

```bash
aws sns create-topic --name clipping-ai-digest
```

Copy the returned ARN and export it as `SNS_TOPIC_ARN`.

### Minimal IAM Policy

The application only **produces** to SNS — it needs a single permission:

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": "sns:Publish",
      "Resource": "arn:aws:sns:us-east-1:123456789012:clipping-ai-digest"
    }
  ]
}
```

Attach this policy to the IAM user or role your application authenticates with.

---

## Docker Compose

```yaml
services:
  postgres:
    image: pgvector/pgvector:pg16
    environment:
      POSTGRES_DB: clipping
      POSTGRES_USER: clipping
      POSTGRES_PASSWORD: clipping
    ports:
      - "5432:5432"
    volumes:
      - pgdata:/var/lib/postgresql/data

  localstack:
    image: localstack/localstack:latest
    environment:
      SERVICES: sns,sqs
    ports:
      - "4566:4566"

volumes:
  pgdata:
```

---

## How It Works — Deep Dive

### Ingestion

`RagNotificationService.ingestPDF()` uses Spring AI's `ParagraphPdfDocumentReader` to split the PDF into paragraph-level `Document` objects. Each document is embedded via OpenAI's `text-embedding-3-small` model and stored in the `vector_store` table managed by `pgvector`.

### Retrieval

`RagNotificationService.search(query)` calls `vectorStore.similaritySearch()` with `topK=5`, returning the five most semantically similar chunks to the query string. Chunks are joined with a separator for prompt injection.

### Grounded Generation

`OpenAIImpl.call()` builds the final prompt by formatting the retrieved context into `PromptConstants.RAG_PROMPT` and sends it to the GPT model via Spring AI's `ChatClient`. The model is instructed to answer **only** from the provided context, preventing hallucinations.

### Cleanup

After publishing, `RagNotificationService.clearDocuments(ids)` removes all ingested document vectors by ID, keeping the vector store lean across runs.

---

## Roadmap

- [ ] Multiple topics per run (one digest per category)
- [ ] Re-ranking of retrieved chunks before prompt assembly
- [ ] Idempotency key per digest to prevent double-publishing on re-runs
- [ ] Evaluation suite (questions + expected answers) to measure retrieval quality
- [ ] Support for local models (Ollama) without changing the pipeline
- [ ] REST endpoint to trigger digests on demand

---

## License

This project is open-source and available under the [MIT License](LICENSE).

---

<p align="center">
  Built with Spring AI · OpenAI · PostgreSQL · AWS SNS
</p>

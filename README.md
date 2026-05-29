# Clipping AI

> A scheduled, RAG-powered daily news digest engine.

**Clipping AI** runs on a timer, retrieves relevant context from a vector database,
builds a grounded prompt, asks an LLM to write a topic-based summary, and publishes
the result to an AWS SNS topic so any downstream consumer (SQS, email, Slack, etc.)
can pick it up.

The project is intentionally small. Its goal is to practice the two hard parts of a
real RAG system end to end: **retrieval** (chunking, embeddings, similarity search)
and **grounding** (building a prompt that forces the model to answer *only* from the
retrieved context).

---

## Architecture

```
                 (event-sns)
   ┌─────────┐  ◄───────────────┐
   │   SQS   │                  │
   └─────────┘                  │ publish
        ▲                       │
        │ subscribes       ┌──────────┐
   ┌─────────┐  publish     │   SNS    │
   │  ...    │ ◄────────────│  topic   │
   └─────────┘              └──────────┘
                                 ▲
                                 │ summary
   ┌─────────────┐   context   ┌──────────┐   prompt   ┌─────────┐
   │  Scheduler  │ ──────────► │   RAG    │ ─────────► │   LLM   │
   │ (@Scheduled)│ ◄────────── │ (PgVector)│           │ (OpenAI)│
   └─────────────┘  retrieved  └──────────┘            └─────────┘
```

**Flow**

1. The **Scheduler** fires on a cron/fixed interval.
2. It asks the **RAG** layer for the most relevant chunks for a given topic/date
   (similarity search against **PgVector**).
3. Retrieved chunks are injected into a prompt template and sent to the **LLM**
   (**OpenAI**).
4. The generated digest is **published to an SNS topic**.
5. SNS fans the message out to subscribers (an **SQS** queue in the diagram).

---

## Tech stack

| Layer            | Choice                                            |
|------------------|---------------------------------------------------|
| Language / build | Java 21, Maven                                    |
| Framework        | Spring Boot 3.5.x                                 |
| Boilerplate      | Lombok                                            |
| LLM + embeddings | OpenAI via Spring AI 1.0                           |
| Vector store     | PostgreSQL + `pgvector` extension                 |
| Messaging        | AWS SNS (producer) via Spring Cloud AWS           |
| Scheduling       | Spring `@Scheduled`                               |
| Local infra      | Docker / Docker Compose                           |

---

## What you need from AWS (to be an SNS producer)

To **publish** to SNS you do **not** need to manage SQS subscriptions in code — the
app only produces. You need three things:

### 1. An SNS topic
Create a standard topic and copy its **ARN** (looks like
`arn:aws:sns:us-east-1:123456789012:clipping-ai-digest`).

```bash
aws sns create-topic --name clipping-ai-digest
```

### 2. Credentials with permission to publish
Create an IAM user (or role) and attach a minimal policy. The only action the
producer needs is `sns:Publish` on your topic ARN:

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

Generate an **access key / secret** for that user, or — better for anything beyond
local dev — use an IAM role and let the default credential provider chain resolve it.

### 3. A region
e.g. `us-east-1`. SNS is regional; the topic and the client must agree.

> **Tip for local development:** you can run SNS (and SQS) entirely offline with
> **LocalStack** in Docker, so you don't touch a real AWS account while iterating.
> Point Spring Cloud AWS at the LocalStack endpoint instead of AWS.

---

## What goes in `pom.xml`

Two BOMs manage versions so you don't hand-pin every artifact: the **Spring AI BOM**
and the **Spring Cloud AWS BOM**. Then you add the starters.

```xml
<properties>
    <java.version>21</java.version>
    <spring-ai.version>1.0.0</spring-ai.version>
    <spring-cloud-aws.version>3.4.0</spring-cloud-aws.version>
</properties>

<dependencyManagement>
    <dependencies>
        <!-- Spring AI: pulls in consistent versions for OpenAI + PgVector -->
        <dependency>
            <groupId>org.springframework.ai</groupId>
            <artifactId>spring-ai-bom</artifactId>
            <version>${spring-ai.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>

        <!-- Spring Cloud AWS: pulls in consistent versions for SNS + AWS SDK v2 -->
        <dependency>
            <groupId>io.awspring.cloud</groupId>
            <artifactId>spring-cloud-aws-dependencies</artifactId>
            <version>${spring-cloud-aws.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>

<dependencies>
    <!-- Core Spring Boot -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>

    <!-- Lombok -->
    <dependency>
        <groupId>org.projectlombok</groupId>
        <artifactId>lombok</artifactId>
        <optional>true</optional>
    </dependency>

    <!-- ===== RAG: OpenAI (chat + embeddings) ===== -->
    <dependency>
        <groupId>org.springframework.ai</groupId>
        <artifactId>spring-ai-starter-model-openai</artifactId>
    </dependency>

    <!-- ===== RAG: PgVector vector store ===== -->
    <dependency>
        <groupId>org.springframework.ai</groupId>
        <artifactId>spring-ai-starter-vector-store-pgvector</artifactId>
    </dependency>

    <!-- Read the source PDF and split it into documents to embed -->
    <dependency>
        <groupId>org.springframework.ai</groupId>
        <artifactId>spring-ai-pdf-document-reader</artifactId>
    </dependency>

    <!-- ===== SNS producer (gives you SnsTemplate) ===== -->
    <dependency>
        <groupId>io.awspring.cloud</groupId>
        <artifactId>spring-cloud-aws-starter-sns</artifactId>
    </dependency>

    <!-- Postgres JDBC driver -->
    <dependency>
        <groupId>org.postgresql</groupId>
        <artifactId>postgresql</artifactId>
        <scope>runtime</scope>
    </dependency>
</dependencies>
```

> **Note on the SNS producer:** the single dependency that matters for *producing* is
> `spring-cloud-aws-starter-sns`. It auto-configures a `SnsTemplate` bean. You inject
> it and call `snsTemplate.sendNotification(topicArn, payload, subject)` — no manual
> AWS SDK client wiring needed. The BOM (`spring-cloud-aws-dependencies`) pulls the
> matching AWS SDK v2 transitively, so you don't list the SDK yourself.

> **Version alignment:** match `spring-cloud-aws.version` to your Spring Boot line
> (3.4.x ↔ Spring Boot 3.5.x). If you bump Spring Boot to 4.x, move Spring Cloud AWS
> to its 4.x line. The easiest way to get a coherent set is to generate the skeleton
> on [start.spring.io](https://start.spring.io).

---

## Local infrastructure (Docker Compose)

Postgres needs the `pgvector` extension. Use the `pgvector/pgvector` image so it's
preinstalled:

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

  # Optional: SNS/SQS locally without touching AWS
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

## Configuration (`application.yml`)

```yaml
spring:
  ai:
    openai:
      api-key: ${OPENAI_API_KEY}
      chat:
        options:
          model: gpt-4.1-mini
          temperature: 0.0
      embedding:
        options:
          model: text-embedding-3-small
    vectorstore:
      pgvector:
        initialize-schema: true   # creates the vector_store table for you
        table-name: vector_store
  datasource:
    url: jdbc:postgresql://localhost:5432/clipping
    username: clipping
    password: clipping

# Spring Cloud AWS
spring.cloud.aws:
  region:
    static: us-east-1
  credentials:
    access-key: ${AWS_ACCESS_KEY_ID}
    secret-key: ${AWS_SECRET_ACCESS_KEY}

# App-specific
clipping:
  sns-topic-arn: ${SNS_TOPIC_ARN}
  schedule-cron: "0 0 7 * * *"   # every day at 07:00
```

### Environment variables

| Variable                | Purpose                                  |
|-------------------------|------------------------------------------|
| `OPENAI_API_KEY`        | OpenAI key (chat + embeddings)           |
| `AWS_ACCESS_KEY_ID`     | IAM access key (or use a role)           |
| `AWS_SECRET_ACCESS_KEY` | IAM secret                               |
| `SNS_TOPIC_ARN`         | ARN of the topic to publish to           |

---

## How it works internally

- **Ingestion (one-off):** read the source PDF with the PDF document reader, split it
  into chunks, generate embeddings, and store them in PgVector via `VectorStore`.
- **Retrieval (per run):** the scheduler calls
  `vectorStore.similaritySearch(...)` with a topic query and a top-k limit.
- **Prompt assembly:** the retrieved chunks fill a `{context}` placeholder in a
  template that instructs the model to answer *only* from that context.
- **Generation:** `ChatClient` calls OpenAI and returns the digest.
- **Publish:** `SnsTemplate` sends the digest to the SNS topic.

### Prompt template (starting point)

```
You are the editor of Clipping AI. Using ONLY the retrieved excerpts below,
write a daily digest about the topic "{topic}".

Rules:
- Use only information from the excerpts. If there isn't enough, say
  "No relevant news about {topic} today."
- Maximum 3 short paragraphs, informative newsletter tone.
- Do not invent numbers, names, or facts not present in the context.

Retrieved excerpts:
{context}

Digest for {date}:
```

The "use ONLY the excerpts" rule is what you'll iterate on — it's the difference
between a grounded summary and a hallucinated one.

---

## Running it

```bash
# 1. Start infra
docker compose up -d

# 2. Export your secrets
export OPENAI_API_KEY=sk-...
export AWS_ACCESS_KEY_ID=...
export AWS_SECRET_ACCESS_KEY=...
export SNS_TOPIC_ARN=arn:aws:sns:us-east-1:123456789012:clipping-ai-digest

# 3. Run
./mvnw spring-boot:run
```

On startup the app ingests the source documents into PgVector; the scheduler then
fires on its cron and publishes a digest to SNS.

---

## Roadmap ideas

- Multiple topics per run (one digest per category).
- Idempotency key per digest so re-runs don't double-publish.
- Re-ranking of retrieved chunks before prompt assembly.
- An eval suite (questions + expected answers) to measure retrieval quality.
- Swap OpenAI for a local model without changing the rest of the pipeline.

---

## License

MIT (or your choice).

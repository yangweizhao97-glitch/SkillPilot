# ADR-001: Use Spring Boot, PostgreSQL + pgvector, and Alibaba Cloud Model Studio for V1

## Status

Accepted

## Date

2026-06-19

## Context

CareerAgent V1 needs to deliver a complete AI career-preparation workflow:

```text
upload resume/JD
  -> parse documents
  -> chunk text
  -> generate embeddings
  -> retrieve relevant chunks
  -> run Agent workflow
  -> validate structured JSON
  -> save reports
  -> expose logs and traces
```

The project should show backend Agent engineering ability without making the first version too complex. The first version must be easy to run locally, easy to debug, and stable enough for later LangGraph and product-level features.

## Decision

Use the following V1 stack:

- Java 21
- Spring Boot 3.x
- Maven
- Spring Security + JWT
- Spring Data JPA
- Flyway
- PostgreSQL
- pgvector
- Redis
- Local file storage
- Alibaba Cloud Model Studio / DashScope
- `qwen-flash` for text generation
- `text-embedding-v4` for embeddings
- `1024` embedding dimension
- Spring Boot state-machine-style workflow for V1 Agent orchestration
- Tool Registry for controlled Agent tool calls
- JSON Schema validation for all Agent structured outputs

V1 does not include LangGraph, MinIO, simulated interview scoring, PDF export, multi-model routing, Elasticsearch, Milvus, or enterprise-grade authorization.

## Rationale

### Spring Boot

CareerAgent is primarily a backend workflow platform. It needs authentication, authorization, transactions, file upload, async tasks, state tracking, logs, API contracts, and database migrations. Spring Boot is strong in these areas and keeps V1 implementation understandable.

### PostgreSQL + pgvector

CareerAgent depends on RAG retrieval. PostgreSQL + pgvector lets the system store business data, metadata, and embeddings in one database. It also supports direct vector similarity search and future vector indexing.

The V1 embedding column is fixed as:

```sql
embedding vector(1024)
```

This matches the selected `text-embedding-v4` dimension. Changing the embedding model or dimension later requires re-indexing existing chunks and migrating the database column.

### Alibaba Cloud Model Studio / DashScope

V1 uses a China-friendly provider with low-cost options and official text-generation plus embedding models in the same platform. `qwen-flash` is suitable for cost-sensitive MVP generation tasks. `text-embedding-v4` is suitable for text search and RAG.

All provider calls are hidden behind `LlmClient` and `EmbeddingClient`, so V2/V3 can switch to DeepSeek, OpenAI, Zhipu, or another provider without rewriting Agent logic.

### Spring Workflow Before LangGraph

V1 workflow is fixed and predictable:

```text
MATCHING_JOB
  -> ANALYZING_RESUME
  -> GENERATING_QUESTIONS
  -> SUCCESS
```

This does not require complex autonomous planning. Spring Boot can run the workflow with clearer logging, fewer services, and simpler debugging. LangGraph remains a V2 extension through an `AgentWorkflowExecutor` interface.

### Tool Registry

Agents must not directly access the database, filesystem, or arbitrary system tools. Tool Registry provides:

- permission checks
- input/output validation
- audit logs
- retry metadata
- traceId propagation
- controlled future extension

### JSON Schema

LLM outputs are untrusted and unstable. JSON Schema validation is required before saving structured reports or rendering frontend pages. Failed validation should trigger repair or retry before the task fails.

## Alternatives Considered

### MySQL as Main Database

Pros:

- Already available locally.
- Familiar and easy to start.

Cons:

- Less mature for vector retrieval in this project context.
- Storing embeddings in JSON/TEXT and calculating similarity in Java is less production-like.
- Harder to present as a complete RAG backend.

Rejected for V1. PostgreSQL + pgvector better matches the Agent/RAG goal.

### Milvus or Elasticsearch

Pros:

- More powerful at scale.
- Specialized retrieval capabilities.

Cons:

- Extra infrastructure.
- More deployment and debugging cost.
- Not needed for V1 data size.

Rejected for V1. Re-evaluate in V3 if data volume grows.

### LangGraph in V1

Pros:

- Stronger Agent orchestration features.
- Better for complex branching and handoff.

Cons:

- Adds another runtime or service.
- More integration, logging, deployment, and debugging work.
- V1 workflow is fixed and does not need it yet.

Rejected for V1. Planned for V2 through `LangGraphWorkflowExecutor`.

### MinIO in V1

Pros:

- More production-like object storage.
- Easier future deployment.

Cons:

- Extra local service and configuration.
- Not part of the core Agent/RAG value.

Rejected for V1. Local file storage is enough; MinIO is planned for V2.

### OpenAI as Default Provider

Pros:

- Strong model ecosystem.
- Good API consistency.

Cons:

- More friction for local China-oriented development.
- Cost and access may be less convenient for this project.

Rejected as V1 default, but supported later through `LlmClient`.

## Consequences

- V1 has a clear, stable stack and smaller implementation surface.
- The project demonstrates real RAG backend ability through pgvector.
- Model provider details stay isolated behind client abstractions.
- LangGraph can be added later without rewriting controllers or persistence logic.
- Existing embeddings must be rebuilt if the embedding model or dimension changes.

## Follow-ups

- Create Spring Boot project using package `com.huatai.careeragent`.
- Add Docker Compose for PostgreSQL + pgvector and Redis.
- Add Flyway migration with `vector(1024)`.

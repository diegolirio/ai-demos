# LLM Gateway (AI Gateway) na POC

Este documento reúne o que é um AI Gateway, por que usar um e como ele se encaixa nesta POC. Também compara as duas opções que queremos avaliar: o **LiteLLM** e o **OpenRouter**.

> Status: a seção 6 (LiteLLM no compose + OpenRouter, alternados por `LLM_PROVIDER`) está **implementada**. A seção 7 é o próximo passo.

---

## 1. O que é um AI Gateway

É um proxy entre as aplicações e os provedores de LLM. Toda chamada a modelo passa por ele, e com isso ele vira o ponto único para aplicar políticas, segurança, observabilidade e controle de custo.

```
apps / agentes ──(API única, formato OpenAI)──▶ AI Gateway ──▶ OpenAI / Anthropic / Bedrock / Azure / Ollama / ...
                                                  │
                     autenticação, políticas, rate limit, guardrails,
                     logs, rastreio, custo, fallback entre modelos
```

Nesta POC, a `ana-agent` e o `investimentos-agent` são os consumidores. Os dois usam o `OpenAiChatModel` do LangChain4j configurado por `LLM_BASE_URL`, `LLM_API_KEY` e `LLM_MODEL`. Por isso qualquer gateway compatível com OpenAI entra na frente deles **sem mudar código**.

---

## 2. Por que utilizar um AI Gateway

| Motivo | O que significa |
|---|---|
| **Governança** | Políticas centralizadas para o uso de IA, em vez de cada time decidir sozinho |
| **Segurança** | Controle de acesso, proteção dos endpoints e guardrails sem grandes alterações nas aplicações. Restringe o acesso às chaves e credenciais dos provedores e facilita a rotação delas |
| **Observabilidade** | Métricas, logs e rastreamento de todas as chamadas a LLM num lugar só |
| **Custos** | Controle e acompanhamento do consumo por aplicação, time ou usuário |
| **Flexibilidade** | Troca de modelo sem alterar as aplicações e padronização dos payloads de request e response |

### 2.1 Governança e controle

- **Políticas de uso dos modelos:** quem pode usar o quê e para qual finalidade.
- **Limites de tokens e requisições:** por chave, aplicação ou usuário.
- **Controle de modelos e provedores permitidos:** allowlist de modelos e de provedores (por exemplo, só provedores com contrato e sem retenção de dados).
- **Auditoria e rastreabilidade das chamadas:** saber quem chamou, qual modelo, quando e com qual resultado.
- **Acompanhamento de custos (FinOps):** gasto por projeto, orçamento e alertas.

### 2.2 Segurança

- **Autenticação e autorização centralizadas:** as aplicações usam uma chave do gateway, nunca a chave do provedor.
- **Controle de acesso por aplicação ou usuário:** chaves distintas com permissões distintas.
- **Rate limiting e proteção contra abusos:** evita consumo sem limite, seja por loop de agente, bug ou ataque.
- **Gerenciamento seguro de credenciais:** as chaves dos provedores ficam só no gateway, e rotacioná-las não exige redeploy das aplicações.
- **Filtros e guardrails nas requisições e respostas:** detecção de prompt injection, mascaramento de PII e bloqueio de conteúdo.

### 2.3 Relação com as iniciativas OWASP para IA

O gateway é um dos lugares naturais para mitigar os riscos descritos pela OWASP:

- **OWASP Top 10 for LLM Applications 2026**
- **OWASP Top 10 for Agentic Applications for 2026**
- **OWASP MCP Top 10**

Riscos como prompt injection, vazamento de informação sensível, consumo sem limite e agência excessiva têm mitigação parcial no gateway: guardrails, mascaramento, rate limit, orçamento e allowlist de modelos. Esta POC usa **agentes (A2A)** e **MCP**, então as três listas se aplicam. O gateway cobre só a chamada ao LLM. A autorização das tools MCP e a delegação A2A continuam sendo responsabilidade dos agentes.

---

## 3. As duas opções

### LiteLLM

É um software **open source e self-hosted** (Python) com duas partes:
- **SDK:** uma biblioteca que chama mais de 100 provedores com a mesma interface.
- **Proxy Server:** o gateway propriamente dito. Expõe `/v1/chat/completions` no formato OpenAI e traduz cada chamada para o provedor real.

Os modelos ficam num `config.yaml` com apelidos (por exemplo, `qwen-local` → Ollama e `sonnet-or` → OpenRouter), e a aplicação só conhece o apelido. Também oferece:
- virtual keys, orçamento e limites por chave (precisam de Postgres);
- fallback e retry entre modelos;
- guardrails plugáveis;
- callbacks de observabilidade, como Langfuse, OpenTelemetry e Prometheus.

### OpenRouter

É um **serviço SaaS**: faz o papel de gateway, mas hospedado pela própria OpenRouter. Com uma conta, créditos e **uma chave**, dá acesso a centenas de modelos (Claude, GPT, Gemini, Llama, Qwen…) em `https://openrouter.ai/api/v1`, também compatível com OpenAI. Faz roteamento e fallback entre provedores, permite restringir provedores e políticas de dados e fatura tudo de forma unificada.

### Comparação

| | LiteLLM | OpenRouter |
|---|---|---|
| O que é | Software open source | Serviço SaaS |
| Quem hospeda | Nós | A OpenRouter |
| Dados passam por | Nossa infra | Terceiro (OpenRouter) |
| Controle e compliance | Total, mas somos nós que operamos | Baixo, fora da nossa infra |
| Governança (chaves, orçamento, limites) | Configurável (virtual keys + Postgres) | Por conta e chave, limitada |
| Guardrails | Plugáveis | Limitados |
| Custo de operação | Subir e manter o proxy | Nenhum (taxa sobre o uso) |
| Uso típico | Montar o próprio gateway | Protótipos e testar muitos modelos |
| Combinação | Pode usar o OpenRouter como um dos provedores | Pode ficar atrás do LiteLLM |

> **Dados reais de clientes não devem passar pelo OpenRouter.** Na POC só usamos dados mock; para dados reais, o caminho é um gateway self-hosted (como o LiteLLM) apontando para provedores com contrato.

---

## 4. Como a POC consome LLM hoje

| Serviço | Onde |
|---|---|
| ana-agent | [AnaConfig.java](../ana-agent/src/main/java/poc/a2a/ana/config/AnaConfig.java) (`OpenAiChatModel`) e [application.yml](../ana-agent/src/main/resources/application.yml) (`llm.*`) |
| investimentos-agent | [EspecialistaConfig.java](../investimentos-agent/src/main/java/poc/a2a/investimentos/especialista/EspecialistaConfig.java) e [application.yml](../investimentos-agent/src/main/resources/application.yml) (`llm.*`) |
| docker-compose | Repassa `LLM_BASE_URL`, `LLM_API_KEY` e `LLM_MODEL` do `.env` aos dois agentes |

Qualquer endpoint compatível com OpenAI funciona trocando o `.env`:

```bash
# Ollama direto, sem gateway (modo legado, sem LLM_PROVIDER)
LLM_BASE_URL=http://host.docker.internal:11434/v1

# OpenRouter direto
LLM_BASE_URL=https://openrouter.ai/api/v1
LLM_MODEL=anthropic/claude-sonnet-5

# LiteLLM
LLM_BASE_URL=http://litellm:4000/v1
LLM_MODEL=<apelido do config.yaml>
```

---

## 5. Objetivo desta etapa

Implementar **LiteLLM** e **OpenRouter** e alternar entre eles por configuração para validar, comparar e testar as duas soluções:
- o LiteLLM é o padrão ativo;
- o OpenRouter é ligado trocando `LLM_PROVIDER`.

Queremos responder:
1. O tool calling da Ana e o JSON do especialista funcionam igual pelos dois caminhos?
2. Qual é a diferença de latência entre o proxy self-hosted e o SaaS?
3. Quanto esforço exige cada item da seção 2 (chaves, limites, custo, logs, guardrails) em cada opção?
4. Faz sentido combinar os dois (LiteLLM na frente, com o OpenRouter como um dos provedores)?

---

## 6. Implementação

```
LLM_PROVIDER=litellm     ana/investimentos ──▶ litellm:4000 ──┬─▶ Ollama (host)
                                                              └─▶ OpenRouter
LLM_PROVIDER=openrouter  ana/investimentos ──────────────────────▶ OpenRouter
```

**Nenhum Java muda.** Os agentes continuam lendo só `LLM_*`.

| Arquivo | Mudança |
|---|---|
| `docker/litellm/config.yaml` (novo) | Apelidos `qwen-local` (Ollama), `sonnet-or` e `gpt-mini-or` (OpenRouter). Autenticação por `LITELLM_MASTER_KEY`, sem banco |
| `docker-compose.yml` | Serviço `litellm` (imagem oficial, porta 4000, healthcheck). Os agentes usam `http://litellm:4000/v1` como padrão, sem `depends_on` (o cliente LLM é preguiçoso e, com `openrouter`, o LiteLLM nem é usado) |
| `scripts/llm-env.sh` (novo) | Traduz `LLM_PROVIDER` em `LLM_BASE_URL`, `LLM_API_KEY` e `LLM_MODEL` (host do compose ou `localhost` fora do Docker). Falha com mensagem clara se faltar chave |
| `Makefile` | `up`/`run-*` usam o script, mais os targets `llm-use P=<provider>`, `llm-restart` (recria só os agentes), `llm-status` (sem mostrar as chaves) e `llm-compare` (smoke nos dois provedores, com tempo lado a lado) |
| `.env.example` | `LLM_PROVIDER=litellm`, `LITELLM_MASTER_KEY`, `LITELLM_MODEL`, `OPENROUTER_API_KEY` e `OPENROUTER_MODEL`. Um `.env` sem `LLM_PROVIDER` continua funcionando como hoje |

Alternar fica assim:

```bash
make llm-use P=openrouter && make llm-restart
make llm-status
make llm-compare
```

**Validação:** `make up && make smoke` com LiteLLM; `curl` no LiteLLM (`/v1/models` e um chat com tools); troca para OpenRouter e `make smoke`; `make test` verde.

### Primeiros resultados (LiteLLM → Ollama `qwen2.5:7b` em CPU, 2026-09-24)

- **Tool calling passa intacto pelo LiteLLM:** a mesma requisição com `tools` dá o mesmo comportamento direto no Ollama e via proxy.
- **Overhead do gateway desprezível:** 3 rodadas de uma mesma pergunta levaram 11–17s nos dois caminhos. O tempo é do modelo, não do proxy.
- **O smoke falhou por causa do modelo, não do gateway** (28 falhas em 15 min): timeouts do LLM (60s na Ana, 90s na delegação A2A) com o Ollama em CPU, e o 7b às vezes responde com texto em vez de chamar a tool. Para validar a jornada completa, use um modelo maior (`LITELLM_MODEL=sonnet-or` ou `LLM_PROVIDER=openrouter`).
- **Pendente:** validar o OpenRouter (direto e via `sonnet-or`). É só preencher `OPENROUTER_API_KEY` no `.env` e rodar `make llm-compare`.
- O log `WARNING: Unsupported upgrade request` do LiteLLM é o HttpClient do Java tentando upgrade para HTTP/2 (h2c). O uvicorn ignora e responde em HTTP/1.1, então é inofensivo.

---

## 7. Próximos passos (depois do básico)

Cada item mapeia um "porquê" da seção 2 para algo demonstrável na POC:

| Porquê | Demonstração possível |
|---|---|
| Controle de acesso por aplicação | Uma virtual key do LiteLLM por agente (Ana e especialista), com os modelos permitidos em cada uma |
| Limites de tokens e requisições | `rpm`/`tpm` e orçamento por chave no LiteLLM, e limite de crédito por chave no OpenRouter |
| FinOps | Painel de gasto do LiteLLM (Postgres do compose) comparado com o dashboard do OpenRouter |
| Auditoria e rastreabilidade | Metadata por chamada (agente, `contextId` e cliente) e um callback OpenTelemetry/Langfuse |
| Flexibilidade | Modelo barato para a Ana rotear e modelo forte para o especialista, trocando só o apelido. Fallback de `sonnet-or` para `qwen-local` |
| Guardrails | Guardrail de PII (CPF) e de prompt injection no LiteLLM antes de chegar ao modelo |
| Credenciais | A chave do OpenRouter só no LiteLLM, com os agentes usando apenas a chave do gateway |

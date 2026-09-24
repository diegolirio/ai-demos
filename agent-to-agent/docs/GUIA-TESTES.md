# Guia: testar e explorar a POC Agent-to-Agent

Roteiro do zero até ver uma conversa passar por **chat-web → Ana → A2A → Investimentos → MCP** e voltar. Todos os comandos rodam a partir de `agent-to-agent/`.

---

## 0. Pré-requisitos

```bash
java -version            # 25 (o Makefile usa $HOME/.sdkman/candidates/java/25.0.2-tem)
mvn -v                   # Maven 3.9+
node -v                  # >= 20.9 (recomendado 24)
docker info >/dev/null && echo "docker ok"
jq --version
```

---

## 1. Escolher o LLM e preencher o `.env`

```bash
cp .env.example .env     # só na primeira vez
```

Atalho para Ollama local: `cp .env.llm-local.example .env` (já vem configurado, sem chave).

Edite o `.env` **no seu editor**. Nunca cole a chave no chat nem faça commit dela; o `.env` está no `.gitignore`.

| Opção | `LLM_BASE_URL` | `LLM_API_KEY` | `LLM_MODEL` | Observação |
|---|---|---|---|---|
| **Ollama local** (grátis) | `http://host.docker.internal:11434/v1` | `ollama` | `qwen2.5:7b` ou `qwen3:8b` | Instale em [ollama.com/download](https://ollama.com/download) e rode `ollama pull qwen2.5:7b`. Modelos pequenos erram mais em tool calling e JSON |
| **LLM Gateway da AI Foundation** | URL do gateway + `/v1` | chave de QA do Haifa | nome do modelo no gateway | Caminho oficial da empresa |
| **API da Anthropic** | `https://api.anthropic.com/v1` | `sk-ant-...` | `claude-sonnet-5` | Endpoint compatível com OpenAI; precisa de créditos na API |
| **OpenAI** | `https://api.openai.com/v1` | `sk-...` | `gpt-4o-mini` | |

> **Dentro do Docker ou fora dele?** `host.docker.internal` só existe **dentro dos containers**. Se for rodar os serviços direto no Mac (seção 7), use `http://localhost:11434/v1` para o Ollama.

---

## 2. Testes automatizados (não precisam de LLM)

```bash
make test        # unitários dos 4 serviços Java (rápido, sem Docker)
make test-web    # chat-web: typecheck + lint + vitest
```

**Opcional:** testes de integração com Postgres e Ollama reais via Testcontainers. Na primeira vez, baixam cerca de 6 GB.

```bash
make test-integration
```

---

## 3. Subir tudo (Docker Compose)

```bash
make up          # build (mvn + npm) + docker compose up --wait
make ps          # todos devem aparecer "healthy"
```

| Serviço | URL |
|---|---|
| chat-web | http://localhost:3000 |
| ana-agent | http://localhost:8080/actuator/health |
| investimentos-agent | http://localhost:8081/.well-known/agent-card.json |
| tracking-money-mcp | http://localhost:8082/actuator/health |
| cdb-mcp | http://localhost:8083/actuator/health |
| cred-mcp | http://localhost:8084/actuator/health |
| Postgres | `localhost:55432` (usuário, senha e banco: `agents`) |

Validação automática da jornada:

```bash
make smoke       # 8 CPFs de teste + retorno + health do chat-web → "SMOKE OK"
```

---

## 4. Explorar pelo chat web

Abra **http://localhost:3000**. O chat pede o **CPF** antes de liberar a conversa; a lista **"CPFs de teste"** preenche o campo e já inicia a sessão. Trocar de CPF inicia uma nova sessão.

1. Envie: `meu dinheiro sumiu`
   - A Ana pergunta onde o dinheiro estava aplicado.
   - O painel de debug mostra **"sem delegação"**: a Ana respondeu sozinha, sem A2A.
2. Envie: `estava em investimentos e agora não consigo encontrar`
   - A Ana delega ao especialista via A2A.
   - O painel de debug mostra `confidence`, `facts`, `risks` e `sources`.

| CPF | O que a Ana deve dizer | O que olhar no painel |
|---|---|---|
| `111.001.001-05` | Resgate em liquidação, cai na conta em alguns minutos | `facts` com `EM_LIQUIDACAO` e crédito `PROCESSANDO`; `sources` com os MCPs |
| `222.002.002-93` | O dinheiro já está na conta | `facts` com `LIQUIDADO` e crédito `CONCLUIDA` |
| `333.003.003-80` | Continua aplicado no CDB | posição `pos-003`, sem resgates |
| `444.004.004-76` | Não encontrou; encaminha para atendimento humano | `confidence` < 0.5 e `risks` preenchido |
| `555.005.005-62` | Já está na conta (passou pela conta garantia e foi liberado) | bloco **Conta garantia** com `LIBERADO_CONTA`; `cred-mcp` em `sources` |
| `666.006.006-59` | Em análise, sem prazo definido | bloco **Conta garantia** com `EM_ANALISE`; `cred-mcp` em `sources` |
| `777.007.007-45` | Retido até o pagamento da fatura (vence 05/10) | bloco **Conta garantia** com `RETIDO_ATE_PAGAMENTO_FATURA`; `cred-mcp` em `sources` |
| `888.008.008-31` | Parte liberada (R$ 6.500), parte retida (R$ 3.500) | bloco **Conta garantia** com `RETIDO_PARCIAL`; `cred-mcp` em `sources` |

Também vale testar:
- **Continuar a conversa** na mesma sessão (ex.: "e quando cai?"). A memória fica no Postgres por `sessionId`.
- **"Nova conversa"** gera um novo `sessionId` (aparece no topo) e a Ana esquece o contexto — a menos que o CPF seja o mesmo (ver 4.1).

---

## 4.1. Voltar depois com o mesmo CPF

1. CPF `888.008.008-31`, envie "meu dinheiro sumiu" e depois "estava em investimentos e agora não consigo encontrar" — a Ana explica a retenção parcial.
2. Clique em **Nova conversa** (mesmo CPF, `sessionId` novo) e diga "oi, voltei".
3. Esperado: a Ana cita o atendimento anterior e pergunta se é o mesmo assunto; se responder "sim", ela delega de novo.
4. Conferir no banco:

```bash
docker compose exec postgres psql -U agents -d agents -c \
  "select criado_em, customer_id, session_id, garantia_status, resumo from ana.atendimento order by criado_em desc"
```

---

## 5. Explorar por baixo (hop a hop)

### 5.1 Ver os hops nos logs

Em outro terminal, deixe rodando enquanto conversa pelo chat:

```bash
docker compose logs -f ana-agent investimentos-agent cdb-mcp tracking-money-mcp \
  | grep -E "ana\.chat|ana\.tool|a2a\.|especialista\.tool|mcp\.tool"
```

Sequência esperada num turno com delegação:

```
ana.tool.delegar_investimentos      → a Ana decidiu delegar
a2a.task.start                      → o especialista recebeu a Task (contextId = sessionId)
especialista.tool.call ... durationMs → cada tool que o LLM do especialista escolheu
mcp.tool.call tool=listar_...       → o MCP server atendeu
a2a.task.completed confidence=...   → o especialista respondeu no schema §9
a2a.delegacao ... state=TASK_STATE_COMPLETED durationMs=...
ana.tool.delegar_investimentos.ok
ana.chat ... delegou=true durationMs=...
```

### 5.2 Falar com a Ana direto (sem o front)

A Ana recebe `cpf` (valida e resolve `customerId` no cadastro mock); o CPF para na Ana e não segue no A2A.

```bash
curl -s -X POST 'localhost:8080/chat?debug=true' -H 'Content-Type: application/json' \
  -d '{"sessionId":"explorar-1","cpf":"111.001.001-05","message":"meu dinheiro sumiu"}' | jq
```

```bash
curl -s -X POST 'localhost:8080/chat?debug=true' -H 'Content-Type: application/json' \
  -d '{"sessionId":"explorar-1","cpf":"111.001.001-05","message":"estava em investimentos e nao encontro"}' | jq
```

### 5.3 Falar A2A com o especialista (sem a Ana)

O Agent Card, que é o contrato descoberto pela Ana:

```bash
curl -s localhost:8081/.well-known/agent-card.json | jq
```

Uma Task A2A 1.0 na mão. O header `A2A-Version: 1.0` é obrigatório; o `customerId` vai num DataPart:

```bash
curl -s -X POST localhost:8081/ -H 'Content-Type: application/json' -H 'A2A-Version: 1.0' \
  -d '{"jsonrpc":"2.0","id":1,"method":"SendMessage","params":{"message":{"messageId":"m-1","role":"ROLE_USER","contextId":"explorar-a2a","parts":[{"text":"cliente nao encontra dinheiro que estava em investimentos"},{"data":{"customerId":"cli-002"}}]}}}' \
  | jq '.result.task | {state: .status.state, parts: .artifacts[0].parts}'
```

### 5.4 Ver a memória no Postgres

```bash
docker compose exec postgres psql -U agents -d agents -c '\dt ana.*' -c '\dt investimentos.*'
```

```bash
docker compose exec postgres psql -U agents -d agents -c 'select * from ana.chat_memory limit 5'
```

A Ana guarda uma linha por `sessionId` (schema `ana`). O especialista guarda uma por `contextId` A2A (schema `investimentos`), com as chamadas e os resultados das tools.

A cada delegação bem-sucedida, a Ana também grava em `ana.atendimento` (por `customerId`):

```bash
docker compose exec postgres psql -U agents -d agents -c \
  "select criado_em, customer_id, session_id, garantia_status, resumo from ana.atendimento order by criado_em desc"
```

---

## 6. Experimentos de falha

| Experimento | Como | O que esperar |
|---|---|---|
| Especialista fora | `make smoke-falha` (automático), ou `docker compose stop investimentos-agent` e conversar | Turno 2: a Ana responde "não consegui consultar seus investimentos agora…"; o chat não quebra (HTTP 200). Log `a2a.delegacao.erro` / `ana.tool.delegar_investimentos.indisponivel` |
| Um MCP fora | `docker compose stop cdb-mcp` e conversar com `cli-001` | Esperado: o especialista continua respondendo; o erro da tool volta para o LLM, que deve citar a falha em `risks`, baixar a `confidence` e tirar `cdb-mcp` de `sources` (depende do modelo) |
| cred-mcp fora | `docker compose stop cred-mcp` e conversar com `888.008.008-31` | o especialista responde sem a informação da conta garantia; pode registrar a limitação em `risks` e baixar a `confidence` (depende do modelo) |
| Ana fora | `docker compose stop ana-agent` e mandar mensagem no chat | Aviso "A Ana está indisponível no momento…" no chat, sem perder o histórico da tela |

Para voltar ao normal:

```bash
docker compose start investimentos-agent cdb-mcp cred-mcp ana-agent
```

---

## 7. Alternativa: rodar fora do Docker (debug na IDE)

Use para colocar breakpoints. No `.env`, o `LLM_BASE_URL` precisa ser alcançável do Mac: para Ollama, `http://localhost:11434/v1`.

| Terminal | Comando | Porta |
|---|---|---|
| 1 | `make run-mcps` | 8083, 8082 e 8084 |
| 2 | `make run-investimentos` (sobe o Postgres do compose; espera os MCPs) | 8081 |
| 3 | `make run-ana` | 8080 |
| 4 | `make run-web` | 3000 |

Ordem importa: MCPs → investimentos → Ana → web. O `investimentos-agent` conecta nos MCPs no startup. Para depurar um serviço na IDE, rode os demais pelo `make` e esse pela IDE, com as mesmas variáveis do `.env`.

---

## 8. Encerrar

```bash
make down        # para e remove containers e volumes do compose (apaga a memória das conversas)
make db-down     # se usou só o Postgres (seção 7)
make db-reset    # apaga o Postgres local e a memória das conversas
```

---

## Problemas comuns

| Sintoma | Causa provável | O que fazer |
|---|---|---|
| `make up` falha com "defina LLM_API_KEY no .env" | `.env` ausente ou sem chave | Seção 1 |
| `npm: command not found` no `make up` | O `build` também compila o chat-web | Instale Node 24 |
| Ana ou especialista respondem erro de conexão com o LLM | URL errada para o contexto (Docker × host) | `host.docker.internal` dentro do compose, `localhost` fora |
| Turno 2 volta "não consegui consultar…" mesmo com tudo no ar | O especialista não conseguiu gerar o JSON §9 (modelo pequeno) ou estourou o timeout | Veja `a2a.task.failed` nos logs; use um modelo maior (`qwen2.5:7b`, Claude, GPT) |
| A Ana não delega no turno 2 | O modelo não chamou a tool | Modelos pequenos falham nisso; troque o `LLM_MODEL` |
| `investimentos-agent` não sobe | MCP servers fora do ar no startup | No compose isso é automático; fora dele rode `make run-mcps` antes |
| Respostas muito lentas | Ollama em CPU ou modelo grande | Normal; o BFF espera até 120s |

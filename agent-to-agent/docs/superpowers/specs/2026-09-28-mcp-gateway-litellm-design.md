# MCP Gateway com LiteLLM — Design

Data: 2026-09-28. Complementa [docs/AI-GATEWAY.md](../../AI-GATEWAY.md) (LiteLLM já é o gateway de LLM da POC).

## 1. Objetivo

Colocar os três MCP servers (`cdb-mcp`, `tracking-money-mcp`, `cred-mcp`) atrás do LiteLLM, que passa a ser o gateway único de LLM **e** MCP. No compose, os agentes (`ana-agent`, `investimentos-agent`) acessam as tools **sempre** pelo gateway.

## 2. Decisões

| Decisão | Escolha |
|---|---|
| Alternância direto x gateway | **Não.** No compose, sempre pelo gateway. Fora do Docker (`make run-*`, testes de integração) os `application.yml` continuam apontando direto para `localhost`. |
| Autenticação | **Master key única** (`LITELLM_MASTER_KEY`) nos dois agentes, via header `x-litellm-api-key: Bearer <chave>`. Virtual keys por agente ficam para depois. |
| Endpoints | **Um por servidor:** `http://litellm:4000/{cdb_mcp,tracking_money_mcp,cred_mcp}/mcp`. Mantém um `McpClient` por MCP e o isolamento de falha. |
| Prefixo das tools | O LiteLLM sempre prefixa (`cdb_mcp-listar_posicoes_cdb`). O especialista **remove o prefixo no cliente** (`toolNameMapper`); o LLM, o prompt e a allowlist continuam com os nomes originais. A Ana continua chamando `consultar_solicitacoes_credito` sem prefixo. |
| Imagem | Fixar `ghcr.io/berriai/litellm:v1.102.1` (versão validada no spike; o código MCP do LiteLLM está em `_experimental`). |

## 3. Evidências do spike (2026-09-28, LiteLLM 1.102.1)

- `initialize`, `tools/list` e `tools/call` funcionam pelo gateway para os três MCPs (Streamable HTTP, SDK Java 2.0.1).
- Sem a chave, o gateway responde 401.
- `/mcp/` agrega as 6 tools; `/{servidor}/mcp` e `/mcp/{servidor}` expõem só as tools do servidor.
- `tools/list` **sempre** devolve os nomes com prefixo `{servidor}-{tool}`. O prefixo é fixo no código (`add_prefix=True  # Always add server prefix`), sem configuração para desligar.
- `tools/call` aceita o nome com prefixo **e** sem prefixo, com o mesmo resultado da chamada direta.
- LangChain4j MCP 1.20.0-beta30:
  - `StreamableHttpMcpTransport.Builder.customHeaders(Map)` envia o header.
  - Em `McpToolProvider`, o `filter` é aplicado **antes** do `toolNameMapper`.
  - O `McpToolExecutor` executa com o nome **original** (com prefixo), não com o nome mapeado.

## 4. Arquitetura

```
ana-agent ──────────── MCP ──▶ litellm:4000/cred_mcp/mcp ──────────▶ cred-mcp:8084
investimentos-agent ── MCP ──▶ litellm:4000/cdb_mcp/mcp ───────────▶ cdb-mcp:8083
                           ──▶ litellm:4000/tracking_money_mcp/mcp ▶ tracking-money-mcp:8082
                           ──▶ litellm:4000/cred_mcp/mcp ──────────▶ cred-mcp:8084
ana + investimentos ── LLM ──▶ litellm:4000/v1 (sem mudança)
```

### 4.1 Infra

- **`docker/litellm/config.yaml`:** bloco `mcp_servers` com `cdb_mcp`, `tracking_money_mcp` e `cred_mcp` (`transport: http`, URLs internas do compose).
- **`docker-compose.yml`:**
  - imagem fixada;
  - `litellm` com `depends_on` nos três MCPs `service_healthy`;
  - `investimentos-agent` com `depends_on: litellm service_healthy` (conecta nos MCPs no startup) no lugar dos três MCPs;
  - `ana-agent` sem `depends_on` no LiteLLM (conexão preguiçosa);
  - URLs `CDB_MCP_URL`, `TRACKING_MONEY_MCP_URL` e `CRED_MCP_URL` apontando para o gateway;
  - nova variável `MCP_GATEWAY_KEY: ${LITELLM_MASTER_KEY:-sk-litellm-poc}` nos dois agentes.
- **`Makefile`:** `llm-status` também testa `tools/list` em `http://localhost:4000/cdb_mcp/mcp` com a master key.

### 4.2 Código

- **investimentos-agent:**
  - nova classe `NomeTool` com `static String semPrefixo(String nome)`: remove até o primeiro `-`, inclusive, e devolve o nome intacto se não houver `-`. Nenhuma tool da POC tem `-` no nome.
  - `EspecialistaConfig`:
    - propriedade `mcp.gateway-key: ${MCP_GATEWAY_KEY:}`;
    - `static Map<String, String> cabecalhosGateway(String chave)`, que devolve um map vazio se a chave estiver em branco;
    - `mcpClient(chave, url, gatewayKey)` passa `customHeaders(...)`;
    - `provedorMcp` troca `filterToolNames` por `filter((c, spec) -> TOOLS_DO_ESPECIALISTA.contains(NomeTool.semPrefixo(spec.name())))` e adiciona `toolNameMapper((c, spec) -> NomeTool.semPrefixo(spec.name()))`.
- **ana-agent:**
  - `CredMcpSolicitacoesCredito`:
    - `conectandoEm(String url, Duration timeout, String gatewayKey)`;
    - `static Map<String, String> cabecalhosGateway(String chave)` com a mesma regra do especialista.
  - Propriedade `cred.gateway-key: ${MCP_GATEWAY_KEY:}`, repassada por `AnaConfig`. `TOOL` continua `consultar_solicitacoes_credito`.

## 5. Erros

- **LiteLLM fora no startup:** o especialista não sobe (compose espera o gateway ficar healthy).
- **LiteLLM fora depois do startup:** as tools do especialista falham e viram `risk` (como um MCP fora hoje); a Ana devolve `CreditoIndisponivelException` e descarta o client.
- **Um MCP fora com o gateway no ar:** só o client daquele servidor falha; `failIfOneServerFails(false)` mantém os outros.
- **Chave errada (401):** o especialista falha no startup, a Ana devolve crédito indisponível e o `llm-status` mostra o HTTP 401.

## 6. Testes

- **`NomeToolTest`:** nome com prefixo, sem `-`, só o primeiro `-` removido, e nulo/vazio.
- **`EspecialistaConfigTest`:**
  - o caso atual (nomes sem prefixo, modo direto) continua passando;
  - novo caso com nomes prefixados: o especialista vê os 5 nomes limpos e não vê o crédito;
  - novo caso de execução: chamar `listar_posicoes_cdb` executa `cdb_mcp-listar_posicoes_cdb` no `McpClient`;
  - `cabecalhosGateway`: com chave e em branco.
- **`CredMcpSolicitacoesCreditoTest`:** `cabecalhosGateway` com chave e em branco; os casos atuais ficam.
- **Compose:** `make up`; `curl` `tools/list` por servidor pelo gateway; logs do LiteLLM mostrando `tools/call`; `make smoke` sem nenhuma falha de conexão ou 401 (as falhas do modelo 7b são conhecidas).
- **`make test`** verde.

## 7. Documentação

- **README:** diagramas ASCII e Mermaid com o LiteLLM como barramento de LLM **e** de MCP; somem as setas MCP diretas.
- **`docs/AI-GATEWAY.md`:** seção "MCP Gateway" com evidências, decisão e próximos passos:
  - virtual key por agente (Ana só em `cred_mcp`);
  - rede separada para impor o gateway;
  - revisar a imagem ao atualizar o LiteLLM.
- **`docs/GUIA-TESTES.md`:** como listar e chamar tools pelo gateway.

## 8. Fora do escopo

- Virtual keys, Postgres no LiteLLM e painel de gasto.
- Alternar entre acesso direto e gateway no compose.
- Rede Docker separada (os MCPs continuam alcançáveis diretamente pela rede do compose).
- Guardrails nas tools.

# cred-mcp — resgate de CDB retido em conta garantia (cartão de crédito)

- **Data:** 2026-09-23
- **Status:** aprovado em brainstorming
- **Objetivo:** na jornada "meu dinheiro sumiu", localizar o dinheiro de um resgate de CDB que foi retido em **conta garantia** por causa de gastos no cartão de crédito; identificar o cliente pelo **CPF** no chat; e, quando o cliente voltar em outra sessão, a Ana **lembrar do atendimento anterior**.
- **Relacionado:** `2026-09-22-a2a-poc-design.md` (POC, schema §9, contrato A2A), `2026-09-23-chat-web-design.md` (frontend).

## 1. Decisões

| Tema | Decisão |
|---|---|
| Novo MCP | `cred-mcp` (:8084), mesmo padrão de `cdb-mcp` (Spring Boot, MCP Streamable HTTP em `/mcp`, mock em memória) |
| Quem consulta | O especialista de investimentos, como 3º `McpClient` no `McpToolProvider` |
| Identificação | O chat envia `cpf`; a Ana resolve CPF → `customerId` num cadastro mock. MCPs e A2A continuam por `customerId`. CPF nunca vai ao LLM nem à memória de chat |
| Memória entre sessões | Abordagem A: a Ana grava um registro por delegação em `ana.atendimento` (chave `customerId`) e **injeta de forma determinística** os 3 mais recentes de outras sessões no system prompt |
| §9 | Ganha campo opcional `situacaoGarantia`, validado no executor A2A |
| Cenários | 4 clientes novos (`cli-005`..`cli-008`), um por status; `cli-001`..`cli-004` inalterados |

## 2. cred-mcp

### 2.1 Tool

`consultar_conta_garantia` — entrada `{customerId}` (JSON Schema estrito, igual ao `cdb-mcp`). Descrição para o LLM: lista as retenções em conta garantia de resgates de investimento do cliente, motivadas por gastos no cartão de crédito; retorna array JSON (vazio se o resgate não passou pela garantia).

Log: `mcp.tool.call tool=consultar_conta_garantia customerId=... itens=...`.

### 2.2 Modelo

```java
enum StatusGarantia { LIBERADO_CONTA, EM_ANALISE, RETIDO_ATE_PAGAMENTO_FATURA, RETIDO_PARCIAL }

record RetencaoGarantia(String resgateId, LocalDateTime dataEntrada, StatusGarantia status,
                        BigDecimal valorResgatado, BigDecimal valorRetido, BigDecimal valorLiberado,
                        BigDecimal gastoCartao, LocalDate vencimentoFatura /* nullable */, String detalhe)
```

Significado dos status:

| Status | Significado |
|---|---|
| `LIBERADO_CONTA` | Passou pela garantia e foi encaminhado integralmente para a conta corrente |
| `EM_ANALISE` | Está na conta garantia em análise por gastos no cartão; sem prazo |
| `RETIDO_ATE_PAGAMENTO_FATURA` | Retido integralmente até o cliente pagar a fatura do cartão |
| `RETIDO_PARCIAL` | Gasto no cartão menor que o resgate: a parte do gasto fica retida, o restante é liberado |

**Invariante:** `valorRetido + valorLiberado = valorResgatado` (verificada em teste sobre todo o mock).

### 2.3 Cenários mock

Todos com resgate de CDB de R$ 10.000,00 `LIQUIDADO`.

| cliente | CPF de teste | status garantia | retido / liberado | gasto cartão | tracking-money |
|---|---|---|---|---|---|
| cli-005 | 555.005.005-62 | `LIBERADO_CONTA` | 0 / 10.000 | 0 | crédito 10.000 `CONCLUIDA` |
| cli-006 | 666.006.006-59 | `EM_ANALISE` | 10.000 / 0 | 4.200 | sem crédito |
| cli-007 | 777.007.007-45 | `RETIDO_ATE_PAGAMENTO_FATURA` | 10.000 / 0 | 12.000 (vence 2026-10-05) | sem crédito |
| cli-008 | 888.008.008-31 | `RETIDO_PARCIAL` | 3.500 / 6.500 | 3.500 (vence 2026-10-05) | crédito 6.500 `CONCLUIDA` |

`cdb-mcp` ganha os resgates `LIQUIDADO` (`res-005`..`res-008`) desses clientes; `tracking-money-mcp` ganha os créditos acima. Para `cli-001`..`cli-004` a tool retorna `[]`.

CPFs de teste dos cenários antigos: cli-001 `111.001.001-05`, cli-002 `222.002.002-93`, cli-003 `333.003.003-80`, cli-004 `444.004.004-76`. Todos fictícios, com dígitos verificadores válidos.

## 3. investimentos-agent

- `EspecialistaConfig`: bean `credMcpClient` (`mcp.cred-url`, env `MCP_CRED_URL`), incluído no `McpToolProvider` (`failIfOneServerFails=false` — cred-mcp fora não derruba o especialista; vira risk).
- Prompt `especialista-investimentos.txt`:
  - Passo novo: se houver resgate `LIQUIDADO` sem crédito integral `CONCLUIDA` na conta, consultar `consultar_conta_garantia`.
  - Interpretação por status (tabela 2.2): informar valor/data (`LIBERADO_CONTA`); "em análise por gastos no cartão, sem prazo" (`EM_ANALISE`); gasto e vencimento da fatura (`RETIDO_ATE_PAGAMENTO_FATURA`); valores liberado e retido e o motivo (`RETIDO_PARCIAL`).
  - `sources` pode incluir `cred-mcp`.
  - Preencher `situacaoGarantia` somente quando usou o cred-mcp e encontrou retenção; senão `null`.
- `RespostaEspecialista` (§9) ganha:

```java
record SituacaoGarantia(String status, BigDecimal valorResgatado, BigDecimal valorRetido,
                        BigDecimal valorLiberado, String proximoPasso)
// RespostaEspecialista(..., SituacaoGarantia situacaoGarantia /* nullable */)
```

- **Validação** (no `InvestimentosAgentExecutor`, antes de publicar o artifact): `status` deve ser um dos 4 valores e `valorRetido + valorLiberado = valorResgatado`. Se falhar, `situacaoGarantia = null` e acrescenta-se em `risks` "situacaoGarantia inconsistente descartada". Nunca falha a Task por isso.
- O DataPart A2A carrega o §9 completo (inclui o campo novo).

## 4. ana-agent

### 4.1 CPF

- `ChatRequisicao`: `{sessionId, cpf, message}` (sai `customerId`).
- `Cpf` (value object): normaliza para 11 dígitos, valida dígitos verificadores, rejeita sequências repetidas; `mascarado()` → `***.***.*XX-XX` (últimos 4 dígitos visíveis).
- `CadastroClientes` (mock em memória): CPF → `customerId` (tabela 2.3).
- `ChatController`:
  - campos vazios → 400 (como hoje);
  - CPF inválido → 400 `"CPF invalido"`;
  - CPF válido não cadastrado → 400 `"cliente nao encontrado"`;
  - senão resolve `customerId` e segue o fluxo atual (`InvocationParameters` com `customerId`).
- Logs usam `cpf` mascarado + `customerId`. O CPF não entra em `InvocationParameters`, prompt nem memória.

### 4.2 Histórico de atendimentos

Tabela (em `init.sql` no schema `ana`; nos ITs criada no schema `public` pelo `BaseIntegrationTest`):

```sql
CREATE TABLE IF NOT EXISTS atendimento (
  id                BIGSERIAL PRIMARY KEY,
  customer_id       TEXT        NOT NULL,
  session_id        TEXT        NOT NULL,
  criado_em         TIMESTAMPTZ NOT NULL DEFAULT now(),
  resumo            TEXT        NOT NULL,   -- answerDraft
  confidence        NUMERIC(3,2) NOT NULL,
  situacao_garantia JSONB                   -- nullable
);
CREATE INDEX IF NOT EXISTS atendimento_customer_criado ON atendimento (customer_id, criado_em DESC);
```

- `AtendimentoRepository` (JDBC puro sobre o `memoriaDataSource`):
  - `registrar(customerId, sessionId, RespostaInvestimentos)`;
  - `recentesDeOutrasSessoes(customerId, sessionIdAtual, limite=3)` → ordem decrescente de `criado_em`.
- **Escrita:** `DelegacaoInvestimentosTool`, após delegação bem-sucedida. `INDISPONIVEL` não grava. Falha de gravação → log `warn`, resposta segue.
- **Leitura:** `ChatController`, a cada turno, formata os registros em texto curto e passa para `AnaAssistant.conversar(...)` como `@V("atendimentosAnteriores")` no system prompt. Formato por linha:
  `22/09 14:03 — <resumo> [garantia: RETIDO_PARCIAL, liberado 6500.00, retido 3500.00]` (colchete omitido quando `situacao_garantia` é nulo). Sem registros: `nenhum`.
- `ana-system.txt` ganha:
  - bloco `Atendimentos anteriores deste cliente: {{atendimentosAnteriores}}`;
  - regra: se houver atendimento anterior, no primeiro turno mencioná-lo ("da última vez vimos que…") e perguntar se é o mesmo assunto; se for, delegar de novo para trazer o status atual. Nunca afirmar o status atual só com base no histórico.
- `RespostaInvestimentos` espelha `situacaoGarantia` (nullable).

## 5. chat-web

- Cabeçalho: campo **CPF** com máscara (`000.000.000-00`) + botão **Iniciar atendimento** no lugar do select; lista **CPFs de teste** (clicável, preenche o campo) com a descrição de cada cenário (`cli-001`..`cli-008`).
- Iniciar atendimento / trocar CPF / **Nova conversa** → novo `sessionId`, conversa limpa. Mesmo CPF + nova conversa = cenário "voltar depois".
- Envio desabilitado enquanto não houver CPF iniciado.
- `tipos.ts`: `ChatRequisicao = {sessionId, cpf, message}`; `RespostaEspecialista.situacaoGarantia?: SituacaoGarantia | null`.
- `PainelDebug`: bloco **Conta garantia** (status, resgatado, retido, liberado, próximo passo) quando presente.
- BFF inalterado (400 da Ana já é repassado; a mensagem de CPF aparece como aviso de sistema).

## 6. Infra e docs

- `docker-compose.yml`: serviço `cred-mcp` (8084) com healthcheck; `investimentos-agent` com `MCP_CRED_URL` e `depends_on: cred-mcp: service_healthy`.
- `Makefile`: `build`/`test` incluem `cred-mcp`; `run-mcps` sobe os 3 MCPs.
- `docker/postgres/init.sql`: tabela `ana.atendimento`.
- `smoke-test.sh`: jornada por CPF para `cli-001`..`cli-008`; cenário de retorno (sessão 1 e sessão 2 com o mesmo CPF) conferindo via `psql` que `ana.atendimento` tem registro do cliente — sem comparar texto do LLM.
- README: diagramas (cred-mcp), tabela de cenários com CPFs, objetos novos; `GUIA-TESTES.md`: roteiro "voltar depois com o mesmo CPF".

## 7. Testes

| Onde | O quê |
|---|---|
| cred-mcp | `CredMcpServerTest` (cliente MCP real): 4 status, `[]` para cli-001, invariante de soma em todo o mock |
| cdb-mcp / tracking-money-mcp | casos `cli-005`..`cli-008` nos testes existentes |
| investimentos-agent | `EspecialistaInvestimentosTest` (ScriptedChatModel) caminho cli-008; teste da validação de `situacaoGarantia` (status inválido, soma errada → `null` + risk); `McpToolProviderFake` com a 5ª tool |
| ana-agent | `CpfTest`, `CadastroClientesTest`; `ChatControllerTest` (400 inválido/desconhecido; CPF não chega ao assistente); `AnaFluxoTest` (delegação grava atendimento; sessão 2 recebe histórico no prompt); `ChatControllerIT` com Postgres real (grava na sessão 1, lê na 2, ignora a própria sessão) |
| chat-web | Vitest: campo CPF, lista de CPFs de teste, nova conversa com mesmo CPF gera novo `sessionId`, bloco Conta garantia |

## 8. Fora de escopo

Autenticação real do cliente/CPF; expiração ou LGPD do histórico; UI de listagem de atendimentos; cred-mcp consultar sistemas reais de cartão; vincular `customerId` no servidor MCP (achado já registrado no README).

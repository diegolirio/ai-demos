# Ana consulta o cred-mcp direto — solicitações de crédito recusadas

- **Data:** 2026-09-23
- **Status:** aprovado em brainstorming
- **Objetivo:** o cliente pergunta por que a solicitação de **empréstimo** ou de **cartão de crédito** foi recusada (ou em que pé está). Por ser um fluxo específico de crédito, e não de outro produto, quem atende é a **Ana**, consultando o `cred-mcp` **direto** — sem passar pelo especialista de investimentos.
- **Relacionado:** `2026-09-22-a2a-poc-design.md`, `2026-09-23-cred-mcp-conta-garantia-design.md`, `2026-09-23-chat-web-design.md`.

## 1. Decisões

| Tema | Decisão |
|---|---|
| Quem consulta | A Ana, direto no `cred-mcp` (sem A2A) |
| Como (estudo comparativo) | **`McpClient` + `@Tool` Java** na Ana — o oposto do investimentos-agent, que usa `McpToolProvider`. Os dois estilos ficam lado a lado no repo para comparação |
| Identidade | `customerId` vem de `InvocationParameters` (CPF → cadastro). A tool **não tem parâmetros**: o LLM nunca escolhe o cliente |
| Motivo da recusa | cred-mcp devolve `motivoCodigo` (interno) **e** `motivoCliente` (texto aprovado). A Ana manda ao LLM **só** o `motivoCliente`; `motivoCodigo` aparece apenas no debug |
| Especialista | Continua com a conta garantia; o `McpToolProvider` dele passa a **filtrar por nome** as 5 tools atuais, sem enxergar a nova |
| Cenários | 4 clientes novos, `cli-009`..`cli-012`; `cli-001`..`cli-008` retornam `[]` |
| Extras | Painel de debug do chat-web, memória entre sessões (`ana.atendimento`), smoke e guia de testes |
| Fora do escopo | Abrir/contestar solicitação, simulação de empréstimo, streaming |

### 1.1 McpClient + @Tool vs McpToolProvider

Ambos são tool calling; muda quem implementa a tool que o LLM vê.

| | Ana (`McpClient` + `@Tool`) | Especialista (`McpToolProvider`) |
|---|---|---|
| Tool vista pelo LLM | `consultar_solicitacoes_credito()` declarada em Java | tools do servidor MCP repassadas como estão |
| Quem preenche `customerId` | Código Java (InvocationParameters) | O LLM (argumento da tool) |
| Filtro do retorno | Java escolhe o que vai ao LLM (tira `motivoCodigo`) | JSON cru do MCP vai ao LLM |
| Falha | `INDISPONIVEL` determinístico, histórico, debug | Erro da tool volta ao LLM |
| Custo | Um método Java por tool usada; acoplado ao contrato | Descoberta automática de tools novas |

## 2. cred-mcp

### 2.1 Tool

`consultar_solicitacoes_credito` — entrada `{customerId}` (mesmo JSON Schema estrito de `consultar_conta_garantia`). Classe própria `SolicitacoesCreditoTools` + `SolicitacoesCreditoRepository` (mock em memória). Log: `mcp.tool.call tool=consultar_solicitacoes_credito customerId=... itens=...`.

### 2.2 Modelo

```java
enum TipoSolicitacao   { EMPRESTIMO_PESSOAL, CARTAO_CREDITO }
enum StatusSolicitacao { APROVADA, RECUSADA, EM_ANALISE }

record SolicitacaoCredito(String solicitacaoId, TipoSolicitacao tipo, LocalDateTime dataSolicitacao,
                          StatusSolicitacao status, BigDecimal valorSolicitado,
                          String motivoCodigo, String motivoCliente, String proximoPasso,
                          LocalDate reavaliacaoApos)
```

- `valorSolicitado`: valor do empréstimo ou limite pedido no cartão.
- `motivoCodigo` / `motivoCliente`: preenchidos **se e somente se** `status = RECUSADA` (invariante testada sobre todo o mock).
- `reavaliacaoApos`: opcional.

### 2.3 Cenários mock

Data de todas as solicitações: `2026-09-20T10:30`.

| cliente | CPF | solicitações |
|---|---|---|
| cli-009 | 999.009.009-28 | `sol-009` EMPRESTIMO_PESSOAL R$ 30.000,00 **RECUSADA** · `RENDA_INSUFICIENTE` · "A parcela do valor pedido compromete mais do que o permitido da renda informada" · próximo passo "Simule um valor menor ou atualize sua renda no app" · sem reavaliação |
| cli-010 | 101.010.010-61 | `sol-010` CARTAO_CREDITO limite R$ 5.000,00 **RECUSADA** · `RESTRICAO_CADASTRAL` · "Encontramos uma pendência no seu CPF em órgãos de proteção ao crédito" · "Regularize a pendência e faça uma nova solicitação" · reavaliação após 2026-10-23 |
| cli-011 | 121.011.011-30 | `sol-011a` CARTAO_CREDITO R$ 3.000,00 **APROVADA** · "Cartão aprovado; chega em até 10 dias úteis" — e `sol-011b` EMPRESTIMO_PESSOAL R$ 20.000,00 **RECUSADA** · `RELACIONAMENTO_RECENTE` · "Sua conta tem menos de 6 meses de relacionamento com o banco" · "Uma nova análise pode ser feita depois da data de reavaliação" · reavaliação após 2027-01-15 |
| cli-012 | 131.012.012-92 | `sol-012` EMPRESTIMO_PESSOAL R$ 15.000,00 **EM_ANALISE** · "Resposta em até 2 dias úteis" |

## 3. Ana

### 3.1 Componentes (pacote `poc.a2a.ana.credito`, espelhando `investimentos/`)

| Unidade | Papel |
|---|---|
| `SolicitacaoCredito` (record) | Cópia do contrato do cred-mcp (tipo/status como `String`, tolerante); `paraTextoLlm()` sem `motivoCodigo`; `resumo(List)` para o histórico |
| `SolicitacoesCredito` (interface) | Porta: `List<SolicitacaoCredito> consultar(String customerId)`; lança `CreditoIndisponivelException` |
| `CredMcpSolicitacoesCredito` | Adaptador: `McpClient.executeTool("consultar_solicitacoes_credito")` + parse JSON. **Conexão preguiçosa**: cria o `McpClient` na primeira chamada (via `Supplier<McpClient>`); qualquer falha fecha e descarta o client, a próxima chamada reconecta. `AutoCloseable` |
| `CreditoIndisponivelException` | cred-mcp fora, timeout, erro da tool ou JSON inválido |
| `ConsultaCreditoTool` (pacote `assistente`) | `@Tool consultar_solicitacoes_credito()` sem parâmetros |
| `UltimasConsultasCredito` (pacote `assistente`) | Lista por `requestId`, para o debug (igual a `UltimasRespostasInvestimentos`) |

A conexão preguiçosa existe porque `DefaultMcpClient` conecta no construtor: como bean comum, a Ana (porta de entrada) não subiria com o cred-mcp fora, e um restart do cred-mcp invalidaria a sessão MCP para sempre.

### 3.2 Fluxo

```
ChatController (customerId via CPF, requestId)
  └▶ AnaAssistant (tools: delegar_investimentos, consultar_solicitacoes_credito)
       └▶ ConsultaCreditoTool
            ├─ SolicitacoesCredito.consultar(customerId)      ── MCP ──▶ cred-mcp
            ├─ UltimasConsultasCredito.registrar(requestId, lista)
            ├─ HistoricoAtendimentos.registrarCredito(...)   (só se lista não vazia; falha não derruba)
            └─ texto ao LLM (sem motivoCodigo)
```

Texto devolvido ao LLM:

```
solicitacoes de credito do cliente:
- CARTAO_CREDITO APROVADA, solicitada em 20/09/2026, valor 3000.00; proximoPasso: <...>
- EMPRESTIMO_PESSOAL RECUSADA, solicitada em 20/09/2026, valor 20000.00; motivo: <motivoCliente>; proximoPasso: <...>; reavaliacaoApos: 15/01/2027
```

Lista vazia → `NENHUMA: nenhuma solicitacao de emprestimo ou cartao encontrada para o cliente.`
Falha → `INDISPONIVEL: nao foi possivel consultar as solicitacoes de credito agora.`

Logs: `ana.tool.consultar_solicitacoes_credito`, `.ok` (com `itens` e `durationMs`), `.indisponivel`.

### 3.3 Prompt

Nova jornada "solicitação de crédito": se o cliente perguntar sobre pedido de empréstimo ou cartão (recusado, status, motivo), chamar `consultar_solicitacoes_credito` sem pedir CPF. Responder com o motivo e o próximo passo como vieram; **nunca citar códigos internos, score ou regras de política**; **nunca prometer aprovação**; informar a data de reavaliação quando houver. `NENHUMA` → dizer que não encontrou solicitações. `INDISPONIVEL` → "Não consegui consultar suas solicitações de crédito agora. Tente novamente em instantes." A regra do "outro lugar" passa a dizer que a Ana ajuda com investimentos e solicitações de crédito.

### 3.4 Configuração

`cred.mcp-url` (`${CRED_MCP_URL:http://localhost:8084/mcp}`), `cred.timeout: 10s`. Compose: `CRED_MCP_URL: http://cred-mcp:8084/mcp` na Ana, **sem** `depends_on` do cred-mcp.

## 4. Contrato do `/chat`

Compatível: `debug` continua sendo a resposta do especialista. Novo campo `credito`:

```json
{ "sessionId": "...", "reply": "...", "debug": {...} | null, "credito": [ SolicitacaoCredito... ] | null }
```

Só com `?debug=true`. `[]` = consultou e não achou; `null` = não consultou neste turno.

## 5. Memória entre sessões

- `atendimento` ganha `origem TEXT NOT NULL DEFAULT 'INVESTIMENTOS'` (`ALTER TABLE ... ADD COLUMN IF NOT EXISTS` para bancos existentes).
- `HistoricoAtendimentos.registrarCredito(customerId, sessionId, resumo)`: `origem = CREDITO`, `confidence = 1.00` (dado vem direto da fonte), `resumo` montado **em Java** (ex.: `EMPRESTIMO_PESSOAL RECUSADA (A parcela ...); CARTAO_CREDITO APROVADA`), sem `motivoCodigo`.
- `Atendimento` ganha `Origem origem` (enum `INVESTIMENTOS`, `CREDITO`); construtor de 4 argumentos mantém `INVESTIMENTOS`.
- `FormatadorAtendimentos` prefixa `[credito] ` nas linhas de crédito; linhas de investimentos ficam como hoje.

## 6. chat-web

- `tipos.ts`: tipo `SolicitacaoCredito`; `ChatResposta.credito?: SolicitacaoCredito[] | null`.
- `clientes.ts`: CPFs 009..012.
- `PainelDebug`: `TurnoDebug.credito`; bloco **"Solicitações de crédito (MCP direto)"** (`aria-label="Solicitações de crédito"`) com tipo, status, valor, `motivoCodigo` marcado como *interno*, `motivoCliente`, próximo passo e reavaliação. "sem delegação" só quando `debug` e `credito` são `null`. Título: "Especialista (A2A) / Crédito (MCP)" (o `aria-label` do painel não muda).

## 7. Testes

- **cred-mcp:** repositório (invariante RECUSADA ⇔ motivos; `[]` para cli-001..008; cenários); servidor (`listTools` com as duas tools; chamada por cliente; schema estrito).
- **investimentos-agent:** provider filtrado não expõe `consultar_solicitacoes_credito` e mantém as 5 tools.
- **ana-agent (unit):** `SolicitacaoCredito` (texto sem código, resumo); `CredMcpSolicitacoesCredito` com `McpClient` mockado (parse, argumentos, erro → exceção, descarte e reconexão, conexão falha); `ConsultaCreditoTool` / fluxo com `ScriptedChatModel` (customerId de InvocationParameters, `INDISPONIVEL`, `NENHUMA`, histórico, debug, histórico quebrado não derruba); `FormatadorAtendimentos` com origem; `ChatControllerTest` com `credito` no debug.
- **ana-agent (IT):** `JdbcHistoricoAtendimentosIT` com `registrarCredito`; `ChatControllerIT` com `SolicitacoesCredito` em `@MockitoBean` (se o modelo chamou a tool, `customerId` do request).
- **chat-web:** vitest do bloco de crédito.
- **smoke:** `cenario_credito` para 009..012 (status em `.credito`, `reply` **não contém** o `motivoCodigo`) e `retorno_credito` com cli-009.
- **Docs:** README (diagrama e seção McpClient vs McpToolProvider), GUIA-TESTES (CPFs e "cred-mcp fora" para a Ana).

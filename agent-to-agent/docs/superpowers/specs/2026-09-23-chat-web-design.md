# chat-web — frontend da POC Agent-to-Agent

- **Data:** 2026-09-23
- **Status:** aprovado em brainstorming
- **Objetivo:** uma tela para conversar com a Ana e **ver a delegação A2A acontecendo** (retorno do especialista no schema §9), para demo e exploração da POC.
- **Relacionado:** `docs/superpowers/specs/2026-09-22-a2a-poc-design.md` (contrato de `POST /chat` da Ana).

## 1. Decisões

| Tema | Decisão |
|---|---|
| Pasta | `agent-to-agent/chat-web/` |
| Stack | Next.js 16 (App Router) + TypeScript, CSS Modules, sem biblioteca de UI |
| Comunicação | Route handler `POST /api/chat` no Next faz proxy (BFF) para `${ANA_URL}/chat?debug=true`. Browser nunca fala direto com a Ana — sem CORS no `ana-agent`, URL da Ana não vai para o bundle |
| Configuração | `ANA_URL` (server-side): compose `http://ana-agent:8080`; dev local `http://localhost:8080` (default) |
| Estado | Só no navegador, em memória. Memória de conversa real fica na Ana (Postgres, por `sessionId`) |
| Execução | Serviço `chat-web` no compose (porta 3000) + `make run-web` (`npm run dev`) |
| Testes | Vitest + Testing Library; typecheck; lint |

## 2. Tela

- **Cabeçalho:** seletor de cliente (`cli-001`..`cli-004`, com descrição do cenário) e botão **Nova conversa** (novo `sessionId` via `crypto.randomUUID()`). Trocar o cliente também inicia nova sessão.

| customerId | Descrição exibida |
|---|---|
| `cli-001` | Resgate de CDB em liquidação |
| `cli-002` | Resgate liquidado, crédito na conta |
| `cli-003` | CDB ativo, sem resgate |
| `cli-004` | Nada encontrado |

- **Coluna principal:** bolhas de mensagem (cliente / Ana), campo de texto + enviar (Enter envia), indicador "Ana está digitando…" enquanto a resposta não chega (respostas levam segundos: LLM + A2A). Input desabilitado durante o envio.
- **Painel lateral de debug:** um item por turno.
  - Turno com `debug` presente (houve delegação A2A): `confidence` como barra (0–1), listas de `facts`, `risks` e `sources`.
  - Turno com `debug` nulo: marcado "sem delegação" (a Ana respondeu sozinha).

## 3. Contratos

**Browser → chat-web:** `POST /api/chat` com `{sessionId, customerId, message}`.

**chat-web → Ana:** `POST ${ANA_URL}/chat?debug=true` com o mesmo corpo; resposta da Ana repassada como está: `{sessionId, reply, debug: {facts, answerDraft, confidence, risks, sources} | null}`.

**Erros do proxy:**
- Ana indisponível (erro de rede) ou status ≥ 500 → `502 {error: "A Ana está indisponível no momento. Tente novamente em instantes."}`.
- Status 400 da Ana → repassa `400` com `{error: "Requisição inválida."}`.
- Timeout do proxy: 120s (acima dos 90s de timeout A2A da Ana).

**Health:** `GET /api/health` → `200 {status: "UP"}` (healthcheck do compose).

Na UI, erro vira um aviso no chat (bolha de sistema) sem apagar o histórico da tela; o usuário pode reenviar. Especialista fora do ar não é tratado aqui — a própria Ana já responde "não consegui consultar…".

## 4. Execução

- `chat-web/Dockerfile` multi-stage `node:24-alpine`, `output: "standalone"`.
- `docker-compose.yml`: serviço `chat-web`, `3000:3000`, `ANA_URL=http://ana-agent:8080`, `depends_on: ana-agent: service_healthy`, healthcheck em `/api/health`.
- Makefile: `run-web` (`npm run dev`, `ANA_URL` default `http://localhost:8080`), `test-web` (typecheck + lint + vitest), `build` inclui `npm ci && npm run build` do chat-web.
- `smoke-test.sh`: checa `GET http://localhost:3000/api/health` = 200.
- README: seção "Chat web".

## 5. Testes

- Route handler `/api/chat` (fetch mockado): repassa corpo e `?debug=true`; devolve a resposta da Ana; 502 em erro de rede e em 5xx; 400 repassado.
- Route handler `/api/health`: 200.
- Componente de chat (Testing Library, `fetch` mockado): enviar mensagem mostra bolha do cliente e depois da Ana; painel de debug preenchido quando há `debug`; "sem delegação" quando `debug` é nulo; aviso ao receber 502; Nova conversa limpa a tela e troca o `sessionId`.

## 6. Fora do escopo

E2E com navegador (Playwright), autenticação, streaming de resposta, persistência de histórico no navegador, trace dos hops (A2A/MCP) na tela, internacionalização.

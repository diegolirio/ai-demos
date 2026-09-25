JAVA_HOME ?= $(HOME)/.sdkman/candidates/java/25.0.2-tem
export JAVA_HOME

SERVICES := cdb-mcp tracking-money-mcp cred-mcp investimentos-agent ana-agent
# Projetos com testes de integracao (@Tag("integration"), Testcontainers)
IT_SERVICES := investimentos-agent ana-agent

# .env lido por scripts/llm-env.sh (LLM_PROVIDER e chaves)
ENV_FILE ?= .env
export ENV_FILE
# LLM_PROVIDER (litellm|openrouter) -> LLM_BASE_URL/LLM_API_KEY/LLM_MODEL. Veja docs/AI-GATEWAY.md
LLM_ENV = LLM_TARGET=docker . ./scripts/llm-env.sh
LLM_ENV_LOCAL = LLM_TARGET=local . ./scripts/llm-env.sh
COMPOSE_DB = docker compose

AGENTS := ana-agent investimentos-agent

.PHONY: llm-use llm-restart llm-status llm-compare llm-up build test test-integration up down logs ps smoke smoke-falha db-up db-down db-reset run-mcps run-investimentos run-ana run-web test-web

build:
	@for s in $(SERVICES); do echo "==> build $$s"; (cd $$s && mvn -q -DskipTests package) || exit 1; done
	@echo "==> build chat-web"; cd chat-web && npm ci && npm run build

# Só unitários (rápido, sem Docker): o surefire exclui @Tag("integration") por padrão.
test:
	@for s in $(SERVICES); do echo "==> test $$s"; (cd $$s && mvn -q test) || exit 1; done

# Só @Tag("integration"): sobe Postgres + Ollama (LLM real) via Testcontainers. Precisa de Docker rodando.
# Primeira execução baixa as imagens e o modelo (veja README); depois usa a imagem local tc-ollama-<modelo>.
test-integration:
	@for s in $(IT_SERVICES); do echo "==> test-integration $$s"; (cd $$s && mvn -q -P integration-test test) || exit 1; done

# Frontend: typecheck + lint + vitest (sem Docker, sem backend)
test-web:
	cd chat-web && npm ci && npm run typecheck && npm run lint && npm test

up: build
	@$(LLM_ENV) && docker compose up -d --build --wait

down:
	docker compose down -v

logs:
	docker compose logs -f

ps:
	docker compose ps

smoke:
	./smoke-test.sh

# Derruba o especialista e confere o fallback da Ana (sem 5xx, mensagem de indisponibilidade)
smoke-falha:
	docker compose stop investimentos-agent
	./smoke-test.sh --investimentos-fora; status=$$?; docker compose start investimentos-agent; exit $$status

# --- Rodar e explorar localmente (fora do Docker, sem Testcontainers) ---

# Sobe só o Postgres do compose (localhost:55432) e espera ficar saudável
db-up:
	$(COMPOSE_DB) up -d --wait postgres

# Para o Postgres (mantém o container e os dados)
db-down:
	$(COMPOSE_DB) stop postgres

# Remove o container do Postgres e o volume anônimo dele (o compose não tem volume nomeado); o init.sql roda de novo no próximo db-up
db-reset:
	$(COMPOSE_DB) rm -sfv postgres

# Os três MCP servers em background com saída prefixada (Ctrl+C encerra todos). Não usam Postgres nem LLM.
run-mcps:
	@echo "cdb-mcp            -> http://localhost:8083/mcp"
	@echo "tracking-money-mcp -> http://localhost:8082/mcp"
	@echo "cred-mcp           -> http://localhost:8084/mcp"
	@trap 'kill 0' INT TERM; \
		( cd cdb-mcp && mvn -q spring-boot:run 2>&1 | awk '{ print "[cdb-mcp] " $$0; fflush() }' ) & \
		( cd tracking-money-mcp && mvn -q spring-boot:run 2>&1 | awk '{ print "[tracking-money-mcp] " $$0; fflush() }' ) & \
		( cd cred-mcp && mvn -q spring-boot:run 2>&1 | awk '{ print "[cred-mcp] " $$0; fflush() }' ) & \
		wait

# Especialista em localhost:8081 (espera os MCPs de run-mcps no ar: o DefaultMcpClient conecta no startup)
run-investimentos: db-up llm-up
	@$(LLM_ENV_LOCAL) && cd investimentos-agent && mvn spring-boot:run

# Ana em localhost:8080 (delegação A2A para localhost:8081)
run-ana: db-up llm-up
	@$(LLM_ENV_LOCAL) && cd ana-agent && mvn spring-boot:run

# Chat web em localhost:3000 (next dev), falando com a Ana em localhost:8080 (make run-ana ou make up)
run-web:
	cd chat-web && ([ -d node_modules ] || npm ci) && ANA_URL=$${ANA_URL:-http://localhost:8080} npm run dev

# --- AI Gateway: LiteLLM x OpenRouter (docs/AI-GATEWAY.md) ---

# Troca o provedor no .env: make llm-use P=openrouter (depois: make llm-restart)
llm-use:
	@case "$(P)" in litellm|openrouter) ;; *) echo "use: make llm-use P=litellm|openrouter"; exit 1;; esac
	@touch $(ENV_FILE)
	@if grep -q '^LLM_PROVIDER=' $(ENV_FILE); then perl -pi -e 's/^LLM_PROVIDER=.*/LLM_PROVIDER=$(P)/' $(ENV_FILE); \
		else printf '\nLLM_PROVIDER=$(P)\n' >> $(ENV_FILE); fi
	@echo "[llm] LLM_PROVIDER=$(P) em $(ENV_FILE). Rode make llm-restart para os agentes usarem."

# Sobe o litellm (so quando o provedor e litellm), para rodar os agentes fora do Docker
llm-up:
	@$(LLM_ENV_LOCAL) && if [ "$$LLM_PROVIDER" = litellm ]; then docker compose up -d --wait litellm; fi

# Recria so os agentes com o LLM atual (compose recria quando o env muda); o litellm sobe junto
llm-restart:
	@$(LLM_ENV) && docker compose up -d --wait litellm $(AGENTS)
	@$(MAKE) -s llm-status

# Provedor, URL e modelo que os agentes estao usando (sem mostrar chaves) + teste do endpoint a partir do Mac
llm-status:
	@$(LLM_ENV) && echo "provedor: $${LLM_PROVIDER:-<legado: LLM_* do .env>}" && echo "url:      $$LLM_BASE_URL" && echo "modelo:   $$LLM_MODEL" \
		&& for a in $(AGENTS); do printf '%-20s %s\n' "$$a:" "$$(docker compose exec -T $$a printenv LLM_BASE_URL LLM_MODEL 2>/dev/null | paste -sd ' ' - || echo 'fora do ar')"; done \
		&& url=$$(echo "$$LLM_BASE_URL" | sed -e 's#//litellm:#//localhost:#' -e 's#//host.docker.internal:#//localhost:#') \
		&& code=$$(curl -s -o /dev/null -w '%{http_code}' -m 10 -H "Authorization: Bearer $$LLM_API_KEY" "$$url/models"); \
		echo "GET $$url/models -> HTTP $$code"

# Roda o smoke nos dois provedores e compara resultado e tempo. Volta ao provedor do .env no fim.
llm-compare:
	@resumo=""; for p in litellm openrouter; do \
		echo; echo "======== $$p ========"; \
		( LLM_PROVIDER=$$p; $(LLM_ENV) && docker compose up -d --wait litellm $(AGENTS) ) || { resumo="$$resumo\n$$p|-|nao configurado|-"; continue; }; \
		modelo=$$(LLM_PROVIDER=$$p; $(LLM_ENV) && echo $$LLM_MODEL); \
		ini=$$(date +%s); ./smoke-test.sh > .llm-compare-$$p.log 2>&1; st=$$?; fim=$$(date +%s); \
		tail -1 .llm-compare-$$p.log; \
		resumo="$$resumo\n$$p|$$modelo|$$(tail -1 .llm-compare-$$p.log)|$$((fim-ini))s"; \
	done; \
	echo; printf "provedor|modelo|smoke|tempo$$resumo\n" | column -t -s '|'; \
	echo "(logs completos em .llm-compare-<provedor>.log)"; \
	$(LLM_ENV) && docker compose up -d --wait $(AGENTS) >/dev/null

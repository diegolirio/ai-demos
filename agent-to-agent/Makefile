JAVA_HOME ?= $(HOME)/.sdkman/candidates/java/25.0.2-tem
export JAVA_HOME

SERVICES := cdb-mcp tracking-money-mcp investimentos-agent ana-agent
# Projetos com testes de integracao (@Tag("integration"), Testcontainers)
IT_SERVICES := investimentos-agent ana-agent

# Variaveis de ambiente (LLM_*) carregadas antes do spring-boot:run local
ENV_FILE ?= .env
LOAD_ENV = set -a; [ -f $(ENV_FILE) ] && . ./$(ENV_FILE) || echo "[make] $(ENV_FILE) nao encontrado, seguindo sem ele"; set +a
# O compose interpola LLM_API_KEY (obrigatoria) mesmo quando so o postgres sobe; o postgres nao usa o valor.
COMPOSE_DB = LLM_API_KEY=$${LLM_API_KEY:-nao-usado-pelo-postgres} docker compose

.PHONY: build test test-integration up down logs ps smoke smoke-falha db-up db-down db-reset run-mcps run-investimentos run-ana

build:
	@for s in $(SERVICES); do echo "==> build $$s"; (cd $$s && mvn -q -DskipTests package) || exit 1; done

# Só unitários (rápido, sem Docker): o surefire exclui @Tag("integration") por padrão.
test:
	@for s in $(SERVICES); do echo "==> test $$s"; (cd $$s && mvn -q test) || exit 1; done

# Só @Tag("integration"): sobe Postgres + Ollama (LLM real) via Testcontainers. Precisa de Docker rodando.
# Primeira execução baixa as imagens e o modelo (veja README); depois usa a imagem local tc-ollama-<modelo>.
test-integration:
	@for s in $(IT_SERVICES); do echo "==> test-integration $$s"; (cd $$s && mvn -q -P integration-test test) || exit 1; done

up: build
	docker compose up -d --build --wait

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

# Os dois MCP servers em background com saída prefixada (Ctrl+C encerra ambos). Não usam Postgres nem LLM.
run-mcps:
	@echo "cdb-mcp            -> http://localhost:8083/mcp"
	@echo "tracking-money-mcp -> http://localhost:8082/mcp"
	@trap 'kill 0' INT TERM; \
		( cd cdb-mcp && mvn -q spring-boot:run 2>&1 | awk '{ print "[cdb-mcp] " $$0; fflush() }' ) & \
		( cd tracking-money-mcp && mvn -q spring-boot:run 2>&1 | awk '{ print "[tracking-money-mcp] " $$0; fflush() }' ) & \
		wait

# Especialista em localhost:8081 (espera os MCPs de run-mcps no ar: o DefaultMcpClient conecta no startup)
run-investimentos: db-up
	@$(LOAD_ENV); cd investimentos-agent && mvn spring-boot:run

# Ana em localhost:8080 (delegação A2A para localhost:8081)
run-ana: db-up
	@$(LOAD_ENV); cd ana-agent && mvn spring-boot:run

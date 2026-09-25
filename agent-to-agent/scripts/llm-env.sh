# Resolve LLM_PROVIDER em LLM_BASE_URL, LLM_API_KEY e LLM_MODEL (as unicas variaveis que os agentes leem).
# Uso (sourced):  LLM_TARGET=docker|local . ./scripts/llm-env.sh
#   docker: URLs vistas de dentro do compose (litellm:4000)
#   local:  URLs vistas do Mac (localhost:4000), para make run-ana / run-investimentos
# LLM_PROVIDER no ambiente tem precedencia sobre o .env (usado pelo llm-compare).
# Sem LLM_PROVIDER, vale o LLM_* explicito do .env (modo legado).

_llm_provider_override=${LLM_PROVIDER:-}
if [ -f "${ENV_FILE:-.env}" ]; then
  set -a; . "./${ENV_FILE:-.env}"; set +a
else
  echo "[llm] ${ENV_FILE:-.env} nao encontrado, seguindo sem ele" >&2
fi
[ -n "$_llm_provider_override" ] && LLM_PROVIDER=$_llm_provider_override

_llm_erro() { echo "[llm] $1" >&2; return 1; }

case "${LLM_PROVIDER:-}" in
  "")
    ;;
  litellm)
    [ -n "${LITELLM_MASTER_KEY:-}" ] || _llm_erro "LLM_PROVIDER=litellm exige LITELLM_MASTER_KEY no .env" || return 1
    if [ "${LLM_TARGET:-docker}" = local ]; then LLM_BASE_URL=http://localhost:4000/v1; else LLM_BASE_URL=http://litellm:4000/v1; fi
    LLM_API_KEY=$LITELLM_MASTER_KEY
    LLM_MODEL=${LITELLM_MODEL:-qwen-local}
    ;;
  openrouter)
    [ -n "${OPENROUTER_API_KEY:-}" ] || _llm_erro "LLM_PROVIDER=openrouter exige OPENROUTER_API_KEY no .env" || return 1
    LLM_BASE_URL=https://openrouter.ai/api/v1
    LLM_API_KEY=$OPENROUTER_API_KEY
    LLM_MODEL=${OPENROUTER_MODEL:-anthropic/claude-sonnet-5}
    ;;
  *)
    _llm_erro "LLM_PROVIDER='$LLM_PROVIDER' invalido (use litellm ou openrouter)" || return 1
    ;;
esac
export LLM_PROVIDER LLM_BASE_URL LLM_API_KEY LLM_MODEL
unset _llm_provider_override

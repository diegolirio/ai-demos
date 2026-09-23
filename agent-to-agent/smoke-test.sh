#!/usr/bin/env bash
# Jornada "meu dinheiro sumiu" contra o compose. Asserções por palavra-chave (LLM não é determinístico).
set -uo pipefail

ANA_URL=${ANA_URL:-http://localhost:8080}
falhas=0

chat() { # sessionId customerId mensagem
  curl -s -X POST "$ANA_URL/chat?debug=true" -H 'Content-Type: application/json' \
    -d "$(jq -n --arg s "$1" --arg c "$2" --arg m "$3" '{sessionId:$s, customerId:$c, message:$m}')"
}

verificar() { # descricao condicao(0/1)
  if [[ "$2" == "0" ]]; then echo "  OK   $1"; else echo "  FALHA $1"; falhas=$((falhas + 1)); fi
}

cenario() { # customerId palavra-esperada|BAIXA_CONFIANCA [facts-esperado]
  local cliente=$1 esperado=$2 factsEsperado=${3:-} sessao="smoke-$1-$(date +%s)"
  echo "== $cliente"
  local r1 r2 reply conf facts
  r1=$(chat "$sessao" "$cliente" "meu dinheiro sumiu")
  echo "  Ana: $(jq -r .reply <<<"$r1")"
  grep -qi "onde" <<<"$(jq -r .reply <<<"$r1")"; verificar "turno 1 pergunta onde estava o dinheiro" $?

  r2=$(chat "$sessao" "$cliente" "estava em investimentos e agora nao consigo encontrar")
  reply=$(jq -r .reply <<<"$r2")
  conf=$(jq -r '.debug.confidence // "null"' <<<"$r2")
  facts=$(jq -c .debug.facts <<<"$r2")
  echo "  Ana: $reply"
  echo "  especialista: confidence=$conf sources=$(jq -c .debug.sources <<<"$r2") facts=$facts"
  [[ "$conf" != "null" ]]; verificar "turno 2 delegou via A2A (debug presente)" $?
  if [[ "$esperado" == "BAIXA_CONFIANCA" ]]; then
    awk -v c="$conf" 'BEGIN { exit !(c != "null" && c < 0.5) }'; verificar "confidence < 0.5" $?
  else
    grep -qi "$esperado" <<<"$reply"; verificar "resposta menciona '$esperado'" $?
  fi
  if [[ -n "$factsEsperado" ]]; then
    grep -qi "$factsEsperado" <<<"$facts"; verificar "debug.facts contem '$factsEsperado'" $?
  fi
}

if [[ "${1:-}" == "--investimentos-fora" ]]; then
  echo "== especialista fora do ar"
  sessao="smoke-fora-$(date +%s)"
  chat "$sessao" cli-001 "meu dinheiro sumiu" >/dev/null
  status=$(curl -s -o /tmp/smoke-fora.json -w '%{http_code}' -X POST "$ANA_URL/chat?debug=true" \
    -H 'Content-Type: application/json' \
    -d "{\"sessionId\":\"$sessao\",\"customerId\":\"cli-001\",\"message\":\"estava em investimentos e nao encontro\"}")
  echo "  Ana: $(jq -r .reply /tmp/smoke-fora.json)"
  [[ "$status" == "200" ]]; verificar "HTTP 200 mesmo com especialista fora" $?
  grep -qi "instantes" <<<"$(jq -r .reply /tmp/smoke-fora.json)"; verificar "mensagem de indisponibilidade" $?
  exit $falhas
fi

cenario cli-001 "liquida"
cenario cli-002 "conta" "LIQUIDADO"
cenario cli-003 "aplicad"
cenario cli-004 BAIXA_CONFIANCA

echo
if [[ $falhas -eq 0 ]]; then echo "SMOKE OK"; else echo "SMOKE: $falhas falha(s)"; fi
exit $falhas

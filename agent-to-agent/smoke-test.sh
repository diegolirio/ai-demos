#!/usr/bin/env bash
# Jornada "meu dinheiro sumiu" contra o compose. Asserções por palavra-chave (LLM não é determinístico).
set -uo pipefail

ANA_URL=${ANA_URL:-http://localhost:8080}
WEB_URL=${WEB_URL:-http://localhost:3000}
falhas=0

chat() { # sessionId cpf mensagem
  curl -s -X POST "$ANA_URL/chat?debug=true" -H 'Content-Type: application/json' \
    -d "$(jq -n --arg s "$1" --arg c "$2" --arg m "$3" '{sessionId:$s, cpf:$c, message:$m}')"
}

verificar() { # descricao condicao(0/1)
  if [[ "$2" == "0" ]]; then echo "  OK   $1"; else echo "  FALHA $1"; falhas=$((falhas + 1)); fi
}

cenario() { # cpf palavra-esperada|BAIXA_CONFIANCA [facts-esperado] [status-garantia]
  local cpf=$1 esperado=$2 factsEsperado=${3:-} garantiaEsperada=${4:-} sessao="smoke-${1//[^0-9]/}-$(date +%s)"
  echo "== $cpf"
  local r1 r2 reply conf facts garantia
  r1=$(chat "$sessao" "$cpf" "meu dinheiro sumiu")
  echo "  Ana: $(jq -r .reply <<<"$r1")"
  grep -qi "onde" <<<"$(jq -r .reply <<<"$r1")"; verificar "turno 1 pergunta onde estava o dinheiro" $?

  r2=$(chat "$sessao" "$cpf" "estava em investimentos e agora nao consigo encontrar")
  reply=$(jq -r .reply <<<"$r2")
  conf=$(jq -r '.debug.confidence // "null"' <<<"$r2")
  facts=$(jq -c .debug.facts <<<"$r2")
  garantia=$(jq -r '.debug.situacaoGarantia.status // "null"' <<<"$r2")
  echo "  Ana: $reply"
  echo "  especialista: confidence=$conf sources=$(jq -c .debug.sources <<<"$r2") garantia=$garantia facts=$facts"
  [[ "$conf" != "null" ]]; verificar "turno 2 delegou via A2A (debug presente)" $?
  if [[ "$esperado" == "BAIXA_CONFIANCA" ]]; then
    awk -v c="$conf" 'BEGIN { exit !(c != "null" && c < 0.5) }'; verificar "confidence < 0.5" $?
  else
    grep -Eqi "$esperado" <<<"$reply"; verificar "resposta menciona '$esperado'" $?
  fi
  if [[ -n "$factsEsperado" ]]; then
    grep -qi "$factsEsperado" <<<"$facts"; verificar "debug.facts contem '$factsEsperado'" $?
  fi
  if [[ -n "$garantiaEsperada" ]]; then
    [[ "$garantia" == "$garantiaEsperada" ]]; verificar "debug.situacaoGarantia.status = $garantiaEsperada" $?
  fi
}

retorno() { # cpf customerId — sessão 1 delega, sessão 2 (mesmo CPF) deve ter o atendimento no banco
  local cpf=$1 cliente=$2 s1="smoke-ret1-$(date +%s)" s2="smoke-ret2-$(date +%s)" linhas
  echo "== retorno com o mesmo CPF ($cpf)"
  chat "$s1" "$cpf" "meu dinheiro sumiu" >/dev/null
  chat "$s1" "$cpf" "estava em investimentos e agora nao consigo encontrar" >/dev/null
  linhas=$(docker compose exec -T postgres psql -U agents -d agents -tAc \
    "select count(*) from ana.atendimento where customer_id = '$cliente'")
  [[ "${linhas:-0}" -ge 1 ]]; verificar "ana.atendimento tem registro de $cliente ($linhas)" $?
  echo "  Ana (sessão nova): $(jq -r .reply <<<"$(chat "$s2" "$cpf" "oi, voltei")")"
  status=$(curl -s -o /dev/null -w '%{http_code}' -X POST "$ANA_URL/chat" -H 'Content-Type: application/json' \
    -d '{"sessionId":"smoke-cpf","cpf":"123.456.789-09","message":"oi"}')
  [[ "$status" == "400" ]]; verificar "CPF fora do cadastro -> 400" $?
}

if [[ "${1:-}" == "--investimentos-fora" ]]; then
  echo "== especialista fora do ar"
  sessao="smoke-fora-$(date +%s)"
  chat "$sessao" 111.001.001-05 "meu dinheiro sumiu" >/dev/null
  status=$(curl -s -o /tmp/smoke-fora.json -w '%{http_code}' -X POST "$ANA_URL/chat?debug=true" \
    -H 'Content-Type: application/json' \
    -d "{\"sessionId\":\"$sessao\",\"cpf\":\"111.001.001-05\",\"message\":\"estava em investimentos e nao encontro\"}")
  echo "  Ana: $(jq -r .reply /tmp/smoke-fora.json)"
  [[ "$status" == "200" ]]; verificar "HTTP 200 mesmo com especialista fora" $?
  grep -qi "instantes" <<<"$(jq -r .reply /tmp/smoke-fora.json)"; verificar "mensagem de indisponibilidade" $?
  exit $falhas
fi

echo "== chat-web"
curl -sf "$WEB_URL/api/health" >/dev/null; verificar "chat-web /api/health responde 200" $?

cenario 111.001.001-05 "liquida"
cenario 222.002.002-93 "conta" "LIQUIDADO"
cenario 333.003.003-80 "aplicad"
cenario 444.004.004-76 BAIXA_CONFIANCA
cenario 555.005.005-62 "conta" "" LIBERADO_CONTA
cenario 666.006.006-59 "an[aá]lise" "" EM_ANALISE
cenario 777.007.007-45 "fatura" "" RETIDO_ATE_PAGAMENTO_FATURA
cenario 888.008.008-31 "6[.,]?500" "" RETIDO_PARCIAL
retorno 888.008.008-31 cli-008

echo
if [[ $falhas -eq 0 ]]; then echo "SMOKE OK"; else echo "SMOKE: $falhas falha(s)"; fi
exit $falhas

#!/usr/bin/env bash
# Jornadas "meu dinheiro sumiu" (A2A) e "solicitacao de credito" (MCP direto) contra o compose. Asserções por palavra-chave (LLM não é determinístico).
set -uo pipefail

ANA_URL=${ANA_URL:-http://localhost:8080}
WEB_URL=${WEB_URL:-http://localhost:3000}
falhas=0
contador_cenario=0

chat() { # sessionId cpf mensagem
  curl -s -X POST "$ANA_URL/chat?debug=true" -H 'Content-Type: application/json' \
    -d "$(jq -n --arg s "$1" --arg c "$2" --arg m "$3" '{sessionId:$s, cpf:$c, message:$m}')"
}

verificar() { # descricao condicao(0/1)
  if [[ "$2" == "0" ]]; then echo "  OK   $1"; else echo "  FALHA $1"; falhas=$((falhas + 1)); fi
}

cenario() { # cpf palavra-esperada|BAIXA_CONFIANCA [facts-esperado] [status-garantia]
  contador_cenario=$((contador_cenario + 1))
  local cpf=$1 esperado=$2 factsEsperado=${3:-} garantiaEsperada=${4:-} sessao="smoke-c${contador_cenario}-$(date +%s)"
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
  local cpf=$1 cliente=$2 s1="smoke-ret1-$(date +%s)" s2="smoke-ret2-$(date +%s)" linhas status
  echo "== retorno com o mesmo CPF ($cpf)"
  chat "$s1" "$cpf" "meu dinheiro sumiu" >/dev/null
  chat "$s1" "$cpf" "estava em investimentos e agora nao consigo encontrar" >/dev/null
  linhas=$(docker compose exec -T postgres psql -U agents -d agents -tAc \
    "select count(*) from ana.atendimento where customer_id = '$cliente' and session_id = '$s1'")
  [[ "${linhas:-0}" -ge 1 ]]; verificar "ana.atendimento tem registro de $cliente na sessao $s1 ($linhas)" $?
  echo "  Ana (sessão nova): $(jq -r .reply <<<"$(chat "$s2" "$cpf" "oi, voltei")")"
  status=$(curl -s -o /dev/null -w '%{http_code}' -X POST "$ANA_URL/chat" -H 'Content-Type: application/json' \
    -d '{"sessionId":"smoke-cpf","cpf":"123.456.789-09","message":"oi"}')
  [[ "$status" == "400" ]]; verificar "CPF fora do cadastro -> 400" $?
}

cenario_credito() { # cpf mensagem status-esperado [motivoCodigo que nao pode vazar na resposta]
  contador_cenario=$((contador_cenario + 1))
  local cpf=$1 mensagem=$2 statusEsperado=$3 codigo=${4:-} sessao="smoke-cred${contador_cenario}-$(date +%s)"
  echo "== $cpf (credito)"
  local r reply statuses
  r=$(chat "$sessao" "$cpf" "$mensagem")
  reply=$(jq -r .reply <<<"$r")
  statuses=$(jq -r '[.credito[]?.status] | join(",")' <<<"$r")
  echo "  Ana: $reply"
  echo "  credito (MCP direto): $(jq -c '[.credito[]? | {tipo, status, motivoCodigo}]' <<<"$r")"
  [[ "$(jq -r '.credito | type' <<<"$r")" == "array" ]]; verificar "Ana consultou o cred-mcp direto (credito presente)" $?
  grep -q "$statusEsperado" <<<"$statuses"; verificar "credito contem status $statusEsperado" $?
  if [[ -n "$codigo" ]]; then
    ! grep -q "$codigo" <<<"$reply"; verificar "resposta nao vaza o codigo interno $codigo" $?
  fi
}

retorno_credito() { # cpf customerId — sessão 1 consulta crédito, sessão 2 (mesmo CPF) deve lembrar
  local cpf=$1 cliente=$2 s1="smoke-rc1-$(date +%s)" s2="smoke-rc2-$(date +%s)" linhas
  echo "== retorno com o mesmo CPF, credito ($cpf)"
  chat "$s1" "$cpf" "minha solicitacao de emprestimo foi recusada, por que?" >/dev/null
  linhas=$(docker compose exec -T postgres psql -U agents -d agents -tAc \
    "select count(*) from ana.atendimento where customer_id = '$cliente' and session_id = '$s1' and origem = 'CREDITO'")
  [[ "${linhas:-0}" -ge 1 ]]; verificar "ana.atendimento tem registro CREDITO de $cliente na sessao $s1 ($linhas)" $?
  echo "  Ana (sessão nova): $(jq -r .reply <<<"$(chat "$s2" "$cpf" "oi, voltei")")"
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

# make smoke é rodável de novo: sem isto, na 2a execução toda sessão já tem historico e a Ana
# abre com "Da ultima vez..." no turno 1, quebrando o grep de "onde" (regra 0 do prompt).
echo "== limpando historico de atendimentos (execucao repetivel)"
docker compose exec -T postgres psql -U agents -d agents \
  -c "delete from ana.atendimento" 2>/dev/null || true

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
cenario_credito 999.009.009-28 "minha solicitacao de emprestimo foi recusada, por que?" RECUSADA RENDA_INSUFICIENTE
cenario_credito 101.010.010-61 "pedi um cartao de credito e foi recusado, qual o motivo?" RECUSADA RESTRICAO_CADASTRAL
cenario_credito 121.011.011-30 "meu emprestimo foi recusado, por que?" RECUSADA RELACIONAMENTO_RECENTE
cenario_credito 131.012.012-92 "como esta minha solicitacao de emprestimo?" EM_ANALISE
retorno_credito 999.009.009-28 cli-009

echo
if [[ $falhas -eq 0 ]]; then echo "SMOKE OK"; else echo "SMOKE: $falhas falha(s)"; fi
exit $falhas

import { MENSAGEM_ANA_INDISPONIVEL, MENSAGEM_REQUISICAO_INVALIDA } from "@/lib/mensagens";

// 120s cobre o caso comum (bem acima dos 90s de timeout A2A da Ana); um turno
// delegado muito lento (LLM 60s + A2A 90s + LLM 60s) ainda pode estourar esse
// orçamento e virar 502 aqui, sem que a Ana chegue a responder antes.
const TIMEOUT_MS = 120_000;

/** BFF: o navegador nunca fala direto com a Ana (sem CORS, URL da Ana fora do bundle). */
export async function POST(request: Request) {
  const anaUrl = process.env.ANA_URL ?? "http://localhost:8080";
  const corpo = await request.text();

  let resposta: Response;
  try {
    resposta = await fetch(`${anaUrl}/chat?debug=true`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: corpo,
      cache: "no-store",
      signal: AbortSignal.timeout(TIMEOUT_MS),
    });
  } catch (erro) {
    console.error("chat-web: falha ao chamar a Ana", erro);
    return Response.json({ error: MENSAGEM_ANA_INDISPONIVEL }, { status: 502 });
  }

  if (resposta.status === 400) {
    return Response.json({ error: await motivoDoErro(resposta) }, { status: 400 });
  }
  if (!resposta.ok) {
    return Response.json({ error: MENSAGEM_ANA_INDISPONIVEL }, { status: 502 });
  }

  try {
    return Response.json(await resposta.json());
  } catch (erro) {
    console.error("chat-web: resposta da Ana não é JSON válido", erro);
    return Response.json({ error: MENSAGEM_ANA_INDISPONIVEL }, { status: 502 });
  }
}

/** A Ana responde 400 como {"error": "..."} (ex.: CPF inválido); sem motivo legível, usa a mensagem genérica. */
async function motivoDoErro(resposta: Response): Promise<string> {
  try {
    const corpo = (await resposta.json()) as { error?: unknown };
    return typeof corpo.error === "string" && corpo.error.trim() ? corpo.error : MENSAGEM_REQUISICAO_INVALIDA;
  } catch {
    return MENSAGEM_REQUISICAO_INVALIDA;
  }
}

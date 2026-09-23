import { MENSAGEM_ANA_INDISPONIVEL, MENSAGEM_REQUISICAO_INVALIDA } from "@/lib/mensagens";

// Acima dos 90s de timeout A2A da Ana: quem desiste primeiro é a Ana, com resposta amigável
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

  if (resposta.status >= 500) {
    return Response.json({ error: MENSAGEM_ANA_INDISPONIVEL }, { status: 502 });
  }
  if (!resposta.ok) {
    return Response.json({ error: MENSAGEM_REQUISICAO_INVALIDA }, { status: 400 });
  }
  return Response.json(await resposta.json());
}

// @vitest-environment node
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { MENSAGEM_ANA_INDISPONIVEL, MENSAGEM_REQUISICAO_INVALIDA } from "@/lib/mensagens";
import { POST } from "./route";

const corpo = { sessionId: "s-1", customerId: "cli-001", message: "meu dinheiro sumiu" };

function requisicao() {
  return new Request("http://localhost:3000/api/chat", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(corpo),
  });
}

describe("POST /api/chat", () => {
  const fetchMock = vi.fn();

  beforeEach(() => {
    vi.stubGlobal("fetch", fetchMock);
    vi.stubEnv("ANA_URL", "http://ana:8080");
  });

  afterEach(() => {
    fetchMock.mockReset();
    vi.unstubAllGlobals();
    vi.unstubAllEnvs();
  });

  it("repassa o corpo para a Ana com debug=true e devolve a resposta", async () => {
    const respostaAna = {
      sessionId: "s-1",
      reply: "Seu dinheiro estava aplicado onde?",
      debug: null,
    };
    fetchMock.mockResolvedValue(Response.json(respostaAna));

    const resposta = await POST(requisicao());

    expect(resposta.status).toBe(200);
    expect(await resposta.json()).toEqual(respostaAna);
    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe("http://ana:8080/chat?debug=true");
    expect(init.method).toBe("POST");
    expect(JSON.parse(init.body)).toEqual(corpo);
  });

  it("devolve 502 quando a Ana está fora do ar", async () => {
    fetchMock.mockRejectedValue(new TypeError("fetch failed"));
    vi.spyOn(console, "error").mockImplementation(() => {});

    const resposta = await POST(requisicao());

    expect(resposta.status).toBe(502);
    expect(await resposta.json()).toEqual({ error: MENSAGEM_ANA_INDISPONIVEL });
  });

  it("devolve 502 quando a Ana responde 5xx", async () => {
    fetchMock.mockResolvedValue(new Response("erro", { status: 500 }));

    const resposta = await POST(requisicao());

    expect(resposta.status).toBe(502);
    expect(await resposta.json()).toEqual({ error: MENSAGEM_ANA_INDISPONIVEL });
  });

  it("repassa 400 quando a Ana rejeita a requisição", async () => {
    fetchMock.mockResolvedValue(new Response("bad request", { status: 400 }));

    const resposta = await POST(requisicao());

    expect(resposta.status).toBe(400);
    expect(await resposta.json()).toEqual({ error: MENSAGEM_REQUISICAO_INVALIDA });
  });

  it("devolve 502 quando a Ana responde um não-2xx diferente de 400 (ex.: 404 de ANA_URL errado)", async () => {
    fetchMock.mockResolvedValue(new Response("not found", { status: 404 }));

    const resposta = await POST(requisicao());

    expect(resposta.status).toBe(502);
    expect(await resposta.json()).toEqual({ error: MENSAGEM_ANA_INDISPONIVEL });
  });

  it("devolve 502 quando a Ana responde 2xx com corpo que não é JSON válido", async () => {
    fetchMock.mockResolvedValue(new Response("<html>não é json</html>", { status: 200 }));
    vi.spyOn(console, "error").mockImplementation(() => {});

    const resposta = await POST(requisicao());

    expect(resposta.status).toBe(502);
    expect(await resposta.json()).toEqual({ error: MENSAGEM_ANA_INDISPONIVEL });
  });

  it("devolve 502 quando a chamada para a Ana estoura o timeout", async () => {
    fetchMock.mockRejectedValue(new DOMException("timeout", "TimeoutError"));
    vi.spyOn(console, "error").mockImplementation(() => {});

    const resposta = await POST(requisicao());

    expect(resposta.status).toBe(502);
    expect(await resposta.json()).toEqual({ error: MENSAGEM_ANA_INDISPONIVEL });
  });
});

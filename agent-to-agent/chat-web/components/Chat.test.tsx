import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { MENSAGEM_ANA_INDISPONIVEL } from "@/lib/mensagens";
import { Chat } from "./Chat";

const fetchMock = vi.fn();

beforeEach(() => {
  vi.stubGlobal("fetch", fetchMock);
});

afterEach(() => {
  fetchMock.mockReset();
  vi.unstubAllGlobals();
});

const SESSION_ID_INICIAL = "sessao-inicial";

function renderChat() {
  return render(<Chat sessionIdInicial={SESSION_ID_INICIAL} />);
}

async function iniciar(cpf = "111.001.001-05") {
  const user = userEvent.setup();
  await user.clear(screen.getByLabelText("CPF"));
  await user.type(screen.getByLabelText("CPF"), cpf);
  await user.click(screen.getByRole("button", { name: "Iniciar atendimento" }));
  return user;
}

async function enviar(texto: string) {
  const user = userEvent.setup();
  await user.type(screen.getByLabelText("Mensagem"), texto);
  await user.click(screen.getByRole("button", { name: "Enviar" }));
  return user;
}

describe("Chat", () => {
  it("não deixa conversar antes de iniciar o atendimento com um CPF", () => {
    renderChat();

    expect(screen.getByLabelText("Mensagem")).toBeDisabled();
    expect(screen.getByRole("button", { name: "Iniciar atendimento" })).toBeDisabled();
    expect(screen.getByRole("button", { name: "Nova conversa" })).toBeDisabled();
  });

  it("aplica a máscara no CPF digitado", async () => {
    renderChat();
    const user = userEvent.setup();

    await user.type(screen.getByLabelText("CPF"), "88800800831");

    expect(screen.getByLabelText("CPF")).toHaveValue("888.008.008-31");
  });

  it("envia a mensagem com o CPF e mostra a resposta da Ana, sem delegação no debug", async () => {
    fetchMock.mockResolvedValue(
      Response.json({ sessionId: "s", reply: "Seu dinheiro estava aplicado onde?", debug: null }),
    );
    renderChat();
    await iniciar("111.001.001-05");

    await enviar("meu dinheiro sumiu");

    const conversa = screen.getByRole("list", { name: "Conversa" });
    expect(within(conversa).getByText("meu dinheiro sumiu")).toHaveAttribute("data-autor", "cliente");
    expect(await within(conversa).findByText("Seu dinheiro estava aplicado onde?")).toHaveAttribute(
      "data-autor",
      "ana",
    );
    expect(screen.getByText("sem delegação")).toBeInTheDocument();

    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe("/api/chat");
    const corpo = JSON.parse(init.body);
    expect(corpo).toEqual({
      sessionId: screen.getByTestId("session-id").textContent,
      cpf: "111.001.001-05",
      message: "meu dinheiro sumiu",
    });
  });

  it("CPF de teste preenche o campo e inicia o atendimento", async () => {
    fetchMock.mockResolvedValue(Response.json({ sessionId: "s", reply: "olá", debug: null }));
    renderChat();
    const user = userEvent.setup();

    await user.click(screen.getByRole("button", { name: /888\.008\.008-31/ }));
    await enviar("oi");
    await screen.findByText("olá");

    expect(screen.getByLabelText("CPF")).toHaveValue("888.008.008-31");
    expect(JSON.parse(fetchMock.mock.calls[0][1].body).cpf).toBe("888.008.008-31");
  });

  it("envia a mensagem ao pressionar Enter no campo de texto", async () => {
    fetchMock.mockResolvedValue(Response.json({ sessionId: "s", reply: "olá", debug: null }));
    renderChat();
    const user = await iniciar();

    await user.type(screen.getByLabelText("Mensagem"), "oi{Enter}");

    expect(await screen.findByText("olá")).toHaveAttribute("data-autor", "ana");
    expect(JSON.parse(fetchMock.mock.calls[0][1].body).message).toBe("oi");
  });

  it("preenche o painel de debug e o bloco Conta garantia quando houver retenção", async () => {
    fetchMock.mockResolvedValue(
      Response.json({
        sessionId: "s",
        reply: "Parte do resgate segue retida.",
        debug: {
          facts: ["Resgate res-008 RETIDO_PARCIAL"],
          answerDraft: "Parte do resgate segue retida.",
          confidence: 0.9,
          risks: [],
          sources: ["cdb-mcp", "cred-mcp"],
          situacaoGarantia: {
            status: "RETIDO_PARCIAL",
            valorResgatado: 10000,
            valorRetido: 3500,
            valorLiberado: 6500,
            proximoPasso: "Pagar a fatura do cartão",
          },
        },
      }),
    );
    renderChat();
    await iniciar("888.008.008-31");

    await enviar("estava em investimentos");

    const painel = screen.getByRole("complementary", { name: "Debug do especialista" });
    expect(await within(painel).findByText("Resgate res-008 RETIDO_PARCIAL")).toBeInTheDocument();
    expect(within(painel).getByText("confidence 0.90")).toBeInTheDocument();
    const garantia = within(painel).getByRole("region", { name: "Conta garantia" });
    expect(within(garantia).getByText("RETIDO_PARCIAL")).toBeInTheDocument();
    expect(within(garantia).getByText("R$ 3.500,00")).toBeInTheDocument();
    expect(within(garantia).getByText("R$ 6.500,00")).toBeInTheDocument();
    expect(within(garantia).getByText("Pagar a fatura do cartão")).toBeInTheDocument();
  });

  it("não mostra Conta garantia quando o especialista não trouxe retenção", async () => {
    fetchMock.mockResolvedValue(
      Response.json({
        sessionId: "s",
        reply: "Em liquidação.",
        debug: { facts: [], answerDraft: "x", confidence: 0.9, risks: [], sources: ["cdb-mcp"] },
      }),
    );
    renderChat();
    await iniciar();

    await enviar("estava em investimentos");

    await screen.findByText("Em liquidação.");
    expect(screen.queryByRole("region", { name: "Conta garantia" })).not.toBeInTheDocument();
  });

  it("mostra aviso quando a Ana está indisponível, mantendo o histórico", async () => {
    fetchMock.mockResolvedValue(Response.json({ error: MENSAGEM_ANA_INDISPONIVEL }, { status: 502 }));
    renderChat();
    await iniciar();

    await enviar("oi");

    expect(await screen.findByText(MENSAGEM_ANA_INDISPONIVEL)).toHaveAttribute("data-autor", "sistema");
    expect(screen.getByText("oi")).toBeInTheDocument();
  });

  it("mostra o motivo quando a Ana recusa o CPF", async () => {
    fetchMock.mockResolvedValue(Response.json({ error: "cliente nao encontrado" }, { status: 400 }));
    renderChat();
    await iniciar("123.456.789-09");

    await enviar("oi");

    expect(await screen.findByText("cliente nao encontrado")).toHaveAttribute("data-autor", "sistema");
  });

  it("nova conversa com o mesmo CPF limpa a tela e troca o sessionId", async () => {
    // Resposta gerada a cada chamada: um Response só permite ler o corpo uma vez,
    // e este teste envia duas mensagens (mockResolvedValue reusaria a mesma instância).
    fetchMock.mockImplementation(async () => Response.json({ sessionId: "s", reply: "olá", debug: null }));
    renderChat();
    await iniciar("888.008.008-31");
    const user = await enviar("oi");
    await screen.findByText("olá");
    const sessaoAnterior = screen.getByTestId("session-id").textContent;

    await user.click(screen.getByRole("button", { name: "Nova conversa" }));
    await enviar("voltei");
    await screen.findAllByText("olá");

    expect(screen.queryByText("oi")).not.toBeInTheDocument();
    expect(screen.getByTestId("session-id").textContent).not.toBe(sessaoAnterior);
    const segunda = JSON.parse(fetchMock.mock.calls[1][1].body);
    expect(segunda.cpf).toBe("888.008.008-31");
    expect(segunda.sessionId).toBe(screen.getByTestId("session-id").textContent);
  });

  it("trocar o CPF inicia nova conversa com o novo CPF", async () => {
    fetchMock.mockResolvedValue(Response.json({ sessionId: "s", reply: "olá", debug: null }));
    renderChat();
    await iniciar("111.001.001-05");
    const sessaoAnterior = screen.getByTestId("session-id").textContent;

    await iniciar("333.003.003-80");
    await enviar("oi");
    await screen.findByText("olá");

    expect(screen.getByTestId("session-id").textContent).not.toBe(sessaoAnterior);
    expect(JSON.parse(fetchMock.mock.calls[0][1].body).cpf).toBe("333.003.003-80");
  });

  it("bloqueia troca de CPF e nova conversa enquanto a requisição está pendente", async () => {
    let resolver: (value: Response) => void;
    fetchMock.mockReturnValue(
      new Promise<Response>((resolve) => {
        resolver = resolve;
      }),
    );
    renderChat();
    await iniciar();

    await enviar("oi");

    expect(screen.getByLabelText("CPF")).toBeDisabled();
    expect(screen.getByRole("button", { name: "Nova conversa" })).toBeDisabled();

    resolver!(Response.json({ sessionId: "s", reply: "olá", debug: null }));

    expect(await screen.findByText("olá")).toBeInTheDocument();
    expect(screen.getByLabelText("CPF")).not.toBeDisabled();
    expect(screen.getByRole("button", { name: "Nova conversa" })).not.toBeDisabled();
  });
});

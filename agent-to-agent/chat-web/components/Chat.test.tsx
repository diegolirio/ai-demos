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

async function enviar(texto: string) {
  const user = userEvent.setup();
  await user.type(screen.getByLabelText("Mensagem"), texto);
  await user.click(screen.getByRole("button", { name: "Enviar" }));
  return user;
}

describe("Chat", () => {
  it("envia a mensagem e mostra a resposta da Ana, sem delegação no debug", async () => {
    fetchMock.mockResolvedValue(
      Response.json({ sessionId: "s", reply: "Seu dinheiro estava aplicado onde?", debug: null }),
    );
    render(<Chat />);

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
    expect(corpo.customerId).toBe("cli-001");
    expect(corpo.message).toBe("meu dinheiro sumiu");
    expect(corpo.sessionId).toBe(screen.getByTestId("session-id").textContent);
  });

  it("preenche o painel de debug quando a Ana delegou ao especialista", async () => {
    fetchMock.mockResolvedValue(
      Response.json({
        sessionId: "s",
        reply: "Seu resgate está em liquidação.",
        debug: {
          facts: ["Resgate res-001 de R$ 5000.00 EM_LIQUIDACAO"],
          answerDraft: "Seu resgate está em liquidação.",
          confidence: 0.9,
          risks: [],
          sources: ["cdb-mcp", "tracking-money-mcp"],
        },
      }),
    );
    render(<Chat />);

    await enviar("estava em investimentos");

    const painel = screen.getByRole("complementary", { name: "Debug do especialista" });
    expect(await within(painel).findByText("Resgate res-001 de R$ 5000.00 EM_LIQUIDACAO")).toBeInTheDocument();
    expect(within(painel).getByText("confidence 0.90")).toBeInTheDocument();
    expect(within(painel).getByText("tracking-money-mcp")).toBeInTheDocument();
    expect(within(painel).queryByText("sem delegação")).not.toBeInTheDocument();
  });

  it("mostra aviso quando a Ana está indisponível, mantendo o histórico", async () => {
    fetchMock.mockResolvedValue(Response.json({ error: MENSAGEM_ANA_INDISPONIVEL }, { status: 502 }));
    render(<Chat />);

    await enviar("oi");

    expect(await screen.findByText(MENSAGEM_ANA_INDISPONIVEL)).toHaveAttribute("data-autor", "sistema");
    expect(screen.getByText("oi")).toBeInTheDocument();
  });

  it("nova conversa limpa a tela e troca o sessionId", async () => {
    fetchMock.mockResolvedValue(Response.json({ sessionId: "s", reply: "olá", debug: null }));
    render(<Chat />);
    const user = await enviar("oi");
    await screen.findByText("olá");
    const sessaoAnterior = screen.getByTestId("session-id").textContent;

    await user.click(screen.getByRole("button", { name: "Nova conversa" }));

    expect(screen.queryByText("olá")).not.toBeInTheDocument();
    expect(screen.queryAllByTestId("turno-debug")).toHaveLength(0);
    expect(screen.getByTestId("session-id").textContent).not.toBe(sessaoAnterior);
  });

  it("trocar o cliente inicia nova conversa com o novo customerId", async () => {
    fetchMock.mockResolvedValue(Response.json({ sessionId: "s", reply: "olá", debug: null }));
    render(<Chat />);
    const sessaoAnterior = screen.getByTestId("session-id").textContent;

    const user = userEvent.setup();
    await user.selectOptions(screen.getByLabelText("Cliente"), "cli-003");
    await enviar("oi");

    expect(screen.getByTestId("session-id").textContent).not.toBe(sessaoAnterior);
    expect(JSON.parse(fetchMock.mock.calls[0][1].body).customerId).toBe("cli-003");
  });
});

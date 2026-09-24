"use client";

import { type FormEvent, useState } from "react";
import { CLIENTES } from "@/lib/clientes";
import { formatarCpf, soDigitos } from "@/lib/cpf";
import { MENSAGEM_ANA_INDISPONIVEL } from "@/lib/mensagens";
import type { ChatRequisicao, ChatResposta, ErroResposta } from "@/lib/tipos";
import styles from "./Chat.module.css";
import { PainelDebug, type TurnoDebug } from "./PainelDebug";

type Mensagem = {
  id: number;
  autor: "cliente" | "ana" | "sistema";
  texto: string;
};

let proximoId = 0;
const novoId = () => ++proximoId;

export function Chat({ sessionIdInicial }: { sessionIdInicial: string }) {
  const [cpfDigitado, setCpfDigitado] = useState("");
  const [cpfAtivo, setCpfAtivo] = useState<string | null>(null);
  const [sessionId, setSessionId] = useState(sessionIdInicial);
  const [mensagens, setMensagens] = useState<Mensagem[]>([]);
  const [turnos, setTurnos] = useState<TurnoDebug[]>([]);
  const [texto, setTexto] = useState("");
  const [enviando, setEnviando] = useState(false);

  function novaConversa(cpf: string) {
    setCpfAtivo(cpf);
    setSessionId(crypto.randomUUID());
    setMensagens([]);
    setTurnos([]);
  }

  function iniciarAtendimento(evento: FormEvent) {
    evento.preventDefault();
    if (soDigitos(cpfDigitado).length === 11) novaConversa(cpfDigitado);
  }

  function usarCpfDeTeste(cpf: string) {
    setCpfDigitado(cpf);
    novaConversa(cpf);
  }

  async function enviar(evento: FormEvent) {
    evento.preventDefault();
    const mensagem = texto.trim();
    if (!mensagem || enviando || !cpfAtivo) return;

    setTexto("");
    setEnviando(true);
    setMensagens((atuais) => [...atuais, { id: novoId(), autor: "cliente", texto: mensagem }]);
    try {
      const requisicao: ChatRequisicao = { sessionId, cpf: cpfAtivo, message: mensagem };
      const resposta = await fetch("/api/chat", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(requisicao),
      });
      if (!resposta.ok) {
        const erro = (await resposta.json()) as ErroResposta;
        setMensagens((atuais) => [...atuais, { id: novoId(), autor: "sistema", texto: erro.error }]);
        return;
      }
      const dados = (await resposta.json()) as ChatResposta;
      setMensagens((atuais) => [...atuais, { id: novoId(), autor: "ana", texto: dados.reply }]);
      setTurnos((atuais) => [...atuais, { id: novoId(), mensagem, debug: dados.debug }]);
    } catch {
      setMensagens((atuais) => [...atuais, { id: novoId(), autor: "sistema", texto: MENSAGEM_ANA_INDISPONIVEL }]);
    } finally {
      setEnviando(false);
    }
  }

  return (
    <div className={styles.layout}>
      <main className={styles.principal}>
        <header className={styles.cabecalho}>
          <h1>Ana</h1>
          <form className={styles.cpf} onSubmit={iniciarAtendimento}>
            <label>
              CPF
              <input
                value={cpfDigitado}
                onChange={(e) => setCpfDigitado(formatarCpf(e.target.value))}
                placeholder="000.000.000-00"
                inputMode="numeric"
                disabled={enviando}
              />
            </label>
            <button type="submit" disabled={enviando || soDigitos(cpfDigitado).length !== 11}>
              Iniciar atendimento
            </button>
          </form>
          <button type="button" onClick={() => cpfAtivo && novaConversa(cpfAtivo)} disabled={enviando || !cpfAtivo}>
            Nova conversa
          </button>
          <small className={styles.sessao}>
            {cpfAtivo ? `Atendendo ${cpfAtivo} · ` : "Nenhum atendimento · "}
            <span data-testid="session-id">{sessionId}</span>
          </small>
          <details className={styles.cpfsTeste}>
            <summary>CPFs de teste</summary>
            <ul>
              {CLIENTES.map((cliente) => (
                <li key={cliente.cpf}>
                  <button type="button" onClick={() => usarCpfDeTeste(cliente.cpf)} disabled={enviando}>
                    {cliente.cpf} — {cliente.descricao}
                  </button>
                </li>
              ))}
            </ul>
          </details>
        </header>

        <ol className={styles.mensagens} aria-label="Conversa" aria-live="polite">
          {mensagens.map((m) => (
            <li key={m.id} className={styles[m.autor]} data-autor={m.autor}>
              {m.texto}
            </li>
          ))}
          {enviando && <li className={styles.digitando}>Ana está digitando…</li>}
        </ol>

        <form className={styles.entrada} onSubmit={enviar}>
          <input
            aria-label="Mensagem"
            value={texto}
            onChange={(e) => setTexto(e.target.value)}
            placeholder={cpfAtivo ? "Escreva para a Ana…" : "Informe o CPF para iniciar"}
            disabled={enviando || !cpfAtivo}
          />
          <button type="submit" disabled={enviando || !cpfAtivo || !texto.trim()}>
            Enviar
          </button>
        </form>
      </main>
      <PainelDebug turnos={turnos} />
    </div>
  );
}

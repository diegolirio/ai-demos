"use client";

import { type FormEvent, useState } from "react";
import { CLIENTES } from "@/lib/clientes";
import { MENSAGEM_ANA_INDISPONIVEL } from "@/lib/mensagens";
import type { ChatResposta, ErroResposta } from "@/lib/tipos";
import styles from "./Chat.module.css";
import { PainelDebug, type TurnoDebug } from "./PainelDebug";

type Mensagem = {
  id: number;
  autor: "cliente" | "ana" | "sistema";
  texto: string;
};

let proximoId = 0;
const novoId = () => ++proximoId;

export function Chat() {
  const [customerId, setCustomerId] = useState<string>(CLIENTES[0].id);
  const [sessionId, setSessionId] = useState(() => crypto.randomUUID());
  const [mensagens, setMensagens] = useState<Mensagem[]>([]);
  const [turnos, setTurnos] = useState<TurnoDebug[]>([]);
  const [texto, setTexto] = useState("");
  const [enviando, setEnviando] = useState(false);

  function novaConversa(cliente: string = customerId) {
    setCustomerId(cliente);
    setSessionId(crypto.randomUUID());
    setMensagens([]);
    setTurnos([]);
  }

  async function enviar(evento: FormEvent) {
    evento.preventDefault();
    const mensagem = texto.trim();
    if (!mensagem || enviando) return;

    setTexto("");
    setEnviando(true);
    setMensagens((atuais) => [...atuais, { id: novoId(), autor: "cliente", texto: mensagem }]);
    try {
      const resposta = await fetch("/api/chat", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ sessionId, customerId, message: mensagem }),
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
          <label>
            Cliente
            <select value={customerId} onChange={(e) => novaConversa(e.target.value)}>
              {CLIENTES.map((cliente) => (
                <option key={cliente.id} value={cliente.id}>
                  {cliente.id} — {cliente.descricao}
                </option>
              ))}
            </select>
          </label>
          <button type="button" onClick={() => novaConversa()}>
            Nova conversa
          </button>
          <small className={styles.sessao} data-testid="session-id">
            {sessionId}
          </small>
        </header>

        <ol className={styles.mensagens} aria-label="Conversa">
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
            placeholder="Escreva para a Ana…"
            disabled={enviando}
          />
          <button type="submit" disabled={enviando || !texto.trim()}>
            Enviar
          </button>
        </form>
      </main>
      <PainelDebug turnos={turnos} />
    </div>
  );
}

import type { RespostaEspecialista, SituacaoGarantia, SolicitacaoCredito } from "@/lib/tipos";
import styles from "./Chat.module.css";

export type TurnoDebug = {
  id: number;
  mensagem: string;
  debug: RespostaEspecialista | null;
  credito: SolicitacaoCredito[] | null;
};

const BRL = new Intl.NumberFormat("pt-BR", { style: "currency", currency: "BRL" });

function Lista({ titulo, itens }: { titulo: string; itens: string[] }) {
  return (
    <div>
      <h4>{titulo}</h4>
      {itens.length === 0 ? (
        <p className={styles.vazio}>nenhum</p>
      ) : (
        <ul>
          {itens.map((item, i) => (
            <li key={i}>{item}</li>
          ))}
        </ul>
      )}
    </div>
  );
}

/** Onde está o dinheiro retido em conta garantia (cred-mcp). */
function ContaGarantia({ situacao }: { situacao: SituacaoGarantia }) {
  return (
    <section className={styles.garantia} aria-label="Conta garantia">
      <h4>Conta garantia</h4>
      <dl>
        <dt>status</dt>
        <dd>{situacao.status}</dd>
        <dt>resgatado</dt>
        <dd>{BRL.format(situacao.valorResgatado)}</dd>
        <dt>retido</dt>
        <dd>{BRL.format(situacao.valorRetido)}</dd>
        <dt>liberado</dt>
        <dd>{BRL.format(situacao.valorLiberado)}</dd>
        <dt>próximo passo</dt>
        <dd>{situacao.proximoPasso}</dd>
      </dl>
    </section>
  );
}

const DATA = new Intl.DateTimeFormat("pt-BR", { timeZone: "UTC" });

/** "2027-01-15" → "15/01/2027" (UTC para não voltar um dia no fuso do navegador). */
function dataCurta(iso: string) {
  return DATA.format(new Date(`${iso}T00:00:00Z`));
}

/** Solicitações de crédito que a Ana consultou direto no cred-mcp (McpClient, sem especialista). */
function SolicitacoesCredito({ solicitacoes }: { solicitacoes: SolicitacaoCredito[] }) {
  return (
    <section className={styles.credito} aria-label={'Solicitações de crédito'}>
      <h4>Solicitações de crédito (MCP direto)</h4>
      {solicitacoes.length === 0 ? (
        <p className={styles.vazio}>nenhuma solicitação</p>
      ) : (
        solicitacoes.map((s) => (
          <dl key={s.solicitacaoId}>
            <dt>tipo</dt>
            <dd>{s.tipo}</dd>
            <dt>status</dt>
            <dd>{s.status}</dd>
            <dt>valor</dt>
            <dd>{BRL.format(s.valorSolicitado)}</dd>
            {s.motivoCodigo && (
              <>
                <dt>motivo (interno)</dt>
                <dd>
                  <code>{s.motivoCodigo}</code>
                </dd>
              </>
            )}
            {s.motivoCliente && (
              <>
                <dt>motivo p/ cliente</dt>
                <dd>{s.motivoCliente}</dd>
              </>
            )}
            <dt>próximo passo</dt>
            <dd>{s.proximoPasso}</dd>
            {s.reavaliacaoApos && (
              <>
                <dt>reavaliação após</dt>
                <dd>{dataCurta(s.reavaliacaoApos)}</dd>
              </>
            )}
          </dl>
        ))
      )}
    </section>
  );
}

/** Um item por turno: retorno do especialista (A2A) e/ou consulta de crédito direta (MCP). */
export function PainelDebug({ turnos }: { turnos: TurnoDebug[] }) {
  return (
    <aside className={styles.painel} aria-label={'Debug do especialista'}>
      <h2>Especialista (A2A) / Crédito (MCP)</h2>
      {turnos.length === 0 && <p className={styles.vazio}>Nenhum turno ainda.</p>}
      {turnos.map((turno) => (
        <section key={turno.id} className={styles.turno} data-testid="turno-debug">
          <p className={styles.turnoMensagem}>&quot;{turno.mensagem}&quot;</p>
          {turno.debug === null && turno.credito === null ? (
            <p className={styles.semDelegacao}>sem delegação</p>
          ) : (
            <>
              {turno.debug && (
                <>
                  <label className={styles.confianca}>
                    confidence {turno.debug.confidence.toFixed(2)}
                    <progress max={1} value={turno.debug.confidence} />
                  </label>
                  <Lista titulo="facts" itens={turno.debug.facts} />
                  <Lista titulo="risks" itens={turno.debug.risks} />
                  <Lista titulo="sources" itens={turno.debug.sources} />
                  {turno.debug.situacaoGarantia && <ContaGarantia situacao={turno.debug.situacaoGarantia} />}
                </>
              )}
              {turno.credito && <SolicitacoesCredito solicitacoes={turno.credito} />}
            </>
          )}
        </section>
      ))}
    </aside>
  );
}

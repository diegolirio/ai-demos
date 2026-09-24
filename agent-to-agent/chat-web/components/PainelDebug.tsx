import type { RespostaEspecialista, SituacaoGarantia } from "@/lib/tipos";
import styles from "./Chat.module.css";

export type TurnoDebug = {
  id: number;
  mensagem: string;
  debug: RespostaEspecialista | null;
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

/** Um item por turno: mostra o retorno do especialista quando a Ana delegou via A2A. */
export function PainelDebug({ turnos }: { turnos: TurnoDebug[] }) {
  return (
    <aside className={styles.painel} aria-label="Debug do especialista">
      <h2>Especialista (A2A)</h2>
      {turnos.length === 0 && <p className={styles.vazio}>Nenhum turno ainda.</p>}
      {turnos.map((turno) => (
        <section key={turno.id} className={styles.turno} data-testid="turno-debug">
          <p className={styles.turnoMensagem}>“{turno.mensagem}”</p>
          {turno.debug === null ? (
            <p className={styles.semDelegacao}>sem delegação</p>
          ) : (
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
        </section>
      ))}
    </aside>
  );
}

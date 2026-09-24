/** Retenção em conta garantia por gastos no cartão (presente só quando o especialista usou o cred-mcp). */
export type SituacaoGarantia = {
  status: "LIBERADO_CONTA" | "EM_ANALISE" | "RETIDO_ATE_PAGAMENTO_FATURA" | "RETIDO_PARCIAL";
  valorResgatado: number;
  valorRetido: number;
  valorLiberado: number;
  proximoPasso: string;
};

/** Solicitação de empréstimo/cartão consultada pela Ana direto no cred-mcp (MCP, sem especialista). */
export type SolicitacaoCredito = {
  solicitacaoId: string;
  tipo: "EMPRESTIMO_PESSOAL" | "CARTAO_CREDITO";
  dataSolicitacao: string;
  status: "APROVADA" | "RECUSADA" | "EM_ANALISE";
  valorSolicitado: number;
  /** Código interno da política de crédito: só para debug, a Ana nunca o repassa ao cliente. */
  motivoCodigo: string | null;
  motivoCliente: string | null;
  proximoPasso: string;
  reavaliacaoApos: string | null;
};

/** Retorno do especialista no schema padrão (guideline §9), exposto pela Ana com ?debug=true. */
export type RespostaEspecialista = {
  facts: string[];
  answerDraft: string;
  confidence: number;
  risks: string[];
  sources: string[];
  situacaoGarantia?: SituacaoGarantia | null;
};

export type ChatRequisicao = {
  sessionId: string;
  cpf: string;
  message: string;
};

export type ChatResposta = {
  sessionId: string;
  reply: string;
  debug: RespostaEspecialista | null;
  /** null: a Ana não consultou crédito neste turno; []: consultou e não achou. */
  credito?: SolicitacaoCredito[] | null;
};

export type ErroResposta = {
  error: string;
};

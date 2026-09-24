/** Retenção em conta garantia por gastos no cartão (presente só quando o especialista usou o cred-mcp). */
export type SituacaoGarantia = {
  status: "LIBERADO_CONTA" | "EM_ANALISE" | "RETIDO_ATE_PAGAMENTO_FATURA" | "RETIDO_PARCIAL";
  valorResgatado: number;
  valorRetido: number;
  valorLiberado: number;
  proximoPasso: string;
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
};

export type ErroResposta = {
  error: string;
};

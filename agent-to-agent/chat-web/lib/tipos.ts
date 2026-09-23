/** Retorno do especialista no schema padrão (guideline §9), exposto pela Ana com ?debug=true. */
export type RespostaEspecialista = {
  facts: string[];
  answerDraft: string;
  confidence: number;
  risks: string[];
  sources: string[];
};

export type ChatRequisicao = {
  sessionId: string;
  customerId: string;
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

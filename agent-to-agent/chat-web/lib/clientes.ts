/** CPFs de teste (fictícios) dos cenários mock; a Ana resolve CPF → customerId. */
export const CLIENTES = [
  { cpf: "111.001.001-05", id: "cli-001", descricao: "Resgate de CDB em liquidação" },
  { cpf: "222.002.002-93", id: "cli-002", descricao: "Resgate liquidado, crédito na conta" },
  { cpf: "333.003.003-80", id: "cli-003", descricao: "CDB ativo, sem resgate" },
  { cpf: "444.004.004-76", id: "cli-004", descricao: "Nada encontrado" },
  { cpf: "555.005.005-62", id: "cli-005", descricao: "Garantia: liberado para a conta" },
  { cpf: "666.006.006-59", id: "cli-006", descricao: "Garantia: em análise (cartão)" },
  { cpf: "777.007.007-45", id: "cli-007", descricao: "Garantia: retido até pagar a fatura" },
  { cpf: "888.008.008-31", id: "cli-008", descricao: "Garantia: retido parcialmente" },
  { cpf: "999.009.009-28", id: "cli-009", descricao: "Crédito: empréstimo recusado (renda)" },
  { cpf: "101.010.010-61", id: "cli-010", descricao: "Crédito: cartão recusado (restrição no CPF)" },
  { cpf: "121.011.011-30", id: "cli-011", descricao: "Crédito: cartão aprovado, empréstimo recusado" },
  { cpf: "131.012.012-92", id: "cli-012", descricao: "Crédito: empréstimo em análise" },
] as const;

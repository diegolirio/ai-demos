/** Cenários mock da POC (mesmos customerIds dos MCP servers). */
export const CLIENTES = [
  { id: "cli-001", descricao: "Resgate de CDB em liquidação" },
  { id: "cli-002", descricao: "Resgate liquidado, crédito na conta" },
  { id: "cli-003", descricao: "CDB ativo, sem resgate" },
  { id: "cli-004", descricao: "Nada encontrado" },
] as const;

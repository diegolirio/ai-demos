/** Só os dígitos, limitado a 11. */
export function soDigitos(valor: string): string {
  return valor.replace(/\D/g, "").slice(0, 11);
}

/** Máscara 000.000.000-00 aplicada conforme o usuário digita. */
export function formatarCpf(valor: string): string {
  const d = soDigitos(valor);
  if (d.length <= 3) return d;
  if (d.length <= 6) return `${d.slice(0, 3)}.${d.slice(3)}`;
  if (d.length <= 9) return `${d.slice(0, 3)}.${d.slice(3, 6)}.${d.slice(6)}`;
  return `${d.slice(0, 3)}.${d.slice(3, 6)}.${d.slice(6, 9)}-${d.slice(9)}`;
}

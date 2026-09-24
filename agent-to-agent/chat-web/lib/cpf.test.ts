import { describe, expect, it } from "vitest";
import { formatarCpf, soDigitos } from "./cpf";

describe("cpf", () => {
  it("mantém só dígitos, no máximo 11", () => {
    expect(soDigitos("888.008.008-31")).toBe("88800800831");
    expect(soDigitos("888.008.008-3199")).toBe("88800800831");
  });

  it("aplica a máscara progressivamente", () => {
    expect(formatarCpf("888")).toBe("888");
    expect(formatarCpf("8880")).toBe("888.0");
    expect(formatarCpf("8880080")).toBe("888.008.0");
    expect(formatarCpf("88800800831")).toBe("888.008.008-31");
  });
});

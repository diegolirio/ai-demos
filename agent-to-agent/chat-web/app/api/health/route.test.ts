// @vitest-environment node
import { describe, expect, it } from "vitest";
import { GET } from "./route";

describe("GET /api/health", () => {
  it("responde UP", async () => {
    const resposta = GET();

    expect(resposta.status).toBe(200);
    expect(await resposta.json()).toEqual({ status: "UP" });
  });
});

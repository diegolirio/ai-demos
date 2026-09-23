import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  // Imagem Docker enxuta: .next/standalone traz só o necessário para "node server.js"
  output: "standalone",
};

export default nextConfig;

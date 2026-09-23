import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "Chat com a Ana",
  description: "POC Agent-to-Agent: Ana → A2A → Investimentos → MCP",
};

export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return (
    <html lang="pt-BR">
      <body>{children}</body>
    </html>
  );
}

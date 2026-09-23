import { randomUUID } from "node:crypto";
import { connection } from "next/server";
import { Chat } from "@/components/Chat";

export default async function Home() {
  // Torna a rota dinâmica: cada request gera seu próprio sessionId inicial no
  // servidor, evitando congelar um UUID no prerender (hydration mismatch).
  await connection();
  return <Chat sessionIdInicial={randomUUID()} />;
}

"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { api } from "@/lib/api";
import { clearSession, getSession, setSession } from "@/lib/auth";
import type { Session } from "@/lib/types";

const AVISO_MS = 5 * 60 * 1000;

export default function SessionGuard() {
  const router = useRouter();
  const [session, setCurrentSession] = useState<Session | null>(null);
  const [ahora, setAhora] = useState(Date.now());
  const [renovando, setRenovando] = useState(false);

  useEffect(() => {
    const sync = () => setCurrentSession(getSession());
    sync();
    window.addEventListener("storage", sync);
    window.addEventListener("sgn-session-updated", sync);
    const timer = window.setInterval(() => setAhora(Date.now()), 1000);
    return () => {
      window.removeEventListener("storage", sync);
      window.removeEventListener("sgn-session-updated", sync);
      window.clearInterval(timer);
    };
  }, []);

  useEffect(() => {
    if (!session) return;
    const msLeft = session.expiresAt - ahora;
    if (!Number.isFinite(session.expiresAt) || msLeft <= 0) {
      clearSession();
      router.replace("/login?expired=1");
    }
  }, [session, ahora, router]);

  async function renovar() {
    setRenovando(true);
    try {
      const fresh = await api.renovarSesion();
      const next: Session = {
        legajo: fresh.legajo,
        nom: fresh.nom,
        tipo: fresh.tipo as Session["tipo"],
        primerIngreso: fresh.primerIngreso,
        expiresAt: fresh.expiresAt,
      };
      setSession(next);
      setCurrentSession(next);
      window.dispatchEvent(new Event("sgn-session-updated"));
    } catch {
      clearSession();
      router.replace("/login?expired=1");
    } finally {
      setRenovando(false);
    }
  }

  function salir() {
    clearSession();
    router.replace("/login");
  }

  if (!session || !Number.isFinite(session.expiresAt) || session.expiresAt - ahora > AVISO_MS) return null;
  const minutos = Math.max(0, Math.ceil((session.expiresAt - ahora) / 60000));

  return <div className="session-overlay" role="presentation">
    <section className="session-dialog" role="dialog" aria-modal="true" aria-labelledby="session-title">
      <h2 id="session-title">Tu sesión está por vencer</h2>
      <p>Se cerrará automáticamente en {minutos} {minutos === 1 ? "minuto" : "minutos"}. ¿Querés seguir trabajando?</p>
      <div className="session-actions">
        <button type="button" className="btn btn-secondary" onClick={salir}>Cerrar sesión</button>
        <button type="button" className="btn btn-primary" onClick={renovar} disabled={renovando}>
          {renovando ? "Renovando…" : "Seguir conectado"}
        </button>
      </div>
    </section>
  </div>;
}

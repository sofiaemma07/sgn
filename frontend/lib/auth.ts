import type { Session } from "./types";

const KEY = "sgn_session";
let volatileSession: Session | null = null;

export function getSession(): Session | null {
  if (typeof window === "undefined") return null;
  try {
    const raw = localStorage.getItem(KEY);
    if (!raw) return volatileSession;
    const value = JSON.parse(raw) as Partial<Session> & { token?: unknown };
    const roles: Session["tipo"][] = ["PROFESOR", "ALUMNO", "OFICINA", "ADMIN", "PRECEPTOR", "DIRECTIVO"];
    if (typeof value.legajo !== "string" || typeof value.nom !== "string"
      || !roles.includes(value.tipo as Session["tipo"])
      || typeof value.expiresAt !== "number" || !Number.isFinite(value.expiresAt)) {
      localStorage.removeItem(KEY);
      return null;
    }
    const session: Session = {
      legajo: value.legajo,
      nom: value.nom,
      tipo: value.tipo as Session["tipo"],
      primerIngreso: value.primerIngreso === true,
      expiresAt: value.expiresAt,
    };
    if ("token" in value) {
      try { localStorage.setItem(KEY, JSON.stringify(session)); }
      catch { localStorage.removeItem(KEY); }
    }
    volatileSession = session;
    return session;
  } catch {
    return volatileSession;
  }
}

export function setSession(s: Session) {
  const safeSession: Session = {
    legajo: s.legajo,
    nom: s.nom,
    tipo: s.tipo,
    primerIngreso: s.primerIngreso,
    expiresAt: s.expiresAt,
  };
  volatileSession = safeSession;
  try { localStorage.setItem(KEY, JSON.stringify(safeSession)); } catch {}
  if (typeof window !== "undefined") window.dispatchEvent(new Event("sgn-session-updated"));
}

export function clearSession() {
  volatileSession = null;
  try { localStorage.removeItem(KEY); } catch {}
  if (typeof window !== "undefined") window.dispatchEvent(new Event("sgn-session-updated"));
}

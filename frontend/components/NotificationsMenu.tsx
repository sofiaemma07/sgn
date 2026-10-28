"use client";

import { useEffect, useMemo, useRef, useState } from "react";
import { usePathname, useRouter } from "next/navigation";
import Icon from "@/components/Icon";
import { api } from "@/lib/api";
import { dateLabel } from "@/lib/presentation";
import type { NotificationItem } from "@/lib/types";

type Filter = "all" | "unread";
type NotificationWire = NotificationItem & {
  recipientId?: string;
  recipientRole?: string;
  createdAt?: string;
  targetUrl?: string;
  metadata?: NotificationItem["metadata"] & { solicitudId?: number };
};

function normalize(item: NotificationWire): NotificationItem {
  const role = String(item.recipientRole ?? item.recipient_role ?? "");
  const normalizedRole = role === "ALUMNO" ? "alumno" : role === "PROFESOR" ? "docente"
    : role === "OFICINA" || role === "ADMIN" ? "oficina_alumnos" : role as NotificationItem["recipient_role"];
  return {
    ...item,
    recipient_id: item.recipient_id || item.recipientId || "",
    recipient_role: normalizedRole,
    created_at: item.created_at || item.createdAt || "",
    target_url: item.target_url || item.targetUrl || "/",
    metadata: item.metadata ? { ...item.metadata, solicitud_id: item.metadata.solicitud_id ?? item.metadata.solicitudId } : undefined,
  };
}

export default function NotificationsMenu() {
  const router = useRouter();
  const pathname = usePathname();
  const root = useRef<HTMLDivElement>(null);
  const [items, setItems] = useState<NotificationItem[]>([]);
  const [open, setOpen] = useState(false);
  const [filter, setFilter] = useState<Filter>("all");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  useEffect(() => { setOpen(false); }, [pathname]);

  async function load() {
    try {
      const items = await api.avisos();
      setItems((items as NotificationWire[]).map(normalize));
      setError("");
    } catch (e) {
      setError(e instanceof Error ? e.message : "No se pudieron cargar las notificaciones");
    }
  }

  useEffect(() => {
    void load();
    const timer = window.setInterval(() => void load(), 30_000);
    const onFocus = () => void load();
    window.addEventListener("focus", onFocus);
    return () => {
      window.clearInterval(timer);
      window.removeEventListener("focus", onFocus);
    };
  }, []);

  useEffect(() => {
    if (!open) return;
    function outside(event: PointerEvent) {
      if (!root.current?.contains(event.target as Node)) setOpen(false);
    }
    function escape(event: KeyboardEvent) {
      if (event.key === "Escape") setOpen(false);
    }
    document.addEventListener("pointerdown", outside);
    document.addEventListener("keydown", escape);
    return () => {
      document.removeEventListener("pointerdown", outside);
      document.removeEventListener("keydown", escape);
    };
  }, [open]);

  const unread = items.filter(item => !item.read).length;
  const visibles = useMemo(() => filter === "unread" ? items.filter(item => !item.read) : items, [items, filter]);

  async function markAllRead() {
    setBusy(true);
    try {
      await api.leerTodasAvisos();
      setItems(current => current.map(item => ({ ...item, read: true })));
    } catch (e) {
      setError(e instanceof Error ? e.message : "No se pudieron marcar como leídas");
    } finally { setBusy(false); }
  }

  async function clearRead() {
    setBusy(true);
    try {
      await api.limpiarAvisosLeidos();
      setItems(current => current.filter(item => !item.read));
    } catch (e) {
      setError(e instanceof Error ? e.message : "No se pudieron limpiar las notificaciones");
    } finally { setBusy(false); }
  }

  async function openItem(item: NotificationItem) {
    try {
      if (!item.read) await api.leerAviso(item.id);
      setItems(current => current.map(value => value.id === item.id ? { ...value, read: true } : value));
      setOpen(false);
      const target = item.target_url;
      if (target.startsWith("/") && !target.startsWith("//")) router.push(target);
    } catch (e) {
      setError(e instanceof Error ? e.message : "No se pudo abrir la notificación");
    }
  }

  async function dismiss(item: NotificationItem) {
    setBusy(true);
    try {
      await api.descartarAviso(item.id);
      setItems(current => current.filter(value => value.id !== item.id));
    } catch (e) {
      setError(e instanceof Error ? e.message : "No se pudo descartar la notificación");
    } finally { setBusy(false); }
  }

  return <div className="notifications-root" ref={root}>
    <button type="button" className="notification-bell" aria-label={`Notificaciones: ${unread} sin leer`}
      aria-expanded={open} aria-controls="notifications-panel" onClick={() => setOpen(value => !value)}>
      <Icon name="bell" size={20}/>
      {unread > 0 && <span className="notification-count">{unread > 99 ? "+99" : unread}</span>}
    </button>
    {open && <section className="notifications-panel" id="notifications-panel" aria-label="Notificaciones">
      <header className="notifications-header">
        <div className="notifications-title-row"><strong>Notificaciones</strong><span>{unread} sin leer</span></div>
        <div className="notifications-tabs" role="tablist" aria-label="Filtrar notificaciones">
          <button type="button" role="tab" aria-selected={filter === "all"} className={filter === "all" ? "active" : ""} onClick={() => setFilter("all")}>Todas</button>
          <button type="button" role="tab" aria-selected={filter === "unread"} className={filter === "unread" ? "active" : ""} onClick={() => setFilter("unread")}>No leídas</button>
        </div>
        <div className="notifications-actions">
          <button type="button" onClick={markAllRead} disabled={busy || unread === 0}>Marcar todo como leído</button>
          <button type="button" onClick={clearRead} disabled={busy || items.every(item => !item.read)}>Limpiar leídas</button>
        </div>
      </header>
      {error && <p className="notifications-error" role="alert">{error}</p>}
      <div className="notifications-list">
        {visibles.map(item => <article key={item.id} className={`notification-card ${item.read ? "is-read" : "is-unread"}`}>
          <button type="button" className="notification-content" onClick={() => void openItem(item)}>
            <span className="notification-card-title">{item.title}</span>
            <span className="notification-message">{item.message}</span>
            <time className="notification-time" dateTime={item.created_at}>{dateLabel(item.created_at)}</time>
          </button>
          <button type="button" className="notification-dismiss" aria-label="Descartar notificación"
            title="Descartar" onClick={() => void dismiss(item)}>×</button>
        </article>)}
        {visibles.length === 0 && <p className="notifications-empty">{filter === "unread" ? "No tenés notificaciones sin leer." : "No tenés notificaciones."}</p>}
      </div>
    </section>}
  </div>;
}

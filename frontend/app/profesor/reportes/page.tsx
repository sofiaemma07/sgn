"use client";

import { FormEvent, useEffect, useState } from "react";
import { useSearchParams } from "next/navigation";
import Toast from "@/components/Toast";
import Shell from "@/components/Shell";
import { Guard } from "@/components/Guard";
import { api } from "@/lib/api";
import type { Reporte } from "@/lib/types";

export default function ReportesProfPage() {
  const searchParams = useSearchParams();
  const reporteId = Number(searchParams.get("reporteId")) || null;
  const [items, setItems] = useState<Reporte[]>([]);
  const [err, setErr] = useState("");
  const [ok, setOk] = useState("");
  const [sel, setSel] = useState<Reporte | null>(null);
  const [val, setVal] = useState("");
  const [mot, setMot] = useState("");
  const [closing, setClosing] = useState(0);

  function load() {
    api.reportesProf().then(setItems).catch((e) => setErr(e.message));
  }

  useEffect(() => {
    api.reportesProf().then(data => {
      setItems(data);
      const target = data.find(r => r.id === reporteId);
      if (target) setSel(target);
    }).catch((e) => setErr(e.message));
  }, [reporteId]);

  async function enviar(e: FormEvent) {
    e.preventDefault();
    if (!sel) return;
    setErr("");
    if (!mot.trim()) {
      setErr("El motivo de la solicitud es obligatorio");
      return;
    }
    try {
      await api.repSolicitar(sel.id, {
        notaId: sel.notaId,
        motivo: mot,
        valNuevo: val,
      });
      setOk("Solicitud de cambio enviada a oficina");
      setSel(null);
      load();
    } catch (ex) {
      setErr(ex instanceof Error ? ex.message : "Error");
    }
  }

  async function cerrarReporte(id: number) {
    setErr("");
    setClosing(id);
    try {
      await api.cerrarReporte(id);
      setOk("Reporte cerrado sin solicitar un cambio de nota");
      if (sel?.id === id) setSel(null);
      load();
    } catch (ex) {
      setErr(ex instanceof Error ? ex.message : "No se pudo cerrar el reporte");
    } finally {
      setClosing(0);
    }
  }

  return (
    <Guard roles={["PROFESOR"]}>
      <Shell>
        <h1>Reportes de alumnos</h1>
        {err && <div className="alert alert-error">{err}</div>}
        {ok && <Toast message={ok} onClose={() => setOk("")}/>}
        <div className="table-wrap">
          <table className="data">
            <thead>
              <tr>
                <th>Alumno</th>
                <th>Materia</th>
                <th>Motivo</th>
                <th>Fecha</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              {items.map((r) => (
                <tr key={r.id}>
                  <td>{r.alumno}</td>
                  <td>{r.materia}</td>
                  <td>{r.motivo}</td>
                  <td className="numeric">{r.fecRep?.slice(0, 16)}</td>
                  <td>
                    <div className="btn-row">
                      {r.puedeSolicitarCambio && <button type="button" className="btn btn-sm" onClick={() => setSel(r)}>
                        Solicitar cambio
                      </button>}
                      <button type="button" className="btn btn-sm" disabled={closing === r.id} onClick={() => cerrarReporte(r.id)}>
                        {closing === r.id ? "Cerrando…" : "Cerrar sin cambio"}
                      </button>
                    </div>
                  </td>
                </tr>
              ))}
              {items.length === 0 && (
                <tr>
                  <td colSpan={5} className="empty">
                    No hay reportes pendientes
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
        {sel && (
          <div className="panel">
            <h2>Solicitar corrección — {sel.alumno}</h2>
            <form className="form" onSubmit={enviar}>
              <div className="field">
                <label htmlFor="field-1">Nueva nota</label>
                <select id="field-1" value={val} onChange={(e) => setVal(e.target.value)} required>
                  <option value="">Seleccioná una nota</option>
                  {Array.from({ length: 10 }, (_, i) => <option key={i + 1} value={String(i + 1)}>{i + 1}</option>)}
                  <option value="EP">EP · En proceso</option><option value="NI">NI · No inició</option><option value="NC">NC · No corresponde</option>
                </select>
              </div>
              <div className="field">
                <label htmlFor="field-2">Motivo de la solicitud <span aria-hidden="true">*</span></label>
                <textarea id="field-2" value={mot} onChange={(e) => setMot(e.target.value)} required />
              </div>
              <div className="btn-row">
                <button type="submit" className="btn btn-primary">
                  Enviar solicitud
                </button>
                <button type="button" className="btn" onClick={() => setSel(null)}>
                  Cancelar
                </button>
              </div>
            </form>
          </div>
        )}
      </Shell>
    </Guard>
  );
}

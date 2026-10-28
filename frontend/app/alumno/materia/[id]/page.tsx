"use client";

import { FormEvent, useEffect, useState } from "react";
import Toast from "@/components/Toast";
import { label, cursoLabel } from "@/lib/presentation";
import Link from "next/link";
import { useParams } from "next/navigation";
import Shell from "@/components/Shell";
import { Guard } from "@/components/Guard";
import { api } from "@/lib/api";
import { getSession } from "@/lib/auth";
import type { Bloque, FasePlanilla, Nota } from "@/lib/types";

const ORDEN_FASES: FasePlanilla[] = ["BIM_1", "BIM_2", "FINAL_1", "BIM_3", "BIM_4", "FINAL_2", "CIERRE_1", "CIERRE_2", "CIERRE_3", "CALIF_DEF"];

function faseDeNota(nota: Nota): FasePlanilla | null {
  if (nota.tipo === "BIM" && nota.numPer >= 1 && nota.numPer <= 4) return `BIM_${nota.numPer}` as FasePlanilla;
  if ((nota.tipo === "FINAL" || nota.tipo === "CUAT") && nota.numPer === 1) return "FINAL_1";
  if ((nota.tipo === "FINAL" || nota.tipo === "CUAT") && nota.numPer === 2) return "FINAL_2";
  if (nota.tipo === "FINAL" && nota.numPer === 3) return "CALIF_DEF";
  if (nota.tipo === "DIC") return "CIERRE_1";
  if (nota.tipo === "FEB" && nota.numPer === 1) return "CIERRE_2";
  if (nota.tipo === "FEB" && nota.numPer === 2) return "CIERRE_3";
  return null;
}

function ordenNota(nota: Nota): number {
  const fase = faseDeNota(nota);
  const orden = fase ? ORDEN_FASES.indexOf(fase) : -1;
  return orden < 0 ? Number.MAX_SAFE_INTEGER : orden;
}

export default function MateriaAlumnoPage() {
  const { id } = useParams<{ id: string }>();
  const bloqueId = Number(id);
  const legajo = getSession()?.legajo || "";
  const [bloque, setBloque] = useState<Bloque | null>(null);
  const [notas, setNotas] = useState<Nota[]>([]);
  const [reportables, setReportables] = useState<number[]>([]);
  const [err, setErr] = useState("");
  const [ok, setOk] = useState("");
  const [repNota, setRepNota] = useState<Nota | null>(null);
  const [motivo, setMotivo] = useState("");
  const [sending, setSending] = useState(false);

  useEffect(() => {
    if (!legajo) return;
    Promise.all([api.materiasAlumno(), api.historialAlumno(), api.notasReportables()]).then(([bloques, historial, habilitadas]) => {
      const materiaSeleccionada = bloques.find(b => b.id === bloqueId);
      if (!materiaSeleccionada) {
        setErr("No se encontró la materia asignada a este curso");
        return;
      }
      setBloque(materiaSeleccionada);
      const items = historial.filter(n => n.borrador !== true
        && (n.materia || "").localeCompare(materiaSeleccionada.materia, "es", { sensitivity: "accent" }) === 0
        && n.curso === materiaSeleccionada.curso)
        .sort((a, b) => ordenNota(a) - ordenNota(b)
          || (a.fecCarga || "").localeCompare(b.fecCarga || ""));
      setNotas(items);
      setReportables(habilitadas);
    }).catch((e) => setErr(e.message));
  }, [bloqueId, legajo]);

  function puedeReportar(nota: Nota) {
    return nota.borrador === false && reportables.includes(nota.id);
  }

  const valoresNumericos = notas.map(n => Number(n.val)).filter(n => Number.isFinite(n) && n >= 1 && n <= 10);
  const prom = valoresNumericos.length
    ? (valoresNumericos.reduce((s, n) => s + n, 0) / valoresNumericos.length).toFixed(2)
    : "—";

  async function reportar(e: FormEvent) {
    e.preventDefault();
    if (!repNota || sending) return;
    setErr("");
    if (!motivo.trim()) {
      setErr("El motivo es obligatorio");
      return;
    }
    setSending(true);
    try {
      await api.reporte({ notaId: repNota.id, motivo });
      setReportables(current => current.filter(id => id !== repNota.id));
      setOk("Reporte enviado al docente");
      setRepNota(null);
      setMotivo("");
    } catch (ex) {
      setErr(ex instanceof Error ? ex.message : "Error");
    } finally {
      setSending(false);
    }
  }

  return (
    <Guard roles={["ALUMNO"]}>
      <Shell>
        <h1>{bloque?.materia || "Detalle de materia"}</h1>
        <p className="card-meta">
          <Link href="/alumno">← Volver</Link>
        </p>
        <p className="card-meta">
          {bloque && <>{bloque.curso} · </>}Promedio no oficial: <strong>{prom}</strong>
        </p>
        {err && <div className="alert alert-error">{err}</div>}
        {ok && <Toast message={ok} onClose={() => setOk("")}/>}

        <div className="table-wrap">
          <table className="data">
            <thead>
              <tr>
                <th>Tipo</th>
                <th>Período</th>
                <th>Detalle</th>
                <th>Nota</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              {notas.map((n) => (
                <tr key={n.id}>
                  <td>{label(n.tipo)}</td>
                  <td className="numeric">{n.numPer}</td>
                  <td>{n.titulo || "—"}</td>
                  <td>
                    {n.est === "EN_PROCESO" ? (
                      <span className="badge badge-warn">En proceso</span>
                    ) : (
                      n.val
                    )}
                  </td>
                  <td>
                    {puedeReportar(n) && <button
                      type="button"
                      className="btn btn-sm"
                      onClick={() => setRepNota(n)}
                    >
                      Reportar error
                    </button>}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>

        {repNota && (
          <div className="panel">
            <h2>Reportar error — nota {repNota.val}</h2>
            <p className="card-meta">Podés tener un solo reporte activo por docente. Esperá a que se cierre o se resuelva antes de enviar otro.</p>
            <form className="form" onSubmit={reportar}>
              <div className="field">
                <label htmlFor="field-1">Motivo <span aria-hidden="true">*</span></label>
                <textarea id="field-1"
                  value={motivo}
                  onChange={(e) => setMotivo(e.target.value)}
                  required
                />
              </div>
              <div className="btn-row">
                  <button type="submit" className="btn btn-primary" disabled={sending}>
                  {sending ? "Enviando…" : "Enviar"}
                </button>
                <button type="button" className="btn" onClick={() => setRepNota(null)}>
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

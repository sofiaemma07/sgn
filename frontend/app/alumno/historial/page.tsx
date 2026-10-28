"use client";

import { useEffect, useMemo, useState } from "react";
import Shell from "@/components/Shell";
import { Guard } from "@/components/Guard";
import { api } from "@/lib/api";
import type { Auditoria, Bloque, FasePlanilla, Nota } from "@/lib/types";

const GUION = "—";

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

function numericValue(value: string | undefined): number | null {
  if (!value) return null;
  const n = Number(value);
  return Number.isFinite(n) && n >= 1 && n <= 10 ? n : null;
}

function average(values: (string | undefined)[]): string {
  const valid = values.map(numericValue).filter((n): n is number => n !== null);
  return valid.length ? (valid.reduce((sum, n) => sum + n, 0) / valid.length).toLocaleString("es-AR", { maximumFractionDigits: 1 }) : GUION;
}

function dateLabel(value: string): string {
  const [date = "", time = ""] = value.split("T");
  const [year, month, day] = date.split("-");
  const hhmm = time.slice(0, 5);
  return `${day}/${month}/${year} - ${hhmm} hs`;
}

function notaMostrada(nota: Nota | undefined): string {
  return nota?.val && nota.val !== "-" ? nota.val : GUION;
}

function currentAndStatus(notas: Partial<Record<FasePlanilla, Nota>>) {
  const definitive = notas.CALIF_DEF;
  const closing3 = notas.CIERRE_3;
  const closing2 = notas.CIERRE_2;
  const closing1 = notas.CIERRE_1;
  const secondFinal = notas.FINAL_2;
  const firstFinal = notas.FINAL_1;

  const current = definitive || closing3 || closing2 || closing1 || secondFinal ||
    ((notas.BIM_3 || notas.BIM_4) ? undefined : firstFinal) || notas.BIM_4 || notas.BIM_3 || firstFinal || notas.BIM_2 || notas.BIM_1;
  let currentValue = notaMostrada(current);
  if (!definitive && !closing3 && !closing2 && !closing1 && !secondFinal) {
    if (notas.BIM_3 || notas.BIM_4) {
      currentValue = average([notas.BIM_3?.val, notas.BIM_4?.val]);
      if (currentValue === GUION) currentValue = notaMostrada(notas.BIM_4 || notas.BIM_3);
    } else if (!firstFinal && (notas.BIM_1 || notas.BIM_2)) {
      currentValue = average([notas.BIM_1?.val, notas.BIM_2?.val]);
      if (currentValue === GUION) currentValue = notaMostrada(notas.BIM_2 || notas.BIM_1);
    }
  }

  const settled = definitive || closing3 || closing2 || closing1 || secondFinal;
  const settledScore = numericValue(settled?.val);
  let status = "En curso";
  if (settledScore !== null && settledScore > 6) status = "Aprobada";
  else if (definitive) status = "Pendiente de acreditación";
  else if (closing2 && settledScore !== null) status = "A período Mar";
  else if (closing1 && settledScore !== null) status = "A período Feb";
  else if (secondFinal && settledScore !== null && settledScore <= 6) status = "A período Dic";
  else if (closing3 && settledScore !== null) status = "Pendiente de calificación definitiva";

  return { currentValue, status };
}

export default function HistorialPage() {
  const [notas, setNotas] = useState<Nota[]>([]);
  const [materias, setMaterias] = useState<Bloque[]>([]);
  const [cambios, setCambios] = useState<Auditoria[]>([]);
  const [err, setErr] = useState("");
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    Promise.all([api.historialAlumno(), api.cambiosAlumno(), api.materiasAlumno()])
      .then(([n, c, m]) => { setNotas(n); setCambios(c); setMaterias(m); })
      .catch((e) => setErr(e.message))
      .finally(() => setLoading(false));
  }, []);

  const rows = useMemo(() => {
    const grouped = new Map<string, { materia: string; fases: Partial<Record<FasePlanilla, Nota>> }>();
    for (const bloque of materias) {
      const materia = bloque.materia || "Materia";
      grouped.set(materia.trim().toLocaleLowerCase("es-AR"), { materia, fases: {} });
    }
    for (const nota of notas) {
      if (nota.borrador === true) continue;
      const fase = faseDeNota(nota);
      if (!fase) continue;
      const materia = nota.materia || "Materia";
      const key = materia.trim().toLocaleLowerCase("es-AR");
      const row = grouped.get(key) || { materia, fases: {} };
      const current = row.fases[fase];
      if (!current || (nota.fecMod || nota.fecCarga || "") >= (current.fecMod || current.fecCarga || "")) row.fases[fase] = nota;
      grouped.set(key, row);
    }
    return [...grouped.values()].sort((a, b) => a.materia.localeCompare(b.materia, "es"));
  }, [notas, materias]);

  return (
    <Guard roles={["ALUMNO"]}>
      <Shell>
        <h1>Seguimiento académico</h1>
        <p className="card-meta">Avance anual por materia y registro de rectificaciones oficiales.</p>
        {err && <div className="alert alert-error">{err}</div>}

        <section className="panel academic-tracking-panel">
          <div className="section-heading">
            <div>
              <h2>Seguimiento anual</h2>
              <p className="card-meta">Las calificaciones aparecen cuando fueron entregadas oficialmente.</p>
            </div>
            <span className="tracking-count">{rows.length} {rows.length === 1 ? "materia" : "materias"}</span>
          </div>
          <div className="table-wrap academic-tracking-wrap">
            <table className="data academic-tracking" aria-label="Seguimiento académico anual por materia">
              <thead>
                <tr>
                  <th rowSpan={2}>Materia</th>
                  <th colSpan={3}>1° Cuatrimestre</th>
                  <th colSpan={3}>2° Cuatrimestre</th>
                  <th colSpan={5}>Cierres y situación</th>
                </tr>
                <tr>
                  <th>1° Bim.</th><th>2° Bim.</th><th>Nota cuatr.</th>
                  <th>3° Bim.</th><th>4° Bim.</th><th>Nota cuatr.</th>
                  <th>Dic.</th><th>Feb.</th><th>Mar.</th><th>Promedio / actual</th><th>Estado</th>
                </tr>
              </thead>
              <tbody>
                {rows.map(row => {
                  const n = row.fases;
                  const { currentValue, status } = currentAndStatus(n);
                  return <tr key={row.materia}>
                    <th scope="row" className="tracking-subject">{row.materia}</th>
                    <td className={n.BIM_1 ? "numeric" : "numeric tracking-empty"}>{notaMostrada(n.BIM_1)}</td>
                    <td className={n.BIM_2 ? "numeric" : "numeric tracking-empty"}>{notaMostrada(n.BIM_2)}</td>
                    <td className={n.FINAL_1 ? "numeric tracking-term" : "numeric tracking-empty"}>{notaMostrada(n.FINAL_1)}</td>
                    <td className={n.BIM_3 ? "numeric" : "numeric tracking-empty"}>{notaMostrada(n.BIM_3)}</td>
                    <td className={n.BIM_4 ? "numeric" : "numeric tracking-empty"}>{notaMostrada(n.BIM_4)}</td>
                    <td className={n.FINAL_2 ? "numeric tracking-term" : "numeric tracking-empty"}>{notaMostrada(n.FINAL_2)}</td>
                    <td className={n.CIERRE_1 ? "numeric" : "numeric tracking-empty"}>{notaMostrada(n.CIERRE_1)}</td>
                    <td className={n.CIERRE_2 ? "numeric" : "numeric tracking-empty"}>{notaMostrada(n.CIERRE_2)}</td>
                    <td className={n.CIERRE_3 ? "numeric" : "numeric tracking-empty"}>{notaMostrada(n.CIERRE_3)}</td>
                    <td className="numeric tracking-current">{n.CALIF_DEF ? notaMostrada(n.CALIF_DEF) : currentValue}</td>
                    <td><span className={`tracking-status ${status === "Aprobada" ? "is-approved" : status.startsWith("A período") ? "is-pending" : "is-progress"}`}>{status}</span></td>
                  </tr>;
                })}
                {!loading && rows.length === 0 && <tr><td colSpan={12} className="empty">Todavía no hay calificaciones oficiales cargadas.</td></tr>}
                {loading && <tr><td colSpan={12} className="empty">Cargando seguimiento…</td></tr>}
              </tbody>
            </table>
          </div>
          <p className="tracking-footnote">Los guiones indican períodos sin una nota oficial cargada. El boletín oficial se consulta por separado cuando Oficina de Alumnos lo publica.</p>
        </section>

        <section className="panel academic-changes-panel">
          <div className="section-heading">
            <div><h2>Modificaciones de notas</h2><p className="card-meta">Rectificaciones aprobadas por Oficina de Alumnos.</p></div>
          </div>
          {cambios.length > 0 ? <div className="table-wrap"><table className="data academic-changes">
            <thead><tr><th>Fecha</th><th>Materia y período</th><th>Nota</th><th>Motivo</th><th>Autorizó</th></tr></thead>
            <tbody>{cambios.map(c => <tr key={c.id}>
              <td className="change-date">{dateLabel(c.fec)}</td>
              <td><strong>{c.materia}</strong><span className="change-period">{c.periodo || "Período no disponible"}</span></td>
              <td className="change-values"><span>{c.valAnt || GUION}</span><span className="change-arrow" aria-label="cambió a">➔</span><strong>{c.valNuevo || GUION}</strong></td>
              <td>{c.motivo || GUION}</td><td>{c.autorizo || "Oficina de Alumnos"}</td>
            </tr>)}</tbody>
          </table></div> : <p className="empty">Todavía no hay rectificaciones oficiales en tu historial.</p>}
        </section>
      </Shell>
    </Guard>
  );
}

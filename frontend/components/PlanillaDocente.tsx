"use client";

import { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import { useParams } from "next/navigation";
import Shell from "@/components/Shell";
import { Guard } from "@/components/Guard";
import Toast from "@/components/Toast";
import { api } from "@/lib/api";
import { cursoLabel } from "@/lib/presentation";
import type { Bloque, FasePlanilla, PlanillaCompleta, PlanillaFase } from "@/lib/types";

const COLUMNAS: { fase: FasePlanilla; titulo: string }[] = [
  { fase: "BIM_1", titulo: "1° Bimestre" }, { fase: "BIM_2", titulo: "2° Bimestre" },
  { fase: "FINAL_1", titulo: "FINAL" }, { fase: "BIM_3", titulo: "3° Bimestre" },
  { fase: "BIM_4", titulo: "4° Bimestre" }, { fase: "FINAL_2", titulo: "FINAL" },
  { fase: "CIERRE_1", titulo: "1° Cierre" }, { fase: "CIERRE_2", titulo: "2° Cierre" },
  { fase: "CIERRE_3", titulo: "3° Cierre" }, { fase: "CALIF_DEF", titulo: "Calif. Def." },
];
type GradeMap = Record<string, Record<string, string>>;
const ESTADO: Record<string, string> = { ABIERTA: "Abierta", CERRADA: "Entregada", PROGRAMADA: "Próxima", VENCIDA: "Vencida", SIN_FECHAS: "Sin fechas" };
function csvCell(v: string) { return `"${v.replaceAll('"', '""')}"`; }

export default function PlanillaDocente() {
  const { id } = useParams<{ id: string }>();
  const bloqueId = Number(id);
  const [bloque, setBloque] = useState<Bloque | null>(null);
  const [sheet, setSheet] = useState<PlanillaCompleta | null>(null);
  const [data, setData] = useState<GradeMap>({});
  const [err, setErr] = useState("");
  const [ok, setOk] = useState("");
  const [saving, setSaving] = useState("");
  const [confirmPhase, setConfirmPhase] = useState<FasePlanilla | null>(null);

  const load = useCallback(async () => {
    if (!bloqueId) return;
    setErr("");
    try {
      const blocks = await api.bloquesProf();
      const current = blocks.find(b => b.id === bloqueId) || null;
      if (!current) throw new Error("No se encontró la materia asignada a tu usuario");
      const complete = await api.planillaCompleta(bloqueId);
      setBloque(current);
      setSheet(complete);
      setData(Object.fromEntries(COLUMNAS.map(c => [c.fase,
        Object.fromEntries(complete.alumnos.map(a => [a.legajo, gradeInputValue(a.notas[c.fase])]))])));
    } catch (e) { setErr(e instanceof Error ? e.message : "No se pudo cargar la planilla"); }
  }, [bloqueId]);

  useEffect(() => { load(); }, [load]);

  function setGrade(fase: FasePlanilla, legajo: string, val: string) {
    setData(prev => ({ ...prev, [fase]: { ...prev[fase], [legajo]: val } }));
  }

  function gradeInputValue(value: string | undefined) {
    if (!value) return "";
    const normalized = value.trim().toUpperCase();
    if (normalized === "EN PROCESO") return "EP";
    const number = Number(normalized);
    return Number.isFinite(number) ? String(Math.round(number)) : normalized;
  }

  function faseInfo(fase: FasePlanilla): PlanillaFase | undefined {
    const info = sheet?.fases.find(f => f.fase === fase);
    if (!info || info.estado === "CERRADA") return info;
    const referencia = fase === "FINAL_1" ? "BIM_2" : fase === "FINAL_2" ? "BIM_4" : null;
    if (!referencia) return info;
    const periodo = sheet?.fases.find(f => f.fase === referencia);
    // The date window is shared, but closing the bimestre must not close the FINAL column.
    return periodo ? { ...info, desde: info.desde || periodo.desde, hasta: info.hasta || periodo.hasta } : info;
  }

  function failed(value: string | undefined) {
    if (!value) return false;
    const normalized = value.trim().toUpperCase();
    if (normalized === "EP" || normalized === "EN PROCESO") return true;
    const grade = Number(normalized);
    return Number.isFinite(grade) && grade <= 6;
  }

  function passed(value: string | undefined) {
    if (!value) return false;
    const grade = Number(value.trim());
    return Number.isFinite(grade) && grade > 6;
  }

  function eligible(fase: FasePlanilla, legajo: string) {
    if (faseInfo(fase)?.estado !== "ABIERTA") return false;
    switch (fase) {
      case "CIERRE_1": return faseInfo("FINAL_2")?.estado === "CERRADA" && failed(data.FINAL_2?.[legajo]);
      case "CIERRE_2": return faseInfo("CIERRE_1")?.estado === "CERRADA" && failed(data.CIERRE_1?.[legajo]);
      case "CIERRE_3": return faseInfo("CIERRE_2")?.estado === "CERRADA" && failed(data.CIERRE_2?.[legajo]);
      case "CALIF_DEF": return (faseInfo("FINAL_2")?.estado === "CERRADA" && passed(data.FINAL_2?.[legajo]))
        || (faseInfo("CIERRE_1")?.estado === "CERRADA" && passed(data.CIERRE_1?.[legajo]))
        || (faseInfo("CIERRE_2")?.estado === "CERRADA" && passed(data.CIERRE_2?.[legajo]))
        || (faseInfo("CIERRE_3")?.estado === "CERRADA" && passed(data.CIERRE_3?.[legajo]));
      default: return true;
    }
  }

  function hasEligible(fase: FasePlanilla) {
    return !!sheet?.alumnos.some(a => eligible(fase, a.legajo));
  }

  function incompleteStudents(fase: FasePlanilla) {
    return sheet?.alumnos.filter(a => eligible(fase, a.legajo)
      && !gradeInputValue(data[fase]?.[a.legajo])) ?? [];
  }

  function requestClose(fase: FasePlanilla) {
    const faltantes = incompleteStudents(fase);
    if (faltantes.length) {
      const nombres = faltantes.slice(0, 3).map(a => a.alumno).join(", ");
      const adicionales = faltantes.length > 3 ? ` y ${faltantes.length - 3} más` : "";
      setErr(`Completá la nota de los ${faltantes.length} alumno(s) habilitado(s) antes de entregar: ${nombres}${adicionales}.`);
      return;
    }
    setErr("");
    setConfirmPhase(fase);
  }

  async function persist(fase: FasePlanilla, cerrar: boolean) {
    if (!sheet || saving) return;
    if (cerrar && incompleteStudents(fase).length) {
      setErr("Completá la nota de todos los alumnos habilitados antes de entregar esta fase.");
      return;
    }
    setSaving(fase); setErr(""); setOk("");
    try {
      const body = { bloqueId, fase, notas: sheet.alumnos.map(a => ({
        legajo: a.legajo, val: data[fase]?.[a.legajo]?.trim() || null,
      })) };
      if (cerrar) {
        await api.cerrarPlanilla(body);
        setOk(`Fase «${faseInfo(fase)?.etiqueta}» entregada y cerrada`);
      } else {
        await api.guardarPlanilla(body);
        setOk(`Borrador de «${faseInfo(fase)?.etiqueta}» guardado`);
      }
      await load();
    } catch (e) { setErr(e instanceof Error ? e.message : "No se pudo guardar la planilla"); }
    finally { setSaving(""); }
  }

  async function saveOpenDrafts() {
    if (!sheet || saving) return;
    const abiertas = sheet.fases.filter(f => faseInfo(f.fase)?.estado === "ABIERTA" && hasEligible(f.fase));
    if (!abiertas.length) { setErr("No hay fases habilitadas para guardar."); return; }
    setSaving("todas"); setErr(""); setOk("");
    try {
      await api.guardarBorradores(abiertas.map(f => ({
        bloqueId, fase: f.fase, notas: sheet.alumnos.map(a => ({
          legajo: a.legajo, val: data[f.fase]?.[a.legajo]?.trim() || null,
        }))
      })));
      setOk("Borradores de todas las fases habilitadas guardados");
      await load();
    } catch (e) { setErr(e instanceof Error ? e.message : "No se pudieron guardar los borradores"); }
    finally { setSaving(""); }
  }

  function download() {
    if (!sheet) return;
    const headers = ["N°", "ALUMNO/A", ...COLUMNAS.map(c => c.titulo)];
    const rows = sheet.alumnos.map((a, i) => [String(i + 1), a.alumno,
      ...COLUMNAS.map(c => gradeInputValue(data[c.fase]?.[a.legajo]))]);
    const content = "\uFEFF" + [["INFORME ANUAL", String(sheet.anio)], ["CURSO", sheet.curso, "MATERIA", sheet.materia], headers, ...rows]
      .map(row => row.map(csvCell).join(";")).join("\r\n");
    const url = URL.createObjectURL(new Blob([content], { type: "text/csv;charset=utf-8" }));
    const link = document.createElement("a"); link.href = url;
    link.download = `boletin-${sheet.curso}-${sheet.anio}.csv`; link.click(); URL.revokeObjectURL(url);
  }

  return <Guard roles={["PROFESOR"]}><Shell className="grade-page">
    <div className="sheet-head">
      <div className="sheet-ident">
        <p className="card-meta no-print"><Link href="/profesor">← Volver a materias</Link></p>
        <p className="card-meta sheet-course-meta">MATERIA: <strong>{sheet?.materia}</strong> · CURSO: <strong>{cursoLabel(sheet?.curso || "")}</strong></p>
      </div>
      <div className="btn-row sheet-actions no-print">
        <button className="btn" onClick={download} disabled={!sheet?.alumnos.length}>Descargar CSV</button>
        <button className="btn btn-primary" disabled={!!saving || !sheet?.fases.some(f => faseInfo(f.fase)?.estado === "ABIERTA" && hasEligible(f.fase))} onClick={saveOpenDrafts}>
          {saving === "todas" ? "Guardando…" : "Guardar borradores abiertos"}
        </button>
      </div>
    </div>
    {err && <div className="alert alert-error">{err}</div>}
    {ok && <Toast message={ok} onClose={() => setOk("")} />}
    <p className="card-meta no-print">En las fases habilitadas seleccioná una nota entera del 1 al 10, EP, NI o NC. Las fases abiertas se indican en verde; las entregadas no se pueden editar.</p>
    <div className="table-wrap grade-sheet-wrap">
      <table className="data grade-sheet">
        <thead>
          <tr><th>#</th><th>ALUMNO/A</th>{COLUMNAS.map(c => {
            const fase = faseInfo(c.fase);
            const abierta = fase?.estado === "ABIERTA";
            const vencida = fase?.estado === "VENCIDA";
            return <th key={c.fase} className={`${abierta ? "selected-phase" : ""} ${vencida ? "phase-expired" : ""}`}>
              <div>{c.titulo}</div>
              {fase?.referencia && <small className="phase-month">{fase.referencia}</small>}
              <small className="phase-date">{fase?.desde && fase?.hasta ? `Cargar y entregar hasta ${fase.hasta}` : "Sin fechas: Oficina debe definir el plazo"}</small>
              <small className={`phase-state phase-${fase?.estado?.toLowerCase() || "cargando"}`}>{ESTADO[fase?.estado || ""] || "Cargando"}</small>
              {abierta && hasEligible(c.fase) && <button className="phase-close no-print" disabled={!!saving} onClick={() => requestClose(c.fase)}>{saving === c.fase ? "…" : "Entregar fase"}</button>}
            </th>;
          })}</tr>
        </thead>
        <tbody>{sheet?.alumnos.map((a, i) => <tr key={a.legajo}>
          <td>{i + 1}</td>
          <td><Link href={`/profesor/bloque/${id}/alumno/${a.legajo}`} className="student-history-link">{a.alumno}</Link><small className="student-legajo">Legajo {a.legajo}</small></td>
          {COLUMNAS.map(c => <td key={c.fase} className={eligible(c.fase, a.legajo) ? "selected-phase" : ""}>
            {eligible(c.fase, a.legajo) ? <select className="grade-input grade-select" aria-label={`${a.alumno}: ${c.titulo}`} title="Seleccioná una nota entera, EP, NI o NC" value={gradeInputValue(data[c.fase]?.[a.legajo])} onChange={e => setGrade(c.fase, a.legajo, e.target.value)}>
              <option value="">—</option>{Array.from({ length: 10 }, (_, n) => <option key={n + 1} value={String(n + 1)}>{n + 1}</option>)}
              <option value="EP">EP</option><option value="NI">NI</option><option value="NC">NC</option>
            </select> : gradeInputValue(data[c.fase]?.[a.legajo]) || "—"}
          </td>)}
        </tr>)}
        {!sheet?.alumnos.length && <tr><td colSpan={12} className="empty">{err ? "" : "No hay alumnos inscriptos en este curso."}</td></tr>}</tbody>
      </table>
    </div>
    <p className="card-meta no-print">Las fases entregadas quedan bloqueadas. Para modificar una nota, solicitá el cambio desde el historial del alumno.</p>
    <section className="sheet-foot grade-legend" aria-label="Guía de valoraciones">
      <strong>VALORACIONES BIMESTRALES RESPECTO DE LOS OBJETIVOS</strong>
      <div className="grade-legend-grid">
        <p><b>EP — EN PROCESO:</b> El estudiante está empezando a desarrollar los aprendizajes previstos o evidencia algunas dificultades y necesita mayor tiempo de acompañamiento e intervención.</p>
        <p><b>NC — NO CORRESPONDE:</b> El estudiante se encuentra en una situación pedagógica o administrativa por la cual no corresponde calificar.</p>
        <p><b>NI — NO INICIÓ:</b> El estudiante no inició su trayectoria.</p>
        <p><b>6–10 — FINALIZADO:</b> El estudiante alcanzó su trayectoria escolar evidenciando el logro de los aprendizajes.</p>
      </div>
    </section>
    {confirmPhase && <div className="confirm-backdrop no-print" role="presentation">
      <div className="confirm-dialog" role="dialog" aria-modal="true" aria-labelledby="confirm-phase-title">
        <h2 id="confirm-phase-title">Confirmar entrega de fase</h2>
        <p>¿Querés entregar «{faseInfo(confirmPhase)?.etiqueta}»? La fase se bloqueará y las notas no podrán modificarse sin solicitar un cambio a Oficina de Alumnos.</p>
        <div className="btn-row">
          <button className="btn" onClick={() => setConfirmPhase(null)}>Volver a la planilla</button>
          <button className="btn btn-primary" disabled={!!saving} onClick={() => {
            const fase = confirmPhase;
            setConfirmPhase(null);
            void persist(fase, true);
          }}>{saving === confirmPhase ? "Entregando…" : "Sí, entregar fase"}</button>
        </div>
      </div>
    </div>}
  </Shell></Guard>;
}

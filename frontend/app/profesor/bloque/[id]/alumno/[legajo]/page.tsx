"use client";

import { FormEvent, useEffect, useState } from "react";
import Toast from "@/components/Toast";
import { cursoLabel, dateLabel } from "@/lib/presentation";
import Link from "next/link";
import { useParams } from "next/navigation";
import Shell from "@/components/Shell";
import { Guard } from "@/components/Guard";
import { api } from "@/lib/api";
import type { Alumno, Bloque, Nota } from "@/lib/types";

const ETAPAS = [
  { fase: "BIM_1", titulo: "1° Bimestre" },
  { fase: "BIM_2", titulo: "2° Bimestre" },
  { fase: "FINAL_1", titulo: "FINAL · 1° Cuatrimestre" },
  { fase: "BIM_3", titulo: "3° Bimestre" },
  { fase: "BIM_4", titulo: "4° Bimestre" },
  { fase: "FINAL_2", titulo: "FINAL · 2° Cuatrimestre" },
  { fase: "CIERRE_1", titulo: "1° Cierre · Diciembre" },
  { fase: "CIERRE_2", titulo: "2° Cierre · Febrero" },
  { fase: "CIERRE_3", titulo: "3° Cierre · Marzo" },
  { fase: "CALIF_DEF", titulo: "Calif. Def." },
];

function faseNota(nota: Nota) {
  if (nota.tipo === "BIM" && nota.numPer >= 1 && nota.numPer <= 4) return `BIM_${nota.numPer}`;
  if ((nota.tipo === "FINAL" || nota.tipo === "CUAT") && nota.numPer === 1) return "FINAL_1";
  if ((nota.tipo === "FINAL" || nota.tipo === "CUAT") && nota.numPer === 2) return "FINAL_2";
  if (nota.tipo === "FINAL" && nota.numPer === 3) return "CALIF_DEF";
  if (nota.tipo === "DIC") return "CIERRE_1";
  if (nota.tipo === "FEB" && nota.numPer === 1) return "CIERRE_2";
  if (nota.tipo === "FEB" && nota.numPer === 2) return "CIERRE_3";
  return null;
}

export default function AlumnoNotasPage() {
  const { id, legajo } = useParams<{ id: string; legajo: string }>();
  const bloqueId = Number(id);
  const [notas, setNotas] = useState<Nota[]>([]);
  const [fasesEntregadas, setFasesEntregadas] = useState<Set<string>>(new Set());
  const [alumno, setAlumno] = useState<Alumno | null>(null);
  const [bloque, setBloque] = useState<Bloque | null>(null);
  const [err, setErr] = useState("");
  const [ok, setOk] = useState("");
  const [modNota, setModNota] = useState<Nota | null>(null);
  const [modVal, setModVal] = useState("");
  const [modMot, setModMot] = useState("");

  function load() {
    api.notas(bloqueId, legajo).then(setNotas).catch((e) => setErr(e.message));
    Promise.all([api.alumnos(bloqueId), api.bloquesProf()])
      .then(([alumnos, bloques]) => {
        setAlumno(alumnos.find(a => a.legajo === legajo) || null);
        setBloque(bloques.find(b => b.id === bloqueId) || null);
      })
      .catch((e) => setErr(e.message));
    api.planillaCompleta(bloqueId)
      .then(planilla => setFasesEntregadas(new Set(planilla.fases.filter(f => f.estado === "CERRADA").map(f => f.fase))))
      .catch(() => setFasesEntregadas(new Set()));
  }

  useEffect(() => {
    load();
  }, [bloqueId, legajo]);

  async function solicitar(e: FormEvent) {
    e.preventDefault();
    if (!modNota) return;
    setErr("");
    if (!modMot.trim()) {
      setErr("El motivo de la solicitud es obligatorio");
      return;
    }
    try {
      await api.solicitar({
        notaId: modNota.id,
        motivo: modMot,
        valNuevo: modVal,
      });
      setOk("Solicitud enviada a oficina de alumnos");
      setModNota(null);
    } catch (ex) {
      setErr(ex instanceof Error ? ex.message : "Error");
    }
  }

  // Never show drafts. For older API responses without the flag, only show
  // phases that the planilla API confirms were delivered.
  const notasPlanilla = notas.filter(n => {
    const fase = faseNota(n);
    return fase !== null && n.borrador !== true && (n.borrador === false || fasesEntregadas.has(fase));
  })
    .sort((a, b) => ETAPAS.findIndex(f => f.fase === faseNota(a)) - ETAPAS.findIndex(f => f.fase === faseNota(b))
      || (a.fecCarga || "").localeCompare(b.fecCarga || ""));
  const ultimaFase = Math.max(-1, ...notasPlanilla.map(n => ETAPAS.findIndex(f => f.fase === faseNota(n))));
  function etapa(nota: Nota) {
    return ETAPAS.find(f => f.fase === faseNota(nota))?.titulo || "";
  }
  function habilitaSolicitud(nota: Nota) {
    const valorValido = /^\d+$/.test(nota.val) || ["EP", "NI", "NC"].includes(nota.val);
    return ETAPAS.findIndex(f => f.fase === faseNota(nota)) === ultimaFase && nota.borrador === false && valorValido;
  }

  return (
    <Guard roles={["PROFESOR"]}>
      <Shell>
        <p className="card-meta">
          <Link href={`/profesor/bloque/${id}`}>← Volver a alumnos</Link>
        </p>
        <h1>Notas de {alumno?.nomCompleto || `alumno ${legajo}`}</h1>
        <p className="card-meta">{bloque ? <>{bloque.materia} · {cursoLabel(bloque.curso)}</> : `Legajo ${legajo}`}</p>
        <p className="card-meta">Se muestran las notas de la planilla. Solo la fase más reciente entregada permite solicitar una modificación.</p>
        {err && <div className="alert alert-error">{err}</div>}
        {ok && <Toast message={ok} onClose={() => setOk("")}/>}

        <div className="table-wrap">
          <table className="data">
            <thead>
              <tr>
                <th>Etapa</th>
                <th>Nota</th>
                <th>Fecha de carga</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              {notasPlanilla.map((n) => (
                <tr key={n.id}>
                  <td>{etapa(n)}</td>
                  <td>
                    {n.est === "EN_PROCESO" ? (
                      <span className="badge badge-warn">{n.val === "EP" ? "EP · En proceso" : n.val}</span>
                    ) : (
                      n.val
                    )}
                  </td>
                  <td>{dateLabel(n.fecCarga)}</td>
                  <td>
                    {habilitaSolicitud(n) && <button type="button" className="btn btn-sm" onClick={() => {
                      setModNota(n); setModVal(""); setModMot("");
                    }}>Solicitar modificación</button>}
                  </td>
                </tr>
              ))}
              {notasPlanilla.length === 0 && (
                <tr>
                  <td colSpan={4} className="empty">
                    Todavía no hay notas de la planilla cargadas
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>

        {modNota && (
          <div className="panel">
            <h2>Solicitar cambio — {etapa(modNota)} · nota actual: {modNota.val}</h2>
            <form className="form" onSubmit={solicitar}>
              <div className="field">
                <label htmlFor="field-5">Nueva nota</label>
                <select id="field-5" value={modVal} onChange={(e) => setModVal(e.target.value)} required>
                  <option value="">Seleccioná una nota</option>
                  {Array.from({ length: 10 }, (_, i) => <option key={i + 1} value={String(i + 1)}>{i + 1}</option>)}
                  <option value="EP">EP · En proceso</option><option value="NI">NI · No inició</option><option value="NC">NC · No corresponde</option>
                </select>
              </div>
              <div className="field">
                <label htmlFor="field-6">Motivo de la solicitud <span aria-hidden="true">*</span></label>
                <textarea id="field-6"
                  value={modMot}
                  onChange={(e) => setModMot(e.target.value)}
                  required
                />
              </div>
              <div className="btn-row">
                <button type="submit" className="btn btn-primary">
                  Enviar a oficina
                </button>
                <button
                  type="button"
                  className="btn"
                  onClick={() => setModNota(null)}
                >
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

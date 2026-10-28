"use client";

import { useEffect, useState } from "react";
import { label, cursoLabel } from "@/lib/presentation";
import Link from "next/link";
import { useParams } from "next/navigation";
import Shell from "@/components/Shell";
import { Guard } from "@/components/Guard";
import { api } from "@/lib/api";
import type { Alumno, Bloque, BoletinPublicacion, Nota, PlanillaAlumno, PlanillaCompleta } from "@/lib/types";

export default function OficinaBloquePage() {
  const { id } = useParams<{ id: string }>();
  const bloqueId = Number(id);
  const [alumnos, setAlumnos] = useState<Alumno[]>([]);
  const [bloque, setBloque] = useState<Bloque | null>(null);
  const [planilla, setPlanilla] = useState<PlanillaCompleta | null>(null);
  const [sel, setSel] = useState<string>("");
  const [notas, setNotas] = useState<Nota[]>([]);
  const [err, setErr] = useState("");
  const [publicacion, setPublicacion] = useState<BoletinPublicacion | null>(null);
  const [savingPublicacion, setSavingPublicacion] = useState(false);

  useEffect(() => {
    api.bloquesVer().then(bloques => setBloque(bloques.find(b => b.id === bloqueId) || null))
      .catch((e) => setErr(e.message));
    api.alumnos(bloqueId).then(setAlumnos).catch((e) => setErr(e.message));
    api.planillaCompleta(bloqueId).then(setPlanilla).catch((e) => setErr(e.message));
    api.publicacionBoletinCurso(bloqueId).then(setPublicacion).catch((e) => setErr(e.message));
  }, [bloqueId]);

  async function cambiarPublicacion() {
    const nuevoEstado = !publicacion?.publicado;
    if (nuevoEstado && !window.confirm("¿Publicar el boletín de todo este curso? Los alumnos podrán leer y descargar la versión oficial.")) return;
    if (!nuevoEstado && !window.confirm("¿Retirar la publicación? Los alumnos dejarán de ver el boletín.")) return;
    setSavingPublicacion(true); setErr("");
    try { setPublicacion(await api.publicarBoletinCurso(bloqueId, nuevoEstado)); }
    catch (e) { setErr(e instanceof Error ? e.message : "No se pudo actualizar la publicación"); }
    finally { setSavingPublicacion(false); }
  }

  useEffect(() => {
    if (!sel) {
      setNotas([]);
      return;
    }
    api.notas(bloqueId, sel).then(setNotas).catch((e) => setErr(e.message));
  }, [bloqueId, sel]);

  async function descargarCurso() {
    setErr("");
    try {
      const nombre = (bloque?.curso || planilla?.curso || `curso-${bloqueId}`).replace(/[^a-zA-Z0-9-]+/g, "-");
      await api.downloadBoletinesCursoPdf(bloqueId, `boletines-${nombre}-${bloque?.anio || planilla?.anio || ""}.pdf`);
    } catch (e) {
      setErr(e instanceof Error ? e.message : "No se pudo descargar el boletín del curso");
    }
  }

  async function descargarAlumno(alumno: Alumno) {
    setErr("");
    try {
      const nombre = alumno.nomCompleto.replace(/[^a-zA-Z0-9-]+/g, "-");
      await api.downloadBoletinCursoAlumno(bloqueId, alumno.legajo, `boletin-${nombre}.pdf`);
    } catch (e) {
      setErr(e instanceof Error ? e.message : "No se pudo descargar el boletín del alumno");
    }
  }

  const filaPorLegajo = new Map<string, PlanillaAlumno>(planilla?.alumnos.map(f => [f.legajo, f] as const) ?? []);
  function claseFase(fase: string) {
    return fase === "CALIF_DEF" ? "office-phase-def" : fase.startsWith("FINAL") ? "office-phase-wide" : "";
  }

  return (
    <Guard roles={["OFICINA", "ADMIN"]}>
      <Shell className="office-grades-page">
        <p className="card-meta"><Link href="/oficina/notas">← Volver a cursos</Link></p>
        <h1>{bloque ? `Notas · ${cursoLabel(bloque.curso)}` : planilla ? `Notas · ${cursoLabel(planilla.curso)}` : "Notas del curso"}</h1>
        {(bloque || planilla) && <p className="card-meta">Materia: {bloque?.materia || planilla?.materia}</p>}
        {err && <div className="alert alert-error">{err}</div>}

        <section className="panel" style={{ marginBottom: "1rem" }}>
          <div className="btn-row" style={{ justifyContent: "space-between", alignItems: "center" }}>
            <div>
              <h2 style={{ margin: 0 }}>Boletín del curso</h2>
              <p className="card-meta">{publicacion?.publicado ? `Publicado${publicacion.publicadoEn ? ` el ${publicacion.publicadoEn.slice(0, 10)}` : ""}. Los alumnos pueden leerlo.` : "Aún no publicado. Los alumnos no pueden acceder al PDF."}</p>
            </div>
            <button type="button" className={`btn ${publicacion?.publicado ? "" : "btn-primary"}`} disabled={!publicacion || savingPublicacion} onClick={cambiarPublicacion}>
              {savingPublicacion ? "Guardando…" : publicacion?.publicado ? "Retirar publicación" : "Publicar boletín para alumnos"}
            </button>
          </div>
        </section>

        <div className="btn-row" style={{ marginBottom: "1rem" }}>
          <button type="button" className="btn btn-primary" onClick={descargarCurso} disabled={!planilla}>
            Descargar boletín del curso (PDF)
          </button>
        </div>

        {planilla && (
          <div className="table-wrap">
            <table className="data office-grades">
              <thead>
                <tr>
                  <th>#</th>
                  <th>Alumno/a</th>
                  {planilla.fases.map(f => <th key={f.fase} className={claseFase(f.fase)}>{f.etiqueta}</th>)}
                  <th>Boletín individual</th>
                  <th></th>
                </tr>
              </thead>
              <tbody>
                {alumnos.map((alumno, index) => {
                  const fila = filaPorLegajo.get(alumno.legajo);
                  return (
                    <tr key={alumno.legajo}>
                      <td>{index + 1}</td>
                  <td className="office-student-name" title={alumno.nomCompleto}>{alumno.nomCompleto}</td>
                      {planilla.fases.map(f => {
                        const valor = fila?.notas[f.fase]?.trim();
                        return <td key={f.fase} className={`numeric office-grade-cell ${claseFase(f.fase)}`}>{valor || "–"}</td>;
                      })}
                      <td>
                        <button type="button" className="btn btn-sm" onClick={() => descargarAlumno(alumno)}>
                          Descargar PDF
                        </button>
                      </td>
                      <td>
                        <button type="button" className="btn btn-sm" onClick={() => setSel(alumno.legajo)}>
                          Ver detalle
                        </button>
                      </td>
                    </tr>
                  );
                })}
                {alumnos.length === 0 && <tr><td colSpan={planilla.fases.length + 5} className="empty">No hay alumnos inscriptos en este curso.</td></tr>}
              </tbody>
            </table>
          </div>
        )}

        {sel && (
          <section className="panel" style={{ marginTop: "1.25rem" }}>
            <div className="btn-row" style={{ justifyContent: "space-between" }}>
              <h2>{alumnos.find(a => a.legajo === sel)?.nomCompleto || "Detalle del alumno"}</h2>
              <button type="button" className="btn btn-sm" onClick={() => setSel("")}>Cerrar detalle</button>
            </div>
            <div className="table-wrap">
              <table className="data">
                <thead><tr><th>Tipo</th><th>Período</th><th>Detalle</th><th>Nota</th></tr></thead>
                <tbody>
                  {notas.map(n => (
                    <tr key={n.id}>
                      <td>{label(n.tipo)}</td><td className="numeric">{n.numPer}</td><td>{n.titulo || "—"}</td><td className="numeric">{n.val}</td>
                    </tr>
                  ))}
                  {notas.length === 0 && <tr><td colSpan={4} className="empty">No hay notas entregadas para este alumno.</td></tr>}
                </tbody>
              </table>
            </div>
          </section>
        )}
      </Shell>
    </Guard>
  );
}

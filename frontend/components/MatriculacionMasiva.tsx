"use client";

import { useEffect, useRef, useState } from "react";
import Toast from "@/components/Toast";
import { api } from "@/lib/api";
import { cursoLabel, label } from "@/lib/presentation";
import type { Curso, PaginaAlumnos } from "@/lib/types";

export default function MatriculacionMasiva({ cursos, anioInicial }: { cursos: Curso[]; anioInicial: number }) {
  const [anio, setAnio] = useState(anioInicial);
  const [cursoId, setCursoId] = useState(cursos[0] ? String(cursos[0].id) : "");
  const [qDisponibles, setQDisponibles] = useState("");
  const [qInscriptos, setQInscriptos] = useState("");
  const [debouncedDisponibles, setDebouncedDisponibles] = useState("");
  const [debouncedInscriptos, setDebouncedInscriptos] = useState("");
  const [pageDisponibles, setPageDisponibles] = useState(0);
  const [pageInscriptos, setPageInscriptos] = useState(0);
  const [size, setSize] = useState(25);
  const [disponibles, setDisponibles] = useState<PaginaAlumnos | null>(null);
  const [inscriptos, setInscriptos] = useState<PaginaAlumnos | null>(null);
  const [selectedDisponibles, setSelectedDisponibles] = useState<string[]>([]);
  const [selectedInscriptos, setSelectedInscriptos] = useState<string[]>([]);
  const [loading, setLoading] = useState(false);
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState("");
  const [ok, setOk] = useState("");
  const requestId = useRef(0);
  const selectedCourse = cursos.find(c => String(c.id) === cursoId);

  useEffect(() => { const t = window.setTimeout(() => { setDebouncedDisponibles(qDisponibles.trim()); setPageDisponibles(0); }, 300); return () => window.clearTimeout(t); }, [qDisponibles]);
  useEffect(() => { const t = window.setTimeout(() => { setDebouncedInscriptos(qInscriptos.trim()); setPageInscriptos(0); }, 300); return () => window.clearTimeout(t); }, [qInscriptos]);

  async function load() {
    const currentRequest = ++requestId.current;
    if (!cursoId) { setDisponibles(null); setInscriptos(null); return; }
    setLoading(true); setErr("");
    const base = { anio: String(anio), cursoId, size: String(size) };
    try {
      const [free, current] = await Promise.all([
        api.matriculacionAlumnos(new URLSearchParams({ ...base, q: debouncedDisponibles, disponibles: "true", page: String(pageDisponibles) })),
        api.matriculacionAlumnos(new URLSearchParams({ ...base, q: debouncedInscriptos, disponibles: "false", page: String(pageInscriptos) })),
      ]);
      if (currentRequest === requestId.current) { setDisponibles(free); setInscriptos(current); }
    } catch (e) {
      if (currentRequest === requestId.current) setErr(e instanceof Error ? e.message : "No se pudo cargar la nómina");
    } finally { if (currentRequest === requestId.current) setLoading(false); }
  }
  useEffect(() => { load(); }, [cursoId, anio, size, pageDisponibles, pageInscriptos, debouncedDisponibles, debouncedInscriptos]);
  useEffect(() => {
    setSelectedDisponibles([]);
    setSelectedInscriptos([]);
  }, [cursoId, anio, size, pageDisponibles, pageInscriptos, debouncedDisponibles, debouncedInscriptos]);

  async function moverSeleccionados(lado: "asignar" | "retirar") {
    const ids = lado === "asignar" ? selectedDisponibles : selectedInscriptos;
    if (!ids.length || !cursoId) return;
    setBusy(true); setErr(""); setOk("");
    try {
      const response = lado === "asignar"
        ? await api.inscribirMasivo({ legajos: ids, cursoId: Number(cursoId), anio })
        : await api.desinscribirMasivo({ legajos: ids, cursoId: Number(cursoId), anio });
      setOk(response.msg); setSelectedDisponibles([]); setSelectedInscriptos([]); await load();
    } catch (e) { setErr(e instanceof Error ? e.message : "No se pudo actualizar la matriculación"); }
    finally { setBusy(false); }
  }

  async function moverTodos(lado: "asignar" | "retirar") {
    if (!cursoId) return;
    const accion = lado === "asignar" ? "asignar a este curso a todos los alumnos actualmente sin curso" : "retirar de este curso a toda la nómina actual";
    if (!window.confirm(`¿Confirmás ${accion} para el ciclo lectivo ${anio}?`)) return;
    setBusy(true); setErr(""); setOk("");
    try {
      const response = lado === "asignar"
        ? await api.matricularDisponibles({ cursoId: Number(cursoId), anio })
        : await api.retirarTodosCurso({ cursoId: Number(cursoId), anio });
      setOk(response.msg); setSelectedDisponibles([]); setSelectedInscriptos([]); setPageDisponibles(0); setPageInscriptos(0); await load();
    } catch (e) { setErr(e instanceof Error ? e.message : "No se pudo actualizar la nómina"); }
    finally { setBusy(false); }
  }

  function toggle(list: "disponibles" | "inscriptos", legajo: string) {
    const values = list === "disponibles" ? selectedDisponibles : selectedInscriptos;
    const setter = list === "disponibles" ? setSelectedDisponibles : setSelectedInscriptos;
    setter(values.includes(legajo) ? values.filter(x => x !== legajo) : [...values, legajo]);
  }

  const left = disponibles?.items || [];
  const right = inscriptos?.items || [];

  return <section className="panel matriculation-panel">
    <div className="roster-heading"><div><h2>Matriculación y armado de cursos</h2><p className="card-meta">Elegí ciclo y curso; incorporá alumnos sin asignación o administrá la nómina actual.</p></div></div>
    {err && <div className="alert alert-error">{err}</div>}{ok && <Toast message={ok} onClose={() => setOk("")}/>}
    <div className="matriculation-selectors"><label>Ciclo lectivo<input type="number" min="2020" max="2100" value={anio} onChange={e => { setAnio(Number(e.target.value)); setPageDisponibles(0); setPageInscriptos(0); }}/></label><label>Curso destino<select value={cursoId} onChange={e => { setCursoId(e.target.value); setPageDisponibles(0); setPageInscriptos(0); }}>{cursos.map(c => <option key={c.id} value={c.id}>{cursoLabel(c.etiqueta)} · {label(c.esp)}</option>)}</select></label></div>
    {selectedCourse && <p className="matriculation-target">Destino seleccionado: <strong>{cursoLabel(selectedCourse.etiqueta)} · {label(selectedCourse.esp)} · {label(selectedCourse.turno)}</strong></p>}

    {cursoId ? <div className="transfer-layout">
      <div className="transfer-list">
        <div className="transfer-list-heading"><div><h3>Alumnos disponibles</h3><span>{(disponibles?.total || 0).toLocaleString("es-AR")} sin asignación en {anio}</span></div><button type="button" className="btn btn-sm" disabled={busy || !disponibles?.total} onClick={() => moverTodos("asignar")}>Agregar todos »</button></div>
        <input type="search" aria-label="Buscar alumnos disponibles" placeholder="Buscar nombre, legajo o correo" value={qDisponibles} onChange={e => setQDisponibles(e.target.value)}/>
        <div className="transfer-items">{loading && !disponibles ? <p className="empty">Cargando alumnos…</p> : left.map(a => <label key={a.legajo} className="transfer-item"><input type="checkbox" checked={selectedDisponibles.includes(a.legajo)} onChange={() => toggle("disponibles", a.legajo)}/><span><strong>{a.nombre}</strong><small>{a.legajo} · {a.mail}</small></span></label>)}{!loading && left.length === 0 && <p className="empty">No hay alumnos disponibles para estos criterios.</p>}</div>
        <TransferPager page={disponibles} current={pageDisponibles} set={setPageDisponibles}/>
      </div>
      <div className="transfer-controls"><button type="button" className="btn btn-primary" aria-label="Agregar seleccionados" title="Agregar seleccionados" disabled={busy || !selectedDisponibles.length} onClick={() => moverSeleccionados("asignar")}>›</button><button type="button" className="btn" aria-label="Retirar seleccionados" title="Retirar seleccionados" disabled={busy || !selectedInscriptos.length} onClick={() => moverSeleccionados("retirar")}>‹</button></div>
      <div className="transfer-list">
        <div className="transfer-list-heading"><div><h3>Nómina actual</h3><span>{(inscriptos?.total || 0).toLocaleString("es-AR")} inscriptos en el curso</span></div><button type="button" className="btn btn-sm" disabled={busy || !inscriptos?.total} onClick={() => moverTodos("retirar")}>Retirar todos «</button></div>
        <input type="search" aria-label="Buscar alumnos inscriptos" placeholder="Buscar en la nómina" value={qInscriptos} onChange={e => setQInscriptos(e.target.value)}/>
        <div className="transfer-items">{loading && !inscriptos ? <p className="empty">Cargando nómina…</p> : right.map(a => <label key={a.legajo} className="transfer-item"><input type="checkbox" checked={selectedInscriptos.includes(a.legajo)} onChange={() => toggle("inscriptos", a.legajo)}/><span><strong>{a.nombre}</strong><small>{a.legajo} · {a.mail}</small></span></label>)}{!loading && right.length === 0 && <p className="empty">Todavía no hay alumnos inscriptos en este curso.</p>}</div>
        <TransferPager page={inscriptos} current={pageInscriptos} set={setPageInscriptos}/>
      </div>
    </div> : <p className="empty">Creá o seleccioná un curso para comenzar la matriculación.</p>}
  </section>;
}

function TransferPager({ page, current, set }: { page: PaginaAlumnos | null; current: number; set: (page: number) => void }) {
  return <div className="transfer-pager"><span>Página {current + 1} de {Math.max(page?.pages || 0, 1)}</span><div><button type="button" className="btn btn-sm" disabled={current === 0} onClick={() => set(Math.max(0, current - 1))}>‹</button><button type="button" className="btn btn-sm" disabled={!page || current + 1 >= page.pages} onClick={() => set(current + 1)}>›</button></div></div>;
}

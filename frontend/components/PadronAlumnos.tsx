"use client";

import { FormEvent, useEffect, useMemo, useRef, useState } from "react";
import Toast from "@/components/Toast";
import { api } from "@/lib/api";
import { label, cursoLabel } from "@/lib/presentation";
import type { AlumnoAdmin, Curso, EstadoAlumno, PaginaAlumnos } from "@/lib/types";

const ESTADOS: { value: EstadoAlumno; title: string }[] = [
  { value: "ACTIVO", title: "Activo" }, { value: "CON_PASE", title: "Con pase" },
  { value: "EGRESADO", title: "Egresado" }, { value: "SUSPENDIDO", title: "Suspendido" },
];

function csvCell(value: string) { return `"${value.replaceAll('"', '""')}"`; }

export default function PadronAlumnos({ cursos, anioInicial }: { cursos: Curso[]; anioInicial: number }) {
  const [anio, setAnio] = useState(anioInicial);
  const [q, setQ] = useState("");
  const [debouncedQ, setDebouncedQ] = useState("");
  const [cursoId, setCursoId] = useState("");
  const [turno, setTurno] = useState("");
  const [especialidad, setEspecialidad] = useState("");
  const [estado, setEstado] = useState("");
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(25);
  const [result, setResult] = useState<PaginaAlumnos | null>(null);
  const [loading, setLoading] = useState(true);
  const [err, setErr] = useState("");
  const [ok, setOk] = useState("");
  const [selected, setSelected] = useState<string[]>([]);
  const [assignOpen, setAssignOpen] = useState(false);
  const [assignCurso, setAssignCurso] = useState("");
  const [saving, setSaving] = useState(false);
  const [altaOpen, setAltaOpen] = useState(false);
  const [alta, setAlta] = useState({ legajo: "", cuil: "", nom: "", nom2: "", ape: "", ape2: "", mail: "", mailTutor: "" });
  const requestId = useRef(0);

  useEffect(() => {
    const timer = window.setTimeout(() => { setDebouncedQ(q.trim()); setPage(0); }, 300);
    return () => window.clearTimeout(timer);
  }, [q]);

  async function load() {
    const currentRequest = ++requestId.current;
    setLoading(true); setErr("");
    const params = new URLSearchParams({ q: debouncedQ, anio: String(anio), page: String(page), size: String(size) });
    if (cursoId) params.set("cursoId", cursoId);
    if (turno) params.set("turno", turno);
    if (especialidad) params.set("especialidad", especialidad);
    if (estado) params.set("estado", estado);
    try {
      const response = await api.padronAlumnos(params);
      if (currentRequest === requestId.current) setResult(response);
    } catch (e) {
      if (currentRequest === requestId.current) setErr(e instanceof Error ? e.message : "No se pudo buscar el padrón");
    } finally { if (currentRequest === requestId.current) setLoading(false); }
  }

  useEffect(() => { load(); }, [debouncedQ, anio, cursoId, turno, especialidad, estado, page, size]);
  useEffect(() => { setSelected([]); }, [debouncedQ, anio, cursoId, turno, especialidad, estado, page, size]);

  const visible = result?.items || [];
  const visibleLegajos = useMemo(() => visible.map(a => a.legajo), [visible]);
  const allVisibleSelected = visibleLegajos.length > 0 && visibleLegajos.every(l => selected.includes(l));
  const first = result && result.total ? result.page * result.size + 1 : 0;
  const last = result ? Math.min((result.page + 1) * result.size, result.total) : 0;

  function toggleVisible() {
    setSelected(current => allVisibleSelected ? current.filter(id => !visibleLegajos.includes(id)) : [...new Set([...current, ...visibleLegajos])]);
  }
  function toggleOne(legajo: string) {
    setSelected(current => current.includes(legajo) ? current.filter(id => id !== legajo) : [...current, legajo]);
  }
  function limpiarFiltros() {
    setQ(""); setDebouncedQ(""); setCursoId(""); setTurno(""); setEspecialidad(""); setEstado(""); setPage(0);
  }

  async function exportarResultados() {
    const params = new URLSearchParams({ q: debouncedQ, anio: String(anio) });
    if (cursoId) params.set("cursoId", cursoId);
    if (turno) params.set("turno", turno);
    if (especialidad) params.set("especialidad", especialidad);
    if (estado) params.set("estado", estado);
    try { await api.exportarPadronAlumnos(params); }
    catch (e) { setErr(e instanceof Error ? e.message : "No se pudo exportar el padrón"); }
  }

  function exportarSeleccionados() {
    const rows = visible.filter(a => selected.includes(a.legajo));
    const csv = ["Legajo,Apellido y nombre,Correo,Curso,Turno,Especialidad,Estado", ...rows.map(a => [a.legajo, a.nombre, a.mail, a.curso || "Sin curso", a.turno || "", a.especialidad || "", a.estado].map(v => csvCell(String(v))).join(","))].join("\r\n");
    const url = URL.createObjectURL(new Blob(["\uFEFF", csv], { type: "text/csv;charset=utf-8" }));
    const link = document.createElement("a"); link.href = url; link.download = "alumnos-seleccionados.csv"; link.click(); URL.revokeObjectURL(url);
  }

  async function descargarSeleccionados() {
    try { setErr(""); await api.descargarBoletinesSeleccionados(selected); setOk("Descarga de boletines preparada"); }
    catch (e) { setErr(e instanceof Error ? e.message : "No se pudieron descargar los boletines"); }
  }

  async function asignarSeleccionados() {
    if (!assignCurso || !selected.length) return;
    setSaving(true); setErr("");
    try {
      const r = await api.inscribirMasivo({ legajos: selected, cursoId: Number(assignCurso), anio });
      setOk(r.msg); setSelected([]); setAssignOpen(false); setPage(0); await load();
    } catch (e) { setErr(e instanceof Error ? e.message : "No se pudo asignar el curso"); }
    finally { setSaving(false); }
  }

  async function altaAlumno(e: FormEvent) {
    e.preventDefault(); setSaving(true); setErr(""); setOk("");
    try {
      await api.crearAlumno({ ...alta, tipo: "ALUMNO", pass: null });
      setAlta({ legajo: "", cuil: "", nom: "", nom2: "", ape: "", ape2: "", mail: "", mailTutor: "" });
      setAltaOpen(false); setOk("Alumno creado. Se envió su contraseña temporal por correo."); await load();
    } catch (e) { setErr(e instanceof Error ? e.message : "No se pudo crear el alumno"); }
    finally { setSaving(false); }
  }

  async function cambiarEstado(alumno: AlumnoAdmin, nuevo: EstadoAlumno) {
    setErr("");
    try { await api.cambiarEstadoAlumno(alumno.legajo, nuevo, anio); await load(); }
    catch (e) { setErr(e instanceof Error ? e.message : "No se pudo actualizar el estado"); }
  }

  return <section className="panel student-roster">
    <div className="roster-heading">
      <div><h2>Padrón general de alumnos</h2><p className="card-meta">Buscá por nombre, apellido, legajo o correo; filtrá y administrá el padrón del ciclo lectivo.</p></div>
      <button type="button" className="btn btn-primary" onClick={() => setAltaOpen(v => !v)}>{altaOpen ? "Cerrar alta" : "+ Alta de alumno"}</button>
    </div>
    {err && <div className="alert alert-error">{err}</div>}{ok && <Toast message={ok} onClose={() => setOk("")}/>}
    {altaOpen && <form className="roster-create-form" onSubmit={altaAlumno}>
      <h3>Nuevo alumno</h3>
      <label>Legajo<input required value={alta.legajo} onChange={e => setAlta({ ...alta, legajo: e.target.value })}/></label>
      <label>CUIL<input required inputMode="numeric" value={alta.cuil} onChange={e => setAlta({ ...alta, cuil: e.target.value })}/></label>
      <label>Nombre<input required value={alta.nom} onChange={e => setAlta({ ...alta, nom: e.target.value })}/></label>
      <label>Segundo nombre<input value={alta.nom2} onChange={e => setAlta({ ...alta, nom2: e.target.value })}/></label>
      <label>Apellido<input required value={alta.ape} onChange={e => setAlta({ ...alta, ape: e.target.value })}/></label>
      <label>Segundo apellido<input value={alta.ape2} onChange={e => setAlta({ ...alta, ape2: e.target.value })}/></label>
      <label>Correo electrónico<input required type="email" value={alta.mail} onChange={e => setAlta({ ...alta, mail: e.target.value })}/></label>
      <label>Correo de tutor<input type="email" value={alta.mailTutor} onChange={e => setAlta({ ...alta, mailTutor: e.target.value })}/></label>
      <div className="roster-create-actions"><button className="btn btn-primary" disabled={saving}>{saving ? "Guardando…" : "Crear alumno"}</button></div>
    </form>}

    <div className="roster-filters">
      <label className="roster-search">Buscar en todo el padrón<input type="search" placeholder="Nombre, apellido, legajo o correo" value={q} onChange={e => setQ(e.target.value)}/></label>
      <label>Ciclo lectivo<input type="number" min="2020" max="2100" value={anio} onChange={e => { setAnio(Number(e.target.value)); setPage(0); }}/></label>
      <label>Curso / división<select value={cursoId} onChange={e => { setCursoId(e.target.value); setPage(0); }}><option value="">Todos los cursos</option><option value="-1">Sin curso asignado</option>{cursos.map(c => <option key={c.id} value={c.id}>{cursoLabel(c.etiqueta)} · {label(c.esp)}</option>)}</select></label>
      <label>Turno<select value={turno} onChange={e => { setTurno(e.target.value); setPage(0); }}><option value="">Todos</option>{[...new Set(cursos.map(c => c.turno))].map(v => <option key={v} value={v}>{label(v)}</option>)}</select></label>
      <label>Especialidad<select value={especialidad} onChange={e => { setEspecialidad(e.target.value); setPage(0); }}><option value="">Todas</option>{[...new Set(cursos.map(c => c.esp))].map(v => <option key={v} value={v}>{label(v)}</option>)}</select></label>
      <label>Estado<select value={estado} onChange={e => { setEstado(e.target.value); setPage(0); }}><option value="">Todos</option>{ESTADOS.map(s => <option key={s.value} value={s.value}>{s.title}</option>)}</select></label>
      <button type="button" className="btn" onClick={limpiarFiltros}>Limpiar filtros</button>
    </div>

    {selected.length > 0 && <div className="bulk-toolbar">
      <strong>{selected.length} seleccionados</strong>
      <button type="button" className="btn btn-sm" onClick={() => { setAssignCurso(cursos[0] ? String(cursos[0].id) : ""); setAssignOpen(true); }}>Asignar curso</button>
      <button type="button" className="btn btn-sm" onClick={descargarSeleccionados}>Boletines PDF (.zip)</button>
      <button type="button" className="btn btn-sm" onClick={exportarSeleccionados}>Exportar seleccionados CSV</button>
      <button type="button" className="btn btn-sm" onClick={() => setSelected([])}>Quitar selección</button>
    </div>}
    {assignOpen && <div className="roster-assign-box"><label>Curso destino<select value={assignCurso} onChange={e => setAssignCurso(e.target.value)}>{cursos.map(c => <option key={c.id} value={c.id}>{cursoLabel(c.etiqueta)} · {label(c.esp)}</option>)}</select></label><button type="button" className="btn btn-primary" disabled={saving || !assignCurso} onClick={asignarSeleccionados}>{saving ? "Asignando…" : `Asignar ${selected.length} alumnos`}</button><button type="button" className="btn" onClick={() => setAssignOpen(false)}>Cancelar</button></div>}

    <div className="roster-table-meta"><span>{loading ? "Buscando alumnos…" : `Mostrando ${first}–${last} de ${(result?.total || 0).toLocaleString("es-AR")} alumnos`}</span><label>Por página<select value={size} onChange={e => { setSize(Number(e.target.value)); setPage(0); }}><option value={25}>25</option><option value={50}>50</option><option value={100}>100</option></select></label></div>
    <div className="table-wrap roster-table-wrap"><table className="data roster-table"><thead><tr><th><input aria-label="Seleccionar alumnos visibles" type="checkbox" checked={allVisibleSelected} onChange={toggleVisible}/></th><th>Legajo</th><th>Apellido y nombre</th><th>Correo electrónico</th><th>Curso</th><th>Turno / especialidad</th><th>Estado</th></tr></thead>
      <tbody>{visible.map(a => <tr key={a.legajo}><td><input aria-label={`Seleccionar ${a.nombre}`} type="checkbox" checked={selected.includes(a.legajo)} onChange={() => toggleOne(a.legajo)}/></td><td className="numeric">{a.legajo}</td><td>{a.nombre}</td><td>{a.mail}</td><td>{a.curso ? cursoLabel(a.curso) : <span className="roster-muted">Sin curso asignado</span>}</td><td>{a.turno ? `${label(a.turno)} · ${label(a.especialidad || "")}` : "—"}</td><td><select aria-label={`Estado de ${a.nombre}`} value={a.estado} onChange={e => cambiarEstado(a, e.target.value as EstadoAlumno)}>{ESTADOS.map(s => <option key={s.value} value={s.value}>{s.title}</option>)}</select></td></tr>)}
        {!loading && visible.length === 0 && <tr><td colSpan={7} className="roster-empty">No hay alumnos que coincidan con la búsqueda y los filtros.</td></tr>}
        {loading && <tr><td colSpan={7} className="roster-empty">Cargando resultados…</td></tr>}
      </tbody></table></div>
    <div className="roster-pagination"><span>Página {result ? result.page + 1 : 1} de {Math.max(1, result?.pages || 0)}</span><div><button type="button" className="btn btn-sm" disabled={page === 0 || loading} onClick={() => setPage(p => Math.max(0, p - 1))}>Anterior</button><button type="button" className="btn btn-sm" disabled={!result || page + 1 >= result.pages || loading} onClick={() => setPage(p => p + 1)}>Siguiente</button></div><button type="button" className="btn btn-sm" onClick={exportarResultados}>Exportar resultados CSV</button></div>
  </section>;
}

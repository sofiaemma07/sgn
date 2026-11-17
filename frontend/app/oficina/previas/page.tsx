"use client";

import { useEffect, useState } from "react";
import Shell from "@/components/Shell";
import { Guard } from "@/components/Guard";
import Toast from "@/components/Toast";
import { api } from "@/lib/api";
import type { ConvocatoriaPrevia, ConvocatoriaPreviaInfo, MateriaPrevia } from "@/lib/types";

type Registro = { convocatoria: ConvocatoriaPrevia; nota: string; fecha: string };

export default function PreviasPage() {
  const [previas, setPrevias] = useState<MateriaPrevia[]>([]);
  const [convocatorias, setConvocatorias] = useState<ConvocatoriaPreviaInfo[]>([]);
  const [anio, setAnio] = useState(new Date().getFullYear());
  const [registros, setRegistros] = useState<Record<number, Registro>>({});
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(0);
  const [err, setErr] = useState("");
  const [ok, setOk] = useState("");

  async function load(year = anio) {
    setErr("");
    try {
      const [p, c] = await Promise.all([api.previas(), api.convocatoriasPrevias(year)]);
      setPrevias(p);
      setConvocatorias(c);
    } catch (e) { setErr(e instanceof Error ? e.message : "No se pudieron cargar las materias previas"); }
    finally { setLoading(false); }
  }

  useEffect(() => { void load(); }, []);

  function datesFor(convocatoria: ConvocatoriaPrevia) {
    return convocatorias.find(c => c.convocatoria === convocatoria && c.anio === anio);
  }

  function cambiarRegistro(id: number, field: keyof Registro, value: string) {
    setRegistros(prev => {
      const existente = prev[id] || { convocatoria: "AGOSTO" as ConvocatoriaPrevia, nota: "", fecha: "" };
      const next = { ...existente, [field]: value } as Registro;
      if (field === "convocatoria") next.fecha = datesFor(value as ConvocatoriaPrevia)?.desde || "";
      return { ...prev, [id]: next };
    });
  }

  async function registrar(previa: MateriaPrevia) {
    const registro = registros[previa.id] || { convocatoria: "AGOSTO", nota: "", fecha: "" };
    if (!registro.nota || !registro.fecha) { setErr("Elegí la convocatoria, la fecha y la nota de la mesa."); return; }
    setSaving(previa.id); setErr(""); setOk("");
    try {
      await api.registrarRendicionPrevia(previa.id, {
        convocatoria: registro.convocatoria, anio, nota: Number(registro.nota), fecha: registro.fecha,
      });
      setRegistros(prev => ({ ...prev, [previa.id]: { ...registro, nota: "", fecha: "" } }));
      setOk(`Rendición de ${previa.materia} guardada`);
      await load();
    } catch (ex) { setErr(ex instanceof Error ? ex.message : "No se pudo guardar la rendición"); }
    finally { setSaving(0); }
  }

  return <Guard roles={["OFICINA", "ADMIN"]}><Shell>
    <div className="previa-header">
      <div><h1>Materias previas</h1><p className="card-meta">Se muestran únicamente las materias desaprobadas en el tercer cierre (febrero).</p></div>
      <label className="previa-year">Año de mesa<input type="number" min="2020" max="2100" value={anio} onChange={e => setAnio(Number(e.target.value))} onBlur={() => void load(anio)} /></label>
    </div>
    {err && <div className="alert alert-error">{err}</div>}
    {ok && <Toast message={ok} onClose={() => setOk("")} />}

    <section className="panel previa-panel">
      <div className="section-heading"><div><h2>Alumnos y materias pendientes</h2><p className="card-meta">La previa se crea automáticamente al desaprobar febrero. Aprobarla no cambia la nota definitiva del ciclo cursado.</p></div><span className="tracking-count">{previas.length} {previas.length === 1 ? "materia" : "materias"}</span></div>
      {loading ? <p className="empty">Cargando previas…</p> : previas.length === 0 ? <div className="previa-empty"><strong>No hay materias previas para mostrar.</strong><span>Se agregan cuando un alumno desaprueba esa materia en el tercer cierre.</span></div> :
        <div className="previa-list">{previas.map(p => {
          const r = registros[p.id] || { convocatoria: "AGOSTO" as ConvocatoriaPrevia, nota: "", fecha: "" };
          const ventana = datesFor(r.convocatoria);
          const fechas = ventana?.desde && ventana.hasta ? [ventana.desde, ventana.hasta] : [];
          return <article className="previa-card" key={p.id}>
            <div className="previa-card-heading">
              <div><h3>{p.materia}</h3><p>{p.alumno} <span>· {p.legajo}</span></p></div>
              <span className={`badge ${p.aprobada ? "badge-ok" : "badge-warn"}`}>{p.aprobada ? "Aprobada" : "Pendiente"}</span>
            </div>
            <div className="previa-card-body">
              <div className="previa-origin"><span>Curso de origen</span><strong>{p.curso} · {p.anioOrigen}</strong></div>
              <div className="previa-history"><span>Rendiciones</span>{p.rendiciones.length ? p.rendiciones.map((x, i) => <p key={`${x.anio}-${x.convocatoria}-${i}`}>{x.convocatoria === "AGOSTO" ? "Agosto" : "Diciembre"} {x.anio} · {x.fecha} · Nota {x.nota} · {x.aprobada ? "Aprobó" : "Desaprobó"}</p>) : <p className="roster-muted">Sin mesas registradas</p>}</div>
            </div>
            {!p.aprobada && <div className="previa-register">
              <label>Convocatoria<select value={r.convocatoria} onChange={e => cambiarRegistro(p.id, "convocatoria", e.target.value)}><option value="AGOSTO">Agosto · 1 y 2</option><option value="DICIEMBRE">Diciembre · dos días después del cierre</option></select></label>
              <label>Fecha<select value={r.fecha} disabled={!fechas.length} onChange={e => cambiarRegistro(p.id, "fecha", e.target.value)}><option value="">{ventana?.desde ? "Seleccionar fecha" : "Configurar fecha de cierre de diciembre"}</option>{fechas.map(f => <option key={f} value={f}>{f}</option>)}</select></label>
              <label>Nota<input type="number" min="1" max="10" step="0.01" placeholder="1 a 10" value={r.nota} onChange={e => cambiarRegistro(p.id, "nota", e.target.value)} /></label>
              <button type="button" className="btn btn-primary" disabled={saving === p.id || !r.fecha || !r.nota} onClick={() => void registrar(p)}>{saving === p.id ? "Guardando…" : "Guardar rendición"}</button>
            </div>}
          </article>;
        })}</div>}
    </section>
  </Shell></Guard>;
}

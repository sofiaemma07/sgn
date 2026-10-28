"use client";

import { useEffect, useState } from "react";
import Toast from "@/components/Toast";
import Shell from "@/components/Shell";
import { Guard } from "@/components/Guard";
import { api } from "@/lib/api";
import type { FaseInfo } from "@/lib/types";

export default function FechasFasePage() {
  const [year, setYear] = useState(2026);
  const [items, setItems] = useState<FaseInfo[]>([]);
  const [dates, setDates] = useState<Record<string, { desde: string; hasta: string }>>({});
  const [err, setErr] = useState("");
  const [ok, setOk] = useState("");
  const [saving, setSaving] = useState("");

  async function load(anio = year) {
    setErr("");
    try {
      const data = await api.fases(anio);
      setItems(data);
      setDates(Object.fromEntries(data.map(f => [f.fase, { desde: f.desde || "", hasta: f.hasta || "" }])));
    } catch (e) { setErr(e instanceof Error ? e.message : "No se pudieron cargar las fases"); }
  }
  useEffect(() => { load(); }, []);

  async function save(item: FaseInfo) {
    setSaving(item.fase); setErr(""); setOk("");
    try {
      await api.configurarFase(year, { fase: item.fase, ...dates[item.fase] });
      setOk(`Fechas de ${item.etiqueta} guardadas`);
      await load();
    } catch (e) { setErr(e instanceof Error ? e.message : "No se pudieron guardar las fechas"); }
    finally { setSaving(""); }
  }

  function estado(item: FaseInfo) {
    if (!item.desde || !item.hasta) return "Sin fechas · bloqueada por calendario";
    const hoy = new Intl.DateTimeFormat("en-CA", { timeZone: "America/Argentina/Buenos_Aires" }).format(new Date());
    if (hoy < item.desde) return "Ventana próxima";
    if (hoy > item.hasta) return "Ventana vencida";
    return "Ventana abierta por fecha";
  }

  return <Guard roles={["OFICINA", "ADMIN"]}><Shell>
    <h1>Fechas de carga de notas</h1>
    <p className="card-meta">Programá las ventanas oficiales de carga. Cada fase queda habilitada desde “Desde” hasta “Hasta”; sin fechas no se puede cargar ni entregar. Los cierres y la calificación definitiva también respetan los requisitos académicos de cada alumno. Podés programar ventanas futuras con anticipación.</p>
    <p className="card-meta">El estado indica si la ventana de calendario está próxima, abierta o vencida. La planilla verifica por separado los requisitos académicos de cada alumno.</p>
    <div className="field year-field"><label htmlFor="anio-fases">Ciclo lectivo</label><input id="anio-fases" type="number" min="2020" max="2100" value={year} onChange={e => setYear(Number(e.target.value))} onBlur={() => load(year)} /></div>
    {err && <div className="alert alert-error">{err}</div>}
    {ok && <Toast message={ok} onClose={() => setOk("")} />}
    <div className="table-wrap"><table className="data phase-date-table"><thead><tr><th>Fase</th><th>Desde</th><th>Hasta · límite obligatorio</th><th>Ventana calendario</th><th></th></tr></thead>
      <tbody>{items.map(item => <tr key={item.fase}><td>{item.etiqueta}</td>
        <td><input className="date-input" aria-label={`Desde ${item.etiqueta}`} type="date" value={dates[item.fase]?.desde || ""} onChange={e => setDates(prev => ({ ...prev, [item.fase]: { ...prev[item.fase], desde: e.target.value } }))} /></td>
        <td><input className="date-input" aria-label={`Hasta ${item.etiqueta}`} type="date" value={dates[item.fase]?.hasta || ""} onChange={e => setDates(prev => ({ ...prev, [item.fase]: { ...prev[item.fase], hasta: e.target.value } }))} /></td>
        <td>{estado(item)}{item.desde && item.hasta && <small className="student-legajo">{item.desde} — {item.hasta}</small>}</td>
        <td className="phase-save-cell"><button className="btn btn-sm btn-primary phase-save-button" disabled={!!saving || !dates[item.fase]?.desde || !dates[item.fase]?.hasta} onClick={() => save(item)}>{saving === item.fase ? "Guardando…" : "Guardar"}</button></td>
      </tr>)}</tbody></table></div>
  </Shell></Guard>;
}

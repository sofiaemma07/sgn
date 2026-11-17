"use client";

import { useEffect, useState } from "react";
import Toast from "@/components/Toast";
import Shell from "@/components/Shell";
import { Guard } from "@/components/Guard";
import { api } from "@/lib/api";
import type { FaseInfo } from "@/lib/types";

const BIMESTRES = ["BIM_1", "BIM_2", "BIM_3", "BIM_4"] as const;
type Fecha = { desde: string; hasta: string };

function fechaTexto(fecha: Date) {
  return `${fecha.getFullYear()}-${String(fecha.getMonth() + 1).padStart(2, "0")}-${String(fecha.getDate()).padStart(2, "0")}`;
}

function sumarDia(fecha: string) {
  const [anio, mes, dia] = fecha.split("-").map(Number);
  return fechaTexto(new Date(anio, mes - 1, dia + 1));
}

function finDelMesSiguiente(desde: string) {
  const [anio, mes] = desde.split("-").map(Number);
  return fechaTexto(new Date(anio, mes + 1, 0));
}

function fechasIniciales(anio: number, fases: FaseInfo[]): Record<string, Fecha> {
  const guardadas = Object.fromEntries(fases.map(f => [f.fase, { desde: f.desde || "", hasta: f.hasta || "" }]));
  const sugeridas: Record<string, Fecha> = {};
  let finAnterior = "";

  BIMESTRES.forEach((fase, indice) => {
    const existente = guardadas[fase];
    const desde = existente?.desde && existente?.hasta
      ? existente.desde
      : indice === 0 ? `${anio}-03-01` : sumarDia(finAnterior);
    const hasta = existente?.desde && existente?.hasta
      ? existente.hasta
      : finDelMesSiguiente(desde);
    sugeridas[fase] = { desde, hasta };
    finAnterior = hasta;
  });

  sugeridas.FINAL_1 = { ...sugeridas.BIM_2 };
  sugeridas.FINAL_2 = { ...sugeridas.BIM_4 };
  for (const fase of fases) {
    if (!sugeridas[fase.fase]) sugeridas[fase.fase] = guardadas[fase.fase];
  }
  return sugeridas;
}

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
      setDates(fechasIniciales(anio, data));
    } catch (e) { setErr(e instanceof Error ? e.message : "No se pudieron cargar las fases"); }
  }
  useEffect(() => { load(); }, []);

  async function save(item: FaseInfo) {
    setSaving(item.fase); setErr(""); setOk("");
    try {
      await api.configurarFase(year, { fase: item.fase, ...dates[item.fase] });
      setOk(item.fase === "BIM_2"
        ? `Fechas de ${item.etiqueta} y del 1° cuatrimestre guardadas`
        : item.fase === "BIM_4"
          ? `Fechas de ${item.etiqueta} y del 2° cuatrimestre guardadas`
          : `Fechas de ${item.etiqueta} guardadas`);
      await load();
    } catch (e) { setErr(e instanceof Error ? e.message : "No se pudieron guardar las fechas"); }
    finally { setSaving(""); }
  }

  function cambiarFecha(fase: string, campo: "desde" | "hasta", valor: string) {
    setDates(prev => {
      const next = { ...prev, [fase]: { ...(prev[fase] || { desde: "", hasta: "" }), [campo]: valor } };
      const grupo = fase === "BIM_2" || fase === "FINAL_1" ? ["BIM_2", "FINAL_1"]
        : fase === "BIM_4" || fase === "FINAL_2" ? ["BIM_4", "FINAL_2"] : [fase];
      for (const relacionada of grupo) next[relacionada] = { ...next[relacionada], [campo]: valor };

      const base = grupo[0];
      const indice = BIMESTRES.indexOf(base as typeof BIMESTRES[number]);
      if (indice < 0) return next;
      if (campo === "desde" && valor && !items.some(f => f.fase === base && f.desde && f.hasta)) {
        next[base].hasta = finDelMesSiguiente(valor);
        for (const relacionada of grupo) next[relacionada].hasta = next[base].hasta;
      }
      if (!next[base].hasta) return next;

      let finAnterior = next[base].hasta;
      for (let i = indice + 1; i < BIMESTRES.length; i++) {
        const siguiente = BIMESTRES[i];
        const yaGuardada = items.some(f => f.fase === siguiente && f.desde && f.hasta);
        if (!yaGuardada) {
          const desde = sumarDia(finAnterior);
          next[siguiente] = { desde, hasta: finDelMesSiguiente(desde) };
          if (siguiente === "BIM_2") next.FINAL_1 = { ...next.BIM_2 };
          finAnterior = next[siguiente].hasta;
        } else {
          finAnterior = items.find(f => f.fase === siguiente)?.hasta || finAnterior;
        }
      }
      if (!items.some(f => f.fase === "FINAL_1" && f.desde && f.hasta)) next.FINAL_1 = { ...next.BIM_2 };
      if (!items.some(f => f.fase === "FINAL_2" && f.desde && f.hasta)) next.FINAL_2 = { ...next.BIM_4 };
      return next;
    });
  }

  function estado(item: FaseInfo) {
    if (!item.desde || !item.hasta) {
      return dates[item.fase]?.desde && dates[item.fase]?.hasta
        ? "Fechas sugeridas · guardá para habilitar"
        : "Sin fechas · bloqueada por calendario";
    }
    const hoy = new Intl.DateTimeFormat("en-CA", { timeZone: "America/Argentina/Buenos_Aires" }).format(new Date());
    if (hoy < item.desde) return "Ventana próxima";
    if (hoy > item.hasta) return "Ventana vencida";
    return "Ventana abierta por fecha";
  }

  return <Guard roles={["OFICINA", "ADMIN"]}><Shell>
    <h1>Fechas de carga de notas</h1>
    <p className="card-meta">Programá las ventanas oficiales de carga. Cada fase queda habilitada desde “Desde” hasta “Hasta”; sin fechas no se puede cargar ni entregar. Los cierres y la calificación definitiva también respetan los requisitos académicos de cada alumno. Podés programar ventanas futuras con anticipación.</p>
    <p className="card-meta">Como sugerencia, el 1° bimestre va del 1 de marzo al último día de abril. Los siguientes empiezan al día siguiente del cierre anterior y terminan el último día del mes siguiente. Podés editar todas las fechas antes de guardarlas. Al guardar el 2° o 4° bimestre también se guardan las fechas del cuatrimestre correspondiente.</p>
    <div className="field year-field"><label htmlFor="anio-fases">Ciclo lectivo</label><input id="anio-fases" type="number" min="2020" max="2100" value={year} onChange={e => setYear(Number(e.target.value))} onBlur={() => load(year)} /></div>
    {err && <div className="alert alert-error">{err}</div>}
    {ok && <Toast message={ok} onClose={() => setOk("")} />}
    <div className="table-wrap"><table className="data phase-date-table"><thead><tr><th>Fase</th><th>Desde</th><th>Hasta · límite obligatorio</th><th>Ventana calendario</th><th></th></tr></thead>
      <tbody>{items.map(item => <tr key={item.fase}><td>{item.etiqueta}</td>
        <td><input className="date-input" aria-label={`Desde ${item.etiqueta}`} type="date" value={dates[item.fase]?.desde || ""} onChange={e => cambiarFecha(item.fase, "desde", e.target.value)} /></td>
        <td><input className="date-input" aria-label={`Hasta ${item.etiqueta}`} type="date" value={dates[item.fase]?.hasta || ""} onChange={e => cambiarFecha(item.fase, "hasta", e.target.value)} /></td>
        <td>{estado(item)}{item.desde && item.hasta && <small className="student-legajo">{item.desde} — {item.hasta}</small>}</td>
        <td className="phase-save-cell"><button className="btn btn-sm btn-primary phase-save-button" disabled={!!saving || !dates[item.fase]?.desde || !dates[item.fase]?.hasta} onClick={() => save(item)}>{saving === item.fase ? "Guardando…" : "Guardar"}</button></td>
      </tr>)}</tbody></table></div>
  </Shell></Guard>;
}

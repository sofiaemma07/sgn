"use client";

import { useEffect, useState } from "react";
import Shell from "@/components/Shell";
import { Guard } from "@/components/Guard";
import { api } from "@/lib/api";
import type { Curso, Perfil } from "@/lib/types";
import PadronAlumnos from "@/components/PadronAlumnos";
import MatriculacionMasiva from "@/components/MatriculacionMasiva";

type Modulo = "padron" | "matriculacion";

export default function OficinaAlumnosPage() {
  const [modulo, setModulo] = useState<Modulo>("padron");
  const [cursos, setCursos] = useState<Curso[]>([]);
  const [anioActivo, setAnioActivo] = useState(new Date().getFullYear());
  const [loading, setLoading] = useState(true);
  const [err, setErr] = useState("");

  useEffect(() => {
    Promise.all([api.cursos(), api.me()])
      .then(([data, perfil]) => { setCursos(data); setAnioActivo(perfil.anioLectivo); })
      .catch(e => setErr(e.message)).finally(() => setLoading(false));
  }, []);

  return <Guard roles={["OFICINA", "ADMIN"]}><Shell>
    <p className="eyebrow">Administración escolar</p>
    <h1>Gestión de alumnos</h1>
    <p className="card-meta">Consultá el padrón y organizá la matriculación por curso y ciclo lectivo.</p>
    {err && <div className="alert alert-error">{err}</div>}
    <div className="roster-tabs" role="tablist" aria-label="Módulos de gestión de alumnos">
      <button type="button" role="tab" aria-selected={modulo === "padron"} className={modulo === "padron" ? "is-active" : ""} onClick={() => setModulo("padron")}>Padrón general</button>
      <button type="button" role="tab" aria-selected={modulo === "matriculacion"} className={modulo === "matriculacion" ? "is-active" : ""} onClick={() => setModulo("matriculacion")}>Matriculación y armado de cursos</button>
    </div>
    {loading ? <section className="panel"><p className="empty">Cargando cursos…</p></section>
      : modulo === "padron" ? <PadronAlumnos cursos={cursos} anioInicial={anioActivo}/> : <MatriculacionMasiva cursos={cursos} anioInicial={anioActivo}/>}
  </Shell></Guard>;
}

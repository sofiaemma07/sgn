"use client";

import { FormEvent, useEffect, useState } from "react";
import Toast from "@/components/Toast";
import { label, cursoLabel } from "@/lib/presentation";
import Shell from "@/components/Shell";
import { Guard } from "@/components/Guard";
import { api } from "@/lib/api";
import type { Bloque, Curso, MailLog, Materia, Usuario } from "@/lib/types";

const TIPOS = ["ADMIN", "OFICINA", "PROFESOR", "ALUMNO", "PRECEPTOR", "DIRECTIVO"] as const;

export default function AdminPage() {
  const [tab, setTab] = useState<"usuarios" | "cursos" | "materias" | "bloques" | "mails">("usuarios");
  const [users, setUsers] = useState<Usuario[]>([]);
  const [cursos, setCursos] = useState<Curso[]>([]);
  const [materias, setMaterias] = useState<Materia[]>([]);
  const [mails, setMails] = useState<MailLog[]>([]);
  const [bloques, setBloques] = useState<Bloque[]>([]);
  const [err, setErr] = useState("");
  const [msg, setMsg] = useState("");
  const [editBloqueId, setEditBloqueId] = useState<number | null>(null);
  const [edit, setEdit] = useState<Usuario | null>(null);
  const [form, setForm] = useState({
    legajo: "",
    cuil: "",
    nom: "",
    ape: "",
    mail: "",
    mailTutor: "",
    tipo: "PROFESOR",
  });
  const [curso, setCurso] = useState({ anio: 4, div: 1, turno: "MANANA", esp: "COMPUTACION" });
  const [matNom, setMatNom] = useState("");
  const [bloque, setBloque] = useState({ materiaId: 0, cursoId: 0, profLegajo: "", segundoProfLegajo: "", modalidad: "INDIVIDUAL" as "INDIVIDUAL" | "SUPLENCIA" | "CONJUNTA", anio: 2026 });

  function load() {
    Promise.all([api.usuarios(), api.cursos(), api.materias(), api.bloquesVer()])
      .then(([u, c, m, b]) => {
        setUsers(u);
        setCursos(c);
        setMaterias(m);
        setBloques(b);
        if (c.length && !bloque.cursoId) setBloque((b) => ({ ...b, cursoId: c[0].id }));
        if (m.length && !bloque.materiaId) setBloque((b) => ({ ...b, materiaId: m[0].id }));
      })
      .catch((e) => setErr(e.message));
  }

  useEffect(() => {
    load();
  }, []);

  async function crearUsr(e: FormEvent) {
    e.preventDefault();
    setErr("");
    try {
      await api.crearUsuario(form);
      setMsg("Usuario creado");
      load();
    } catch (ex) {
      setErr(ex instanceof Error ? ex.message : "Error");
    }
  }

  async function guardarEdit(e: FormEvent) {
    e.preventDefault();
    if (!edit) return;
    setErr("");
    try {
      await api.editarUsuario(edit.legajo, {
        cuil: edit.cuil,
        nom: edit.nom,
        nom2: edit.nom2,
        ape: edit.ape,
        ape2: edit.ape2,
        mail: edit.mail,
        mailTutor: edit.mailTutor,
        tipo: edit.tipo,
      });
      setMsg("Usuario actualizado");
      setEdit(null);
      load();
    } catch (ex) {
      setErr(ex instanceof Error ? ex.message : "Error");
    }
  }

  async function toggle(legajo: string, activo: boolean) {
    setErr("");
    try {
      await api.toggleUsuario(legajo, activo);
      setMsg(`Usuario ${activo ? "activado" : "desactivado"}`);
      load();
    } catch (ex) {
      setErr(ex instanceof Error ? ex.message : "Error");
    }
  }

  async function resetPassword(legajo: string) {
  setErr("");
  setMsg("");

  if (!confirm(`¿Restablecer la contraseña del usuario ${legajo}?`)) {
    return;
  }

  try {
    const res = await api.resetPassword(legajo);
    setMsg(res.msg);
  } catch (ex) {
    setErr(ex instanceof Error ? ex.message : "Error al restablecer contraseña");
  }
}

  async function crearCurso(e: FormEvent) {
    e.preventDefault();
    try {
      await api.crearCurso({ ...curso, esp: curso.anio <= 2 ? "CICLO_BASICO" : curso.esp });
      setMsg("Curso creado");
      load();
    } catch (ex) {
      setErr(ex instanceof Error ? ex.message : "Error");
    }
  }

  async function crearMat(e: FormEvent) {
    e.preventDefault();
    try {
      await api.crearMateria({ nom: matNom });
      setMsg("Materia creada");
      setMatNom("");
      load();
    } catch (ex) {
      setErr(ex instanceof Error ? ex.message : "Error");
    }
  }

  async function asignar(e: FormEvent) {
    e.preventDefault();
    setErr(""); setMsg("");
    try {
      if (editBloqueId) {
        const current = bloques.find((item) => item.id === editBloqueId);
        if (current && current.modalidad === undefined)
          throw new Error("El backend está desactualizado. Reiniciá el servidor Java para habilitar la edición de modalidades docentes.");
        await api.editarBloque(editBloqueId, bloque);
        const actualizados = await api.bloquesVer();
        setBloques(actualizados);
        const guardado = actualizados.find((item) => item.id === editBloqueId);
        if (bloque.modalidad !== "INDIVIDUAL" && (guardado?.modalidad !== bloque.modalidad || guardado?.segundoDocenteLegajo !== bloque.segundoProfLegajo))
          throw new Error("El servidor no guardó la modalidad elegida. Reiniciá el backend y volvé a intentarlo.");
        setMsg("Docentes y modalidad actualizados");
        setEditBloqueId(null);
      } else {
        await api.asignarBloque(bloque);
        const actualizados = await api.bloquesVer();
        setBloques(actualizados);
        const guardado = actualizados.find((item) => item.materiaId === bloque.materiaId && item.cursoId === bloque.cursoId && item.anio === bloque.anio);
        if (bloque.modalidad !== "INDIVIDUAL" && (guardado?.modalidad !== bloque.modalidad || guardado?.segundoDocenteLegajo !== bloque.segundoProfLegajo))
          throw new Error("El servidor respondió a la asignación, pero no guardó la modalidad elegida. Reiniciá el backend y volvé a intentarlo.");
        setMsg(bloque.modalidad === "INDIVIDUAL" ? "Docente asignado al bloque" : "Docentes asignados al bloque");
      }
    } catch (ex) {
      setErr(ex instanceof Error ? ex.message : "Error");
    }
  }

  function editarAsignacion(item: Bloque) {
    setErr(""); setMsg(""); setTab("bloques"); setEditBloqueId(item.id);
    setBloque({ materiaId: item.materiaId, cursoId: item.cursoId, profLegajo: item.docenteLegajo || "",
      segundoProfLegajo: item.segundoDocenteLegajo || "", modalidad: item.modalidad || "INDIVIDUAL", anio: item.anio });
  }

  async function finalizarSuplencia(item: Bloque) {
    setErr("");
    try {
      await api.finalizarSuplencia(item.id);
      setMsg(`Suplencia finalizada. ${item.docente || "El docente titular"} vuelve a quedar a cargo.`);
      load();
    } catch (ex) {
      setErr(ex instanceof Error ? ex.message : "No se pudo finalizar la suplencia");
    }
  }

  function loadMails() {
    api.notificaciones().then(setMails).catch((e) => setErr(e.message));
  }

  const docentes = users.filter((u) => u.tipo === "PROFESOR");

  return (
    <Guard roles={["ADMIN"]}>
      <Shell>
        <h1>Administración</h1>
        {err && <div className="alert alert-error">{err}</div>}
        {msg && <Toast message={msg} onClose={() => setMsg("")}/>}
        <div className="tabs">
          {(["usuarios", "cursos", "materias", "bloques", "mails"] as const).map((t) => (
            <button
              key={t}
              type="button"
              className={`tab ${tab === t ? "active" : ""}`}
              onClick={() => {
                setTab(t);
                if (t === "mails") loadMails();
              }}
            >
              {{usuarios:"Usuarios",cursos:"Cursos",materias:"Materias",bloques:"Bloques",mails:"Correos"}[t]}
            </button>
          ))}
        </div>

        {tab === "usuarios" && (
          <>
            <div className="panel">
              <h2>Crear usuario</h2>
              <form className="form form-wide" onSubmit={crearUsr}>
                <div className="field">
                  <label htmlFor="field-1">Legajo</label>
                  <input id="field-1" value={form.legajo} onChange={(e) => setForm({ ...form, legajo: e.target.value })} required />
                </div>
                <div className="field">
                  <label htmlFor="field-2">CUIL (11 dígitos)</label>
                  <input id="field-2" value={form.cuil} onChange={(e) => setForm({ ...form, cuil: e.target.value })} required />
                </div>
                <div className="field">
                  <label htmlFor="field-3">Nombre</label>
                  <input id="field-3" value={form.nom} onChange={(e) => setForm({ ...form, nom: e.target.value })} required />
                </div>
                <div className="field">
                  <label htmlFor="field-4">Apellido</label>
                  <input id="field-4" value={form.ape} onChange={(e) => setForm({ ...form, ape: e.target.value })} required />
                </div>
                <div className="field">
                  <label htmlFor="field-5">Mail</label>
                  <input id="field-5" type="email" value={form.mail} onChange={(e) => setForm({ ...form, mail: e.target.value })} required />
                </div>
                <div className="field">
                  <label htmlFor="field-6">Mail tutor (alumnos)</label>
                  <input id="field-6" type="email" value={form.mailTutor} onChange={(e) => setForm({ ...form, mailTutor: e.target.value })} required={form.tipo === "ALUMNO"} />
                </div>
                
                <div className="field">
                  <label htmlFor="field-7">Rol</label>
                  <select id="field-7" value={form.tipo} onChange={(e) => setForm({ ...form, tipo: e.target.value })}>
                    {TIPOS.map((t) => (
                      <option key={t} value={t}>{label(t)}</option>
                    ))}
                  </select>
                </div>
                <button type="submit" className="btn btn-primary">
                  Crear
                </button>
              </form>
            </div>

            {edit && (
              <div className="panel">
                <h2>Editar {edit.legajo}</h2>
                <form className="form" onSubmit={guardarEdit}>
                  <div className="field">
                    <label htmlFor="field-8">Nombre</label>
                    <input id="field-8" value={edit.nom} onChange={(e) => setEdit({ ...edit, nom: e.target.value })} />
                  </div>
                  <div className="field">
                    <label htmlFor="field-9">Apellido</label>
                    <input id="field-9" value={edit.ape} onChange={(e) => setEdit({ ...edit, ape: e.target.value })} />
                  </div>
                  <div className="field">
                    <label htmlFor="field-10">Mail</label>
                    <input id="field-10" value={edit.mail} onChange={(e) => setEdit({ ...edit, mail: e.target.value })} />
                  </div>
                  <div className="field">
                    <label htmlFor="field-12">Mail tutor (alumnos)</label>
                    <input id="field-12" type="email" required={edit.tipo === "ALUMNO"} value={edit.mailTutor || ""} onChange={(e) => setEdit({ ...edit, mailTutor: e.target.value })} />
                  </div>
                  <div className="field">
                    <label htmlFor="field-11">Rol</label>
                    <select id="field-11"
                      value={edit.tipo}
                      onChange={(e) => setEdit({ ...edit, tipo: e.target.value as Usuario["tipo"] })}
                    >
                      {TIPOS.map((t) => (
                        <option key={t} value={t}>{label(t)}</option>
                      ))}
                    </select>
                  </div>
                  <div className="btn-row">
                    <button type="submit" className="btn btn-primary">
                      Guardar
                    </button>
                    <button type="button" className="btn" onClick={() => setEdit(null)}>
                      Cancelar
                    </button>
                  </div>
                </form>
              </div>
            )}

            <div className="table-wrap">
              <table className="data">
                <thead>
                  <tr>
                    <th>Legajo</th>
                    <th>CUIL</th>
                    <th>Nombre</th>
                    <th>Mail</th>
                    <th>Tipo</th>
                    <th>Activo</th>
                    <th></th>
                  </tr>
                </thead>
                <tbody>
                  {users.map((u) => (
                    <tr key={u.legajo}>
                      <td className="numeric">{u.legajo}</td>
                      <td className="numeric">{u.cuil}</td>
                      <td>
                        {u.nom} {u.ape}
                      </td>
                      <td>{u.mail}</td>
                      <td>{label(u.tipo)}</td>
                      <td>{u.activo ? "Sí" : "No"}</td>
                      <td>
                        <div className="btn-row">
  <button
    type="button"
    className="btn btn-sm"
    onClick={() => setEdit(u)}
  >
    Editar
  </button>

  <button
    type="button"
    className="btn btn-sm"
    onClick={() => resetPassword(u.legajo)}
  >
    Resetear contraseña
  </button>

  <button
    type="button"
    className="btn btn-sm"
    onClick={() => toggle(u.legajo, !u.activo)}
  >
    {u.activo ? "Desactivar" : "Activar"}
  </button>
</div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </>
        )}

        {tab === "cursos" && (
          <>
            <div className="panel">
              <h2>Nuevo curso</h2>
              <form className="form" onSubmit={crearCurso}>
                <div className="field">
                  <label htmlFor="field-12">Año</label>
                  <input id="field-12" type="number" min="1" max="6" value={curso.anio} onChange={(e) => {
                    const anio = Number(e.target.value);
                    setCurso({ ...curso, anio, esp: anio <= 2 ? "CICLO_BASICO" : curso.esp === "CICLO_BASICO" ? "COMPUTACION" : curso.esp });
                  }} />
                </div>
                <div className="field">
                  <label htmlFor="field-13">División</label>
                  <input id="field-13" type="number" value={curso.div} onChange={(e) => setCurso({ ...curso, div: Number(e.target.value) })} />
                </div>
                <div className="field">
                  <label htmlFor="field-14">Turno</label>
                  <select id="field-14" value={curso.turno} onChange={(e) => setCurso({ ...curso, turno: e.target.value })}>
                    <option value="MANANA">{label("MANANA")}</option>
                    <option value="TARDE">{label("TARDE")}</option>
                    <option value="NOCHE">{label("NOCHE")}</option>
                  </select>
                </div>
                <div className="field">
                  <label htmlFor="field-15">{curso.anio <= 2 ? "Ciclo" : "Especialidad"}</label>
                  {curso.anio <= 2
                    ? <input id="field-15" value={label("CICLO_BASICO")} readOnly />
                    : <select id="field-15" value={curso.esp} onChange={(e) => setCurso({ ...curso, esp: e.target.value })}>
                      <option value="COMPUTACION">{label("COMPUTACION")}</option>
                      <option value="AUTOMOTORES">{label("AUTOMOTORES")}</option>
                    </select>}
                </div>
                <button type="submit" className="btn btn-primary">
                  Crear curso
                </button>
              </form>
            </div>
            <div className="table-wrap">
              <table className="data">
                <thead>
                  <tr>
                    <th>Curso</th>
                    <th>Turno</th>
                    <th>Especialidad</th>
                  </tr>
                </thead>
                <tbody>
                  {cursos.map((c) => (
                    <tr key={c.id}>
                      <td>{cursoLabel(c.etiqueta)}</td>
                      <td>{label(c.turno)}</td>
                      <td>{label(c.esp)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </>
        )}

        {tab === "materias" && (
          <>
            <div className="panel">
              <h2>Nueva materia</h2>
              <form className="form" onSubmit={crearMat}>
                <div className="field">
                  <label htmlFor="field-16">Nombre</label>
                  <input id="field-16" value={matNom} onChange={(e) => setMatNom(e.target.value)} required />
                </div>
                <button type="submit" className="btn btn-primary">
                  Crear
                </button>
              </form>
            </div>
            <h2 id="materias-creadas">Materias creadas</h2>
            <div className="table-wrap">
              <table className="data" aria-labelledby="materias-creadas">
                <thead>
                  <tr>
                    <th scope="col">Nombre</th>
                  </tr>
                </thead>
                <tbody>
                  {materias.map((m) => (
                    <tr key={m.id}>
                      <td>{m.nom}</td>
                    </tr>
                  ))}
                  {materias.length === 0 && (
                    <tr>
                      <td className="empty">No hay materias creadas.</td>
                    </tr>
                  )}
                </tbody>
              </table>
            </div>
          </>
        )}

        {tab === "bloques" && (
          <>
            <div className="panel">
            <h2>{editBloqueId ? "Editar docentes y modalidad" : "Asignar docentes a materia/curso"}</h2>
            <form className="form" onSubmit={asignar}>
              <div className="field">
                <label htmlFor="field-17">Materia</label>
                <select id="field-17"
                  value={bloque.materiaId}
                  onChange={(e) => setBloque({ ...bloque, materiaId: Number(e.target.value) })}
                >
                  {materias.map((m) => (
                    <option key={m.id} value={m.id}>
                      {m.nom}
                    </option>
                  ))}
                </select>
              </div>
              <div className="field">
                <label htmlFor="field-18">Curso</label>
                <select id="field-18"
                  value={bloque.cursoId}
                  onChange={(e) => setBloque({ ...bloque, cursoId: Number(e.target.value) })}
                >
                  {cursos.map((c) => (
                    <option key={c.id} value={c.id}>
                      {cursoLabel(c.etiqueta)}
                    </option>
                  ))}
                </select>
              </div>
              <div className="field">
                <label htmlFor="field-19">{bloque.modalidad === "SUPLENCIA" ? "Docente titular" : "Docente"}</label>
                <select id="field-19" required
                  value={bloque.profLegajo}
                  onChange={(e) => setBloque({ ...bloque, profLegajo: e.target.value, segundoProfLegajo: bloque.segundoProfLegajo === e.target.value ? "" : bloque.segundoProfLegajo })}
                >
                  <option value="">Seleccionar</option>
                  {docentes.map((d) => (
                    <option key={d.legajo} value={d.legajo}>
                      {d.nom} {d.ape} ({d.legajo})
                    </option>
                  ))}
                </select>
              </div>
              <div className="field">
                <label htmlFor="field-21">Modalidad docente</label>
                <select id="field-21" value={bloque.modalidad}
                  onChange={(e) => setBloque({ ...bloque, modalidad: e.target.value as typeof bloque.modalidad, segundoProfLegajo: "" })}>
                  <option value="INDIVIDUAL">Un profesor</option>
                  <option value="SUPLENCIA">Suplencia temporal</option>
                  <option value="CONJUNTA">Dan la materia juntos</option>
                </select>
              </div>
              {bloque.modalidad !== "INDIVIDUAL" && <div className="field">
                <label htmlFor="field-22">{bloque.modalidad === "SUPLENCIA" ? "Profesor suplente" : "Segundo profesor"}</label>
                <select id="field-22" required value={bloque.segundoProfLegajo}
                  onChange={(e) => setBloque({ ...bloque, segundoProfLegajo: e.target.value })}>
                  <option value="">Seleccionar</option>
                  {docentes.filter((d) => d.legajo !== bloque.profLegajo).map((d) => (
                    <option key={d.legajo} value={d.legajo}>{d.nom} {d.ape} ({d.legajo})</option>
                  ))}
                </select>
                {bloque.modalidad === "SUPLENCIA"
                  ? <small>El titular queda suspendido en este bloque hasta que Oficina finalice la suplencia.</small>
                  : <small>Ambos editan la misma planilla. Cada fase requiere la confirmación de los dos para entregarse.</small>}
              </div>}
              <div className="field">
                <label htmlFor="field-20">Año lectivo</label>
                <input id="field-20"
                  type="number"
                  value={bloque.anio}
                  onChange={(e) => setBloque({ ...bloque, anio: Number(e.target.value) })}
                />
              </div>
              <button type="submit" className="btn btn-primary">
                {editBloqueId ? "Guardar cambios" : "Asignar modalidad"}
              </button>
              {editBloqueId && <button type="button" className="btn" onClick={() => setEditBloqueId(null)}>Cancelar</button>}
            </form>
            </div>
            <div className="table-wrap">
              <table className="data">
                <thead><tr><th>Materia</th><th>Curso</th><th>Docente titular</th><th>Segundo docente</th><th>Modalidad</th><th>Estado</th><th>Acciones</th></tr></thead>
                <tbody>{bloques.map((b) => <tr key={b.id}>
                  <td>{b.materia}</td><td>{cursoLabel(b.curso)}</td><td>{b.docente || "—"}</td><td>{b.segundoDocente || "—"}</td>
                  <td>{b.modalidad === "CONJUNTA" ? "Conjunta" : b.modalidad === "SUPLENCIA" ? "Suplencia" : "Individual"}</td>
                  <td>{b.modalidad === "SUPLENCIA" ? b.suplenciaActiva ? "Activa" : "Finalizada" : "Activa"}</td>
                  <td><div className="btn-row">
                    <button type="button" className="btn btn-sm" onClick={() => editarAsignacion(b)}>Editar</button>
                    {b.modalidad === "SUPLENCIA" && b.suplenciaActiva && <button type="button" className="btn btn-sm" onClick={() => void finalizarSuplencia(b)}>Finalizar suplencia</button>}
                  </div></td>
                </tr>)}
                {!bloques.length && <tr><td colSpan={7} className="empty">No hay materias asignadas para el año activo.</td></tr>}</tbody>
              </table>
            </div>
          </>
        )}

        {tab === "mails" && (
          <div className="table-wrap">
            <table className="data">
              <thead>
                <tr>
                  <th>Fecha</th>
                  <th>Destino</th>
                  <th>Asunto</th>
                  <th>Enviado</th>
                  <th>Detalle</th>
                </tr>
              </thead>
              <tbody>
                {mails.map((m) => (
                  <tr key={m.id}>
                    <td className="numeric">{m.fec?.slice(0, 16)}</td>
                    <td>{m.dest}</td>
                    <td>{m.asunto}</td>
                    <td>{m.enviado ? "Sí" : "No"}</td>
                    <td>{m.error || "—"}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Shell>
    </Guard>
  );
}

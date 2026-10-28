import { authHeader, clearSession, getSession } from "./auth";

const BASE = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080";

async function req<T>(path: string, opts: RequestInit = {}): Promise<T> {
  const isPublicAuthPath = path === "/api/auth/login" || path === "/api/auth/recuperar-password";
  const res = await fetch(`${BASE}${path}`, {
    cache: "no-store",
    ...opts,
    headers: {
      "Content-Type": "application/json",
      ...(isPublicAuthPath ? {} : authHeader()),
      ...(opts.headers || {}),
    },
  });
  if (!res.ok) {
    const err = await res.json().catch(() => ({ error: res.statusText }));
    if (res.status === 401 && !isPublicAuthPath) {
      if (typeof window !== "undefined") {
        clearSession();
        window.location.replace("/login");
      }
    }
    throw new Error(err.error || "Error de servidor");
  }
  if (res.status === 204) return undefined as T;

  const text = await res.text();
  if (!text) return undefined as T;
  return JSON.parse(text);
}

export async function downloadPdf(path: string, filename: string) {
  const res = await fetch(`${BASE}${path}`, { cache: "no-store", headers: { ...authHeader() } });
  if (!res.ok) {
    const err = await res.json().catch(() => ({ error: res.statusText }));
    if (res.status === 401) {
      clearSession();
      window.location.replace("/login");
    }
    throw new Error(err.error || "No se pudo descargar el PDF");
  }
  const blob = await res.blob();
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = filename;
  a.click();
  URL.revokeObjectURL(url);
}

export const api = {
  login: (ident: string, pass: string) =>
  req<{
    token: string;
    legajo: string;
    nom: string;
    tipo: string;
    primerIngreso: boolean;
  }>("/api/auth/login", {
    method: "POST",
    body: JSON.stringify({ ident, pass }),
  }),
  recuperarPassword: (ident: string) =>
    req<{ msg: string }>("/api/auth/recuperar-password", {
      method: "POST",
      body: JSON.stringify({ ident }),
    }),
  cambiarPassword: (actual: string, nueva: string) =>
  req<void>("/api/auth/cambiar-password", {
    method: "POST",
    body: JSON.stringify({ actual, nueva }),
  }),
  me: () => req<import("./types").Perfil>("/api/usuarios/me"),
resetPassword: (legajo: string) =>
  req<{ msg: string }>(`/api/usuarios/${legajo}/reset-password`, {
    method: "POST",
  }),
  bloquesProf: () => req<import("./types").Bloque[]>("/api/notas/bloques/prof"),
  bloquesVer: () => req<import("./types").Bloque[]>("/api/notas/bloques/ver"),
  alumnos: (bloqueId: number) =>
    req<import("./types").Alumno[]>(`/api/notas/bloques/${bloqueId}/alumnos`),
  fases: (anio: number) => req<import("./types").FaseInfo[]>(`/api/notas/fases/${anio}`),
  configurarFase: (anio: number, body: object) => req<import("./types").FaseInfo>(`/api/notas/fases/${anio}`, { method: "PUT", body: JSON.stringify(body) }),
  planilla: (bloqueId: number, fase: string) => req<import("./types").Planilla>(`/api/notas/bloques/${bloqueId}/planilla?fase=${fase}`),
  planillaCompleta: (bloqueId: number) => req<import("./types").PlanillaCompleta>(`/api/notas/bloques/${bloqueId}/planilla/completa`),
  guardarPlanilla: (body: object) => req<import("./types").Planilla>("/api/notas/planilla/borrador", { method: "PUT", body: JSON.stringify(body) }),
  guardarBorradores: (body: object) => req<{ msg: string }>("/api/notas/planilla/borradores", { method: "PUT", body: JSON.stringify(body) }),
  cerrarPlanilla: (body: object) => req<import("./types").Planilla>("/api/notas/planilla/cerrar", { method: "POST", body: JSON.stringify(body) }),
  notas: (bloqueId: number, legajo: string) =>
    req<import("./types").Nota[]>(`/api/notas/bloques/${bloqueId}/alumnos/${legajo}`),
  cargarNota: (body: object) =>
    req<import("./types").Nota>("/api/notas", {
      method: "POST",
      body: JSON.stringify(body),
    }),
  solicitar: (body: object) =>
    req<import("./types").Solicitud>("/api/notas/solicitud", {
      method: "POST",
      body: JSON.stringify(body),
    }),
  solicitudes: (todas = false) =>
    req<import("./types").Solicitud[]>(`/api/notas/solicitudes?todas=${todas}`),
  solicitudesProf: () =>
    req<import("./types").Solicitud[]>("/api/notas/solicitudes/prof"),
  resolverSol: (id: number, ok: boolean) =>
    req<import("./types").Solicitud>(`/api/notas/solicitud/${id}/resolver?ok=${ok}`, {
      method: "POST",
    }),
  reporte: (body: object) =>
    req<{ msg: string }>("/api/notas/reporte", {
      method: "POST",
      body: JSON.stringify(body),
    }),
  reportesProf: () => req<import("./types").Reporte[]>("/api/notas/reportes/prof"),
  repSolicitar: (id: number, body: object) =>
    req<import("./types").Solicitud>(`/api/notas/reportes/${id}/solicitar`, {
      method: "POST",
      body: JSON.stringify(body),
    }),
  cerrarReporte: (id: number) =>
    req<{ msg: string }>(`/api/notas/reportes/${id}/cerrar`, { method: "POST" }),
  materiasAlumno: () =>
    req<import("./types").Bloque[]>("/api/notas/alumno/materias"),
  rendimiento: () => req<import("./types").Rendimiento>("/api/notas/alumno/rendimiento"),
  historialAlumno: () => req<import("./types").Nota[]>("/api/notas/alumno/historial"),
  notasReportables: () => req<number[]>("/api/notas/alumno/reportables"),
  cambiosAlumno: () => req<import("./types").Auditoria[]>("/api/notas/alumno/cambios"),
  avisos: () => req<import("./types").NotificationItem[]>("/api/notificaciones"),
  leerAviso: (id: string) => req<{ msg: string }>(`/api/notificaciones/${id}/leer`, { method: "POST" }),
  leerTodasAvisos: () => req<{ msg: string }>("/api/notificaciones/leer-todas", { method: "POST" }),
  limpiarAvisosLeidos: () => req<{ msg: string }>("/api/notificaciones/leidas", { method: "DELETE" }),
  descartarAviso: (id: string) => req<{ msg: string }>(`/api/notificaciones/${id}`, { method: "DELETE" }),
  boletines: () => req<import("./types").Boletin[]>("/api/notas/alumno/boletines"),
  publicacionBoletinAlumno: () => req<import("./types").BoletinPublicacion>("/api/notas/alumno/boletin/publicacion"),
  publicacionBoletinCurso: (bloqueId: number) => req<import("./types").BoletinPublicacion>(`/api/notas/bloques/${bloqueId}/boletin/publicacion`),
  publicarBoletinCurso: (bloqueId: number, publicado: boolean) => req<import("./types").BoletinPublicacion>(`/api/notas/bloques/${bloqueId}/boletin/publicacion?publicado=${publicado}`, { method: "PUT" }),
  notasAnio: (anio: number) =>
    req<import("./types").Nota[]>(`/api/notas/anio/${anio}`),
  auditoria: () => req<import("./types").Auditoria[]>("/api/notas/auditoria"),
  downloadBoletin: () => downloadPdf("/api/notas/alumno/boletin.pdf", "boletin.pdf"),
  downloadPlanillaPdf: (bloqueId: number, filename = `boletin-planilla-${bloqueId}.pdf`) => downloadPdf(`/api/notas/bloques/${bloqueId}/planilla.pdf`, filename),
  downloadBoletinesCursoPdf: (bloqueId: number, filename = `boletines-curso-${bloqueId}.pdf`) => downloadPdf(`/api/notas/bloques/${bloqueId}/boletines.pdf`, filename),
  downloadBoletinCursoAlumno: (bloqueId: number, legajo: string, filename = `boletin-${legajo}.pdf`) => downloadPdf(`/api/notas/bloques/${bloqueId}/alumnos/${legajo}/boletin.pdf`, filename),
  downloadBoletinAlumno: (legajo: string, filename = `boletin-${legajo}.pdf`) =>
    downloadPdf(`/api/notas/alumno/${legajo}/boletin.pdf`, filename),

  usuarios: (tipo?: string) =>
    req<import("./types").Usuario[]>(`/api/usuarios${tipo ? `?tipo=${tipo}` : ""}`),
  padronAlumnos: (params: URLSearchParams) => req<import("./types").PaginaAlumnos>(`/api/usuarios/alumnos/padron?${params.toString()}`),
  exportarPadronAlumnos: async (params: URLSearchParams) => {
    const res = await fetch(`${BASE}/api/usuarios/alumnos/exportar.csv?${params.toString()}`, { cache: "no-store", headers: { ...authHeader() } });
    if (!res.ok) throw new Error("No se pudo exportar el padrón");
    const blob = await res.blob(); const url = URL.createObjectURL(blob); const a = document.createElement("a"); a.href = url; a.download = "padron-alumnos.csv"; a.click(); URL.revokeObjectURL(url);
  },
  crearAlumno: (body: object) => req<import("./types").Usuario>("/api/usuarios/alumnos", { method: "POST", body: JSON.stringify(body) }),
  cambiarEstadoAlumno: (legajo: string, estado: import("./types").EstadoAlumno, anio: number) => req<import("./types").AlumnoAdmin>(`/api/usuarios/${legajo}/estado-alumno?anio=${anio}`, { method: "PATCH", body: JSON.stringify({ estado }) }),
  matriculacionAlumnos: (params: URLSearchParams) => req<import("./types").PaginaAlumnos>(`/api/usuarios/matriculacion/alumnos?${params.toString()}`),
  inscribirMasivo: (body: object) => req<{ msg: string }>("/api/usuarios/matriculacion/asignar", { method: "POST", body: JSON.stringify(body) }),
  desinscribirMasivo: (body: object) => req<{ msg: string }>("/api/usuarios/matriculacion/desasignar", { method: "POST", body: JSON.stringify(body) }),
  matricularDisponibles: (body: object) => req<{ msg: string }>("/api/usuarios/matriculacion/asignar-disponibles", { method: "POST", body: JSON.stringify(body) }),
  retirarTodosCurso: (body: object) => req<{ msg: string }>("/api/usuarios/matriculacion/retirar-todos", { method: "POST", body: JSON.stringify(body) }),
  descargarBoletinesSeleccionados: async (legajos: string[]) => {
    const res = await fetch(`${BASE}/api/usuarios/alumnos/boletines.zip`, { method: "POST", headers: { "Content-Type": "application/json", ...authHeader() }, body: JSON.stringify({ legajos }) });
    if (!res.ok) { const err = await res.json().catch(() => ({ error: res.statusText })); throw new Error(err.error || "No se pudieron descargar los boletines"); }
    const blob = await res.blob(); const url = URL.createObjectURL(blob); const a = document.createElement("a"); a.href = url; a.download = "boletines-alumnos.zip"; a.click(); URL.revokeObjectURL(url);
  },
  crearUsuario: (body: object) =>
    req<import("./types").Usuario>("/api/usuarios", {
      method: "POST",
      body: JSON.stringify(body),
    }),
  crearUsr: (body: object) =>
    req<import("./types").Usuario>("/api/usuarios", {
      method: "POST",
      body: JSON.stringify(body),
    }),
  editarUsuario: (legajo: string, body: object) =>
    req<import("./types").Usuario>(`/api/usuarios/${legajo}`, {
      method: "PUT",
      body: JSON.stringify(body),
    }),
  toggleUsuario: (legajo: string, activo: boolean) =>
    req<import("./types").Usuario>(
      `/api/usuarios/${legajo}/activo?activo=${activo}`,
      { method: "PATCH" }
    ),
  cursos: () => req<import("./types").Curso[]>("/api/usuarios/cursos"),
  crearCurso: (body: object) =>
    req<import("./types").Curso>("/api/usuarios/cursos", {
      method: "POST",
      body: JSON.stringify(body),
    }),
  materias: () => req<import("./types").Materia[]>("/api/usuarios/materias"),
  crearMateria: (body: object) =>
    req<import("./types").Materia>("/api/usuarios/materias", {
      method: "POST",
      body: JSON.stringify(body),
    }),
  inscribir: (body: object) =>
    req<{ msg: string }>("/api/usuarios/inscripcion", {
      method: "POST",
      body: JSON.stringify(body),
    }),
  asignarBloque: (body: object) =>
    req<{ msg: string }>("/api/usuarios/bloque", {
      method: "POST",
      body: JSON.stringify(body),
    }),
  notificaciones: () =>
    req<import("./types").MailLog[]>("/api/usuarios/notificaciones"),
};

export function currentLegajo() {
  return getSession()?.legajo || "";
}

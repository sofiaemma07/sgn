export type TipoUsuario =
  | "PROFESOR"
  | "ALUMNO"
  | "OFICINA"
  | "ADMIN"
  | "PRECEPTOR"
  | "DIRECTIVO";

export type EstadoAlumno = "ACTIVO" | "CON_PASE" | "EGRESADO" | "SUSPENDIDO";

export interface Session {
  token: string;
  legajo: string;
  nom: string;
  tipo: TipoUsuario;
  primerIngreso: boolean;
}

export interface Perfil {
  legajo: string;
  cuil: string;
  nom: string;
  mail: string;
  tipo: TipoUsuario;
  curso: string;
  anioLectivo: number;
}

export interface Bloque {
  id: number;
  titulo: string;
  materiaId: number;
  materia: string;
  cursoId: number;
  curso: string;
  anio: number;
}

export interface Alumno {
  legajo: string;
  nomCompleto: string;
}

export interface Nota {
  id: number;
  tipo: string;
  numPer: number;
  titulo: string | null;
  val: string;
  est: string;
  fecCarga: string | null;
  fecMod: string | null;
  materia?: string;
  curso?: string;
  anio?: number;
  borrador?: boolean;
}

export type FasePlanilla = "BIM_1" | "BIM_2" | "FINAL_1" | "BIM_3" | "BIM_4" | "FINAL_2" | "CUAT_1" | "CUAT_2" | "CIERRE_1" | "CIERRE_2" | "CIERRE_3" | "CALIF_DEF";
export interface FaseInfo { fase: FasePlanilla; etiqueta: string; desde: string | null; hasta: string | null; habilitada: boolean }
export interface PlanillaFila { legajo: string; alumno: string; val: string; notaId: number | null; borrador: boolean }
export interface Planilla { bloqueId: number; materia: string; curso: string; anio: number; fase: FasePlanilla; estado: string; desde: string | null; hasta: string | null; alumnos: PlanillaFila[] }
export interface PlanillaFase { fase: FasePlanilla; etiqueta: string; referencia: string; desde: string | null; hasta: string | null; estado: string }
export interface PlanillaAlumno { legajo: string; alumno: string; notas: Record<string, string> }
export interface PlanillaCompleta { bloqueId: number; materia: string; curso: string; anio: number; fases: PlanillaFase[]; alumnos: PlanillaAlumno[] }

export interface Solicitud {
  id: number;
  notaId: number;
  alumno: string;
  materia: string;
  motivo: string;
  valActual: string;
  valNuevo: string;
  est: string;
  fecSol: string;
  docente?: string;
  autorizo?: string;
  fecRes?: string | null;
}

export interface Reporte {
  id: number;
  notaId: number;
  alumno: string;
  materia: string;
  motivo: string;
  est: string;
  fecRep: string;
  puedeSolicitarCambio?: boolean;
}

export interface Usuario {
  legajo: string;
  cuil: string;
  nom: string;
  nom2: string | null;
  ape: string;
  ape2: string | null;
  mail: string;
  mailTutor: string | null;
  tipo: TipoUsuario;
  activo: boolean;
}

export interface AlumnoAdmin {
  legajo: string;
  nombre: string;
  mail: string;
  activo: boolean;
  estado: EstadoAlumno;
  cursoId: number | null;
  curso: string | null;
  turno: string | null;
  especialidad: string | null;
}

export interface PaginaAlumnos {
  items: AlumnoAdmin[];
  page: number;
  size: number;
  total: number;
  pages: number;
}

export interface Curso {
  id: number;
  anio: number;
  div: number;
  turno: string;
  esp: string;
  etiqueta: string;
}

export interface Materia {
  id: number;
  nom: string;
}

export interface Boletin {
  id: number;
  anio: number;
  tipoPer: string;
  numPer: number;
  contenido: string;
  fecGen: string;
}

export interface BoletinPublicacion {
  publicado: boolean;
  publicadoEn: string | null;
}

export interface Rendimiento {
  pctGlobal: number;
  promGeneral: number;
  materias: { materia: string; promNoOf: number; pct: number; est: string }[];
  periodos: { tipo: string; numPer: number; promNoOf: number }[];
}

export interface Auditoria {
  id: number;
  actor: string;
  autorizo: string;
  alumno: string;
  materia: string;
  periodo: string | null;
  valAnt: string;
  valNuevo: string;
  motivo: string;
  accion: string;
  est: string;
  fec: string;
}

export interface NotificationItem {
  id: string;
  recipient_id: string;
  recipient_role: "alumno" | "docente" | "oficina_alumnos";
  title: string;
  message: string;
  read: boolean;
  created_at: string;
  target_url: string;
  metadata?: {
    materia?: string;
    periodo?: string;
    solicitud_id?: number;
  };
}

export interface MailLog {
  id: number;
  dest: string;
  asunto: string;
  enviado: boolean;
  error: string | null;
  fec: string;
}

export const RUTAS: Record<TipoUsuario, string> = {
  PROFESOR: "/profesor",
  ALUMNO: "/alumno",
  OFICINA: "/oficina",
  ADMIN: "/admin",
  PRECEPTOR: "/otros",
  DIRECTIVO: "/otros",
};

export const TIPOS_NOTA = ["PRUEBA", "TRABAJO", "BIM", "DIC", "FEB", "CUAT", "FINAL"] as const;

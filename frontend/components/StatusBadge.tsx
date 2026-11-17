import { label, notaAprobada, notaDesaprobada } from "@/lib/presentation";
export default function StatusBadge({status,value}: {status:string;value?:string|number}) {
  let tone = "pend";
  if (["RECHAZADA","EN_PROCESO","PREVIA"].includes(status) || notaDesaprobada(value)) tone = "warn";
  else if (["APROBADA","APLICADA","AUTORIZADA"].includes(status) || notaAprobada(value)) tone = "ok";
  else if (["NORMAL","CURSANDO","DOC_SOLICITA"].includes(status)) tone = "info";
  return <span className={"badge badge-" + tone}>{label(status)}</span>;
}

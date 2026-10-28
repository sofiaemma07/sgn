package com.sgn.srv;

import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.sgn.dto.Dtos.PlanillaCompleta;
import com.sgn.dto.Dtos.PlanillaFase;
import com.sgn.dto.Dtos.PlanillaAlumno;
import com.sgn.ent.Usuario;
import com.sgn.repo.InscripcionRepo;
import com.sgn.repo.BoletinPublicacionRepo;
import com.sgn.repo.UsuarioRepo;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

@Service
public class BoletinSrv {

    private final NotaSrv notaSrv;
    private final UsuarioRepo usrRepo;
    private final InscripcionRepo inscRepo;
    private final BoletinPublicacionRepo publicacionRepo;

    @Value("${sgn.anio-activo:2026}")
    private int anioAct;

    public BoletinSrv(NotaSrv notaSrv, UsuarioRepo usrRepo, InscripcionRepo inscRepo,
                      BoletinPublicacionRepo publicacionRepo) {
        this.notaSrv = notaSrv;
        this.usrRepo = usrRepo;
        this.inscRepo = inscRepo;
        this.publicacionRepo = publicacionRepo;
    }

    public byte[] pdfAlumno(String legajoPedido, Usuario actor) {
        if (actor.getTipo().name().equals("ALUMNO") && !actor.getLegajo().equals(legajoPedido)) {
            throw new AccessDeniedException("No puede descargar el boletín de otro alumno");
        }
        Usuario alum = usrRepo.findById(legajoPedido)
            .orElseThrow(() -> new NoSuchElementException("Alumno no encontrado"));
        if (actor.getTipo().name().equals("ALUMNO")) {
            var inscripciones = inscRepo.findByAlumnoLegajoAndAnioAndActivaTrue(legajoPedido, anioAct);
            boolean publicado = !inscripciones.isEmpty() && publicacionRepo
                .findByCursoIdAndAnio(inscripciones.get(0).getCurso().getId(), anioAct)
                .map(p -> p.isPublicado()).orElse(false);
            if (!publicado) throw new org.springframework.security.access.AccessDeniedException(
                "Oficina de Alumnos todavía no publicó el boletín de tu curso");
        }
        return pdfBoletinesCurso(notaSrv.planillasCursoParaBoletin(legajoPedido, anioAct), legajoPedido);
    }

    public byte[] pdfPlanilla(PlanillaCompleta planilla) {
        try {
            var baos = new ByteArrayOutputStream();
            var doc = new Document(PageSize.A3.rotate(), 24, 24, 24, 24);
            PdfWriter.getInstance(doc, baos);
            doc.open();
            var school = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, Color.BLACK);
            var detail = FontFactory.getFont(FontFactory.HELVETICA, 9, Color.BLACK);
            var title = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, Color.BLACK);
            var header = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 7, Color.BLACK);
            var body = FontFactory.getFont(FontFactory.HELVETICA, 8, Color.BLACK);

            PdfPTable masthead = new PdfPTable(new float[]{1.0f, 5.7f, 2.1f});
            masthead.setWidthPercentage(100);
            try (InputStream logoStream = new org.springframework.core.io.ClassPathResource("logo-et35.png").getInputStream()) {
                Image logo = Image.getInstance(logoStream.readAllBytes());
                logo.scaleToFit(62, 72);
                PdfPCell logoCell = new PdfPCell(logo, false);
                logoCell.setBorder(Rectangle.BOX); logoCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
                masthead.addCell(logoCell);
            } catch (Exception ignored) {
                PdfPCell logoCell = new PdfPCell(new Phrase("ET35", school));
                logoCell.setBorder(Rectangle.BOX); logoCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
                masthead.addCell(logoCell);
            }
            PdfPCell schoolCell = new PdfPCell();
            schoolCell.setHorizontalAlignment(Element.ALIGN_CENTER); schoolCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
            schoolCell.addElement(new Paragraph("GOBIERNO DE LA CIUDAD AUTÓNOMA DE BUENOS AIRES", school));
            schoolCell.addElement(new Paragraph("E. T. N° 35 D. E. N° 18 “Ing. Eduardo Latzina”", school));
            schoolCell.addElement(new Paragraph("Av. Lope de Vega 2150 — CABA", detail));
            schoolCell.setPadding(8); masthead.addCell(schoolCell);
            PdfPCell titleCell = new PdfPCell(new Phrase("INFORME ANUAL " + planilla.anio(), title));
            titleCell.setHorizontalAlignment(Element.ALIGN_CENTER); titleCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
            titleCell.setPadding(8); masthead.addCell(titleCell);
            doc.add(masthead);

            PdfPTable datos = new PdfPTable(new float[]{1, 3, 1, 2});
            datos.setWidthPercentage(100); datos.setSpacingBefore(5); datos.setSpacingAfter(8);
            cell(datos, "MATERIA", school); cell(datos, planilla.materia(), detail);
            cell(datos, "CURSO", school); cell(datos, planilla.curso(), detail);
            cell(datos, "CICLO LECTIVO", school); cell(datos, String.valueOf(planilla.anio()), detail);
            doc.add(datos);

            List<PlanillaFase> fases = planilla.fases();
            float[] widths = new float[2 + fases.size()]; widths[0] = .35f; widths[1] = 2.8f;
            for (int i = 2; i < widths.length; i++) widths[i] = .9f;
            PdfPTable tabla = new PdfPTable(widths); tabla.setWidthPercentage(100); tabla.setHeaderRows(1);
            tabla.setSplitLate(false); tabla.setSpacingAfter(6);
            header(tabla, "#", header); header(tabla, "ALUMNO/A", header);
            for (PlanillaFase fase : fases) header(tabla,
                fase.referencia().isBlank() ? fase.etiqueta() : fase.etiqueta() + "\n" + fase.referencia(), header);
            int fila = 1;
            for (PlanillaAlumno alumno : planilla.alumnos()) {
                cell(tabla, String.valueOf(fila++), body); cell(tabla, alumno.alumno(), body);
                for (PlanillaFase fase : fases) {
                    String nota = alumno.notas().get(fase.fase().name());
                    cell(tabla, nota == null || nota.isBlank() ? "–" : nota, body);
                }
            }
            if (planilla.alumnos().isEmpty()) {
                PdfPCell empty = new PdfPCell(new Phrase("No hay alumnos inscriptos", body)); empty.setColspan(widths.length); tabla.addCell(empty);
            }
            doc.add(tabla);
            PdfPTable referencias = new PdfPTable(1); referencias.setWidthPercentage(100); referencias.setHorizontalAlignment(Element.ALIGN_LEFT);
            header(referencias, "VALORACIONES BIMESTRALES RESPECTO DE LOS OBJETIVOS", header);
            cell(referencias, "EP — EN PROCESO: El estudiante está empezando a desarrollar los aprendizajes previstos o evidencia algunas dificultades y necesita mayor tiempo de acompañamiento e intervención.", body);
            cell(referencias, "NC — NO CORRESPONDE: El estudiante se encuentra en una situación pedagógica o administrativa por la cual no corresponde calificar.", body);
            cell(referencias, "NI — NO INICIÓ: El estudiante no inició su trayectoria.", body);
            cell(referencias, "6–10 — FINALIZADO: El estudiante alcanzó su trayectoria escolar evidenciando el logro de los aprendizajes.", body);
            doc.add(referencias);
            doc.close();
            return baos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("No se pudo generar el PDF de la planilla", e);
        }
    }

    public byte[] pdfBoletinesCurso(List<PlanillaCompleta> planillas, String soloLegajo) {
        if (planillas == null || planillas.isEmpty()) throw new NoSuchElementException("El curso no tiene materias cargadas");
        PlanillaCompleta referencia = planillas.get(0);
        List<PlanillaAlumno> alumnos = new ArrayList<>(referencia.alumnos());
        if (soloLegajo != null) alumnos.removeIf(a -> !a.legajo().equals(soloLegajo));
        if (alumnos.isEmpty()) throw new NoSuchElementException("No hay alumnos para generar el boletín");

        try {
            var baos = new ByteArrayOutputStream();
            var doc = new Document(PageSize.A3.rotate(), 24, 24, 24, 24);
            PdfWriter.getInstance(doc, baos);
            doc.open();
            var school = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, Color.BLACK);
            var detail = FontFactory.getFont(FontFactory.HELVETICA, 9, Color.BLACK);
            var title = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, Color.BLACK);
            var header = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 7, Color.BLACK);
            var body = FontFactory.getFont(FontFactory.HELVETICA, 8, Color.BLACK);
            var small = FontFactory.getFont(FontFactory.HELVETICA, 7, Color.BLACK);

            for (int alumnoIndex = 0; alumnoIndex < alumnos.size(); alumnoIndex++) {
                if (alumnoIndex > 0) doc.newPage();
                PlanillaAlumno alumno = alumnos.get(alumnoIndex);

                PdfPTable masthead = new PdfPTable(new float[]{1.0f, 5.7f, 2.1f});
                masthead.setWidthPercentage(100);
                try (InputStream logoStream = new org.springframework.core.io.ClassPathResource("logo-et35.png").getInputStream()) {
                    Image logo = Image.getInstance(logoStream.readAllBytes());
                    logo.scaleToFit(62, 72);
                    PdfPCell logoCell = new PdfPCell(logo, false);
                    logoCell.setBorder(Rectangle.BOX); logoCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
                    masthead.addCell(logoCell);
                } catch (Exception ignored) {
                    PdfPCell logoCell = new PdfPCell(new Phrase("ET35", school));
                    logoCell.setBorder(Rectangle.BOX); logoCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
                    masthead.addCell(logoCell);
                }
                PdfPCell schoolCell = new PdfPCell();
                schoolCell.setHorizontalAlignment(Element.ALIGN_CENTER); schoolCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
                schoolCell.addElement(new Paragraph("GOBIERNO DE LA CIUDAD AUTÓNOMA DE BUENOS AIRES", school));
                schoolCell.addElement(new Paragraph("E. T. N° 35 D. E. N° 18 “Ing. Eduardo Latzina”", school));
                schoolCell.addElement(new Paragraph("Av. Lope de Vega 2150 — CABA", detail));
                schoolCell.setPadding(8); masthead.addCell(schoolCell);
                PdfPCell titleCell = new PdfPCell(new Phrase("INFORME ANUAL " + referencia.anio(), title));
                titleCell.setHorizontalAlignment(Element.ALIGN_CENTER); titleCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
                titleCell.setPadding(8); masthead.addCell(titleCell);
                doc.add(masthead);

                PdfPTable datos = new PdfPTable(new float[]{1, 3, 1, 2});
                datos.setWidthPercentage(100); datos.setSpacingBefore(5); datos.setSpacingAfter(8);
                cell(datos, "ALUMNO/A", school); cell(datos, alumno.alumno(), detail);
                cell(datos, "AÑO / DIVISIÓN", school); cell(datos, referencia.curso(), detail);
                cell(datos, "LEGAJO", school); cell(datos, alumno.legajo(), detail);
                doc.add(datos);

                List<PlanillaFase> fases = referencia.fases();
                float[] widths = new float[2 + fases.size()]; widths[0] = .35f; widths[1] = 2.8f;
                for (int i = 2; i < widths.length; i++) widths[i] = .9f;
                PdfPTable tabla = new PdfPTable(widths); tabla.setWidthPercentage(100); tabla.setHeaderRows(1);
                tabla.setSplitLate(false); tabla.setSpacingAfter(6);
                header(tabla, "#", header); header(tabla, "ASIGNATURA", header);
                for (PlanillaFase fase : fases) header(tabla,
                    fase.referencia().isBlank() ? fase.etiqueta() : fase.etiqueta() + "\n" + fase.referencia(), header);
                int materiaIndex = 1;
                for (PlanillaCompleta planilla : planillas) {
                    PlanillaAlumno fila = planilla.alumnos().stream()
                        .filter(a -> a.legajo().equals(alumno.legajo())).findFirst().orElse(null);
                    cell(tabla, String.valueOf(materiaIndex++), body);
                    cell(tabla, planilla.materia(), body);
                    for (PlanillaFase fase : fases) {
                        String nota = fila == null ? null : fila.notas().get(fase.fase().name());
                        cell(tabla, nota == null || nota.isBlank() ? "–" : nota, body);
                    }
                }
                doc.add(tabla);

                PdfPTable referencias = new PdfPTable(1);
                referencias.setWidthPercentage(68); referencias.setHorizontalAlignment(Element.ALIGN_LEFT);
                header(referencias, "VALORACIONES BIMESTRALES RESPECTO DE LOS OBJETIVOS", header);
                cell(referencias, "EP — EN PROCESO: requiere mayor tiempo de acompañamiento e intervención.", small);
                cell(referencias, "NC — NO CORRESPONDE: situación pedagógica o administrativa por la cual no corresponde calificar.", small);
                cell(referencias, "NI — NO INICIÓ: el estudiante no inició su trayectoria.", small);
                cell(referencias, "6–10 — FINALIZADO: alcanzó los aprendizajes previstos.", small);
                doc.add(referencias);
            }
            doc.close();
            return baos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("No se pudieron generar los boletines del curso", e);
        }
    }

    private void header(PdfPTable t, String txt) {
        var f = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Color.WHITE);
        var c = new PdfPCell(new Phrase(txt, f));
        c.setBackgroundColor(new Color(80, 73, 69));
        c.setPadding(5);
        t.addCell(c);
    }

    private void cell(PdfPTable t, String txt) {
        var f = FontFactory.getFont(FontFactory.HELVETICA, 9, Color.BLACK);
        var c = new PdfPCell(new Phrase(txt == null ? "" : txt, f));
        c.setPadding(4);
        t.addCell(c);
    }

    private void cell(PdfPTable t, String txt, Font font) {
        PdfPCell c = new PdfPCell(new Phrase(txt == null ? "" : txt, font));
        c.setPadding(4); c.setVerticalAlignment(Element.ALIGN_MIDDLE); t.addCell(c);
    }

    private void header(PdfPTable t, String txt, Font font) {
        PdfPCell c = new PdfPCell(new Phrase(txt == null ? "" : txt, font));
        c.setBackgroundColor(new Color(225, 227, 230)); c.setHorizontalAlignment(Element.ALIGN_CENTER);
        c.setVerticalAlignment(Element.ALIGN_MIDDLE); c.setPadding(4); t.addCell(c);
    }
}

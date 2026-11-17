package com.sgn.enm;

public enum FasePlanilla {
    BIM_1("1° Bimestre", TipoNota.BIM, 1),
    BIM_2("2° Bimestre", TipoNota.BIM, 2),
    FINAL_1("Final 1° cuatrimestre", TipoNota.FINAL, 1),
    BIM_3("3° Bimestre", TipoNota.BIM, 3),
    BIM_4("4° Bimestre", TipoNota.BIM, 4),
    FINAL_2("Final 2° cuatrimestre", TipoNota.FINAL, 2),
    CUAT_1("1° Cuatrimestre", TipoNota.CUAT, 1),
    CUAT_2("2° Cuatrimestre", TipoNota.CUAT, 2),
    CIERRE_ANUAL("Cierre anual (marzo–noviembre)", TipoNota.CIERRE_ANUAL, 1),
    CIERRE_1("Cierre de diciembre", TipoNota.DIC, 1),
    CIERRE_2("Cierre de febrero", TipoNota.FEB, 1),
    CIERRE_3("Cierre de marzo (histórico)", TipoNota.FEB, 2),
    CALIF_DEF("Calificación definitiva (automática)", TipoNota.FINAL, 3);

    private final String etiqueta;
    private final TipoNota tipo;
    private final int periodo;

    FasePlanilla(String etiqueta, TipoNota tipo, int periodo) {
        this.etiqueta = etiqueta;
        this.tipo = tipo;
        this.periodo = periodo;
    }

    public String etiqueta() { return etiqueta; }
    public String referencia() {
        return switch (this) {
            case CIERRE_ANUAL -> "Marzo–noviembre";
            case CIERRE_1 -> "Diciembre";
            case CIERRE_2 -> "Febrero";
            case CIERRE_3 -> "Marzo (histórico)";
            default -> "";
        };
    }
    public TipoNota tipo() { return tipo; }
    public int periodo() { return periodo; }
}

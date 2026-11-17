package ec.edu.c4.sri.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/** Respuesta de obtenerPorNumerosRuc del SRI (solo los campos utilizados). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Contribuyente(
        String numeroRuc,
        String razonSocial,
        String estadoContribuyenteRuc,
        String actividadEconomicaPrincipal,
        String tipoContribuyente,
        String regimen,
        String categoria,
        String obligadoLlevarContabilidad,
        String agenteRetencion,
        String contribuyenteEspecial,
        FechasContribuyente informacionFechasContribuyente,
        List<RepresentanteLegal> representantesLegales,
        String contribuyenteFantasma,
        String transaccionesInexistente
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record FechasContribuyente(String fechaInicioActividades, String fechaCese,
                                      String fechaReinicioActividades, String fechaActualizacion) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RepresentanteLegal(String identificacion, String nombre) { }

    public boolean esPersonaNatural() {
        return tipoContribuyente != null && tipoContribuyente.trim().equalsIgnoreCase("PERSONA NATURAL");
    }
}

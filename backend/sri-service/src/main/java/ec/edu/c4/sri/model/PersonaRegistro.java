package ec.edu.c4.sri.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Datos de la persona por cedula (Persona/obtenerPorTipoIdentificacion del SRI).
 * Funciona aunque la persona no tenga RUC.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PersonaRegistro(String identificacion, String nombreCompleto, String tipoPersona) {

    public boolean esPersonaNatural() {
        return "NAT".equalsIgnoreCase(tipoPersona);
    }
}

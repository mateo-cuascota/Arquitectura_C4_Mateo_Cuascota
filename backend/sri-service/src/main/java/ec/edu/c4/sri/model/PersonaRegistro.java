package ec.edu.c4.sri.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Datos de la persona por cedula.
 *
 * @param fuente de donde salio el dato: "SRI" (Persona/obtenerPorTipoIdentificacion)
 *               o "Registro Civil (Didit)" para cedulas que el SRI no conoce.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PersonaRegistro(String identificacion, String nombreCompleto, String tipoPersona, String fuente) {

    public PersonaRegistro(String identificacion, String nombreCompleto, String tipoPersona) {
        this(identificacion, nombreCompleto, tipoPersona, null);
    }

    public PersonaRegistro conFuente(String f) {
        return new PersonaRegistro(identificacion, nombreCompleto, tipoPersona, f);
    }

    public boolean esPersonaNatural() {
        return tipoPersona == null || "NAT".equalsIgnoreCase(tipoPersona);
    }
}

package ec.edu.c4.sri.model;

/**
 * Resultado de la verificacion por cedula o RUC.
 *
 * @param persona        nombre completo por cedula (existe aunque no tenga RUC)
 * @param contribuyente  datos del catastro del SRI (null si no es contribuyente)
 */
public record ContribuyenteResponse(
        String ruc,
        String cedula,
        String email,
        boolean esContribuyente,
        boolean esPersonaNatural,
        PersonaRegistro persona,
        Contribuyente contribuyente
) { }

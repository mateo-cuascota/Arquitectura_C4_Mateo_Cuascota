package ec.edu.c4.sri.model;

/**
 * Resultado de la verificacion: el RUC existe en el catastro, es persona natural
 * y la cedula derivada (10 primeros digitos del RUC) para consultar la ANT.
 */
public record ContribuyenteResponse(
        String ruc,
        String cedula,
        String email,
        boolean esContribuyente,
        boolean esPersonaNatural,
        Contribuyente contribuyente
) { }

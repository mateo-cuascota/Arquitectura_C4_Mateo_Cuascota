package ec.edu.c4.sri.service;

/**
 * Cedula ecuatoriana (10 digitos) o RUC de persona natural (cedula + "001").
 * Valida provincia, tercer digito (0-5 = persona natural) y digito verificador (modulo 10).
 */
public record Identificacion(String cedula, String ruc, boolean ingresoRuc) {

    public static Identificacion de(String valor) {
        String v = valor == null ? "" : valor.trim();
        if (!v.matches("\\d{10}|\\d{10}001")) {
            throw new ValidacionException("IDENTIFICACION_INVALIDA", "Ingresa una cédula de 10 dígitos o un RUC de 13 dígitos terminado en 001");
        }
        String cedula = v.substring(0, 10);
        int tercerDigito = cedula.charAt(2) - '0';
        if (v.length() == 13 && (tercerDigito == 6 || tercerDigito == 9)) {
            // En el RUC el tercer digito 9 = sociedad privada y 6 = entidad publica.
            throw new BusinessRuleException("NO_ES_PERSONA_NATURAL", "El RUC " + v + " corresponde a "
                    + (tercerDigito == 9 ? "una SOCIEDAD" : "una ENTIDAD PÚBLICA") + "; se requiere una PERSONA NATURAL");
        }
        if (!cedulaValida(cedula)) {
            throw new ValidacionException("CEDULA_INVALIDA", "La cédula " + cedula + " no es válida (dígito verificador incorrecto)");
        }
        return new Identificacion(cedula, cedula + "001", v.length() == 13);
    }

    static boolean cedulaValida(String c) {
        int provincia = Integer.parseInt(c.substring(0, 2));
        if ((provincia < 1 || provincia > 24) && provincia != 30) {
            return false;
        }
        if (c.charAt(2) - '0' > 5) {
            return false; // 6 y 9 corresponden a entidades publicas y sociedades
        }
        int suma = 0;
        for (int i = 0; i < 9; i++) {
            int d = (c.charAt(i) - '0') * (i % 2 == 0 ? 2 : 1);
            suma += d > 9 ? d - 9 : d;
        }
        int verificador = (10 - suma % 10) % 10;
        return verificador == c.charAt(9) - '0';
    }
}

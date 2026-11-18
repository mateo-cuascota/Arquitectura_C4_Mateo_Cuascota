package ec.edu.c4.sri.service;

/** Dato de entrada invalido (HTTP 400). */
public class ValidacionException extends RuntimeException {

    private final String codigo;

    public ValidacionException(String codigo, String message) {
        super(message);
        this.codigo = codigo;
    }

    public String codigo() {
        return codigo;
    }
}

package ec.edu.c4.sri.service;

/** Los datos existen pero no cumplen una regla del negocio (HTTP 422). */
public class BusinessRuleException extends RuntimeException {

    private final String codigo;

    public BusinessRuleException(String codigo, String message) {
        super(message);
        this.codigo = codigo;
    }

    public String codigo() {
        return codigo;
    }
}

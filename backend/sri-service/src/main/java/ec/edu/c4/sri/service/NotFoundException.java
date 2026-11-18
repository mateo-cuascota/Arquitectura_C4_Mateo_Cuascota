package ec.edu.c4.sri.service;

/** Recurso inexistente en el SRI (HTTP 404). */
public class NotFoundException extends RuntimeException {

    private final String codigo;

    public NotFoundException(String codigo, String message) {
        super(message);
        this.codigo = codigo;
    }

    public String codigo() {
        return codigo;
    }
}

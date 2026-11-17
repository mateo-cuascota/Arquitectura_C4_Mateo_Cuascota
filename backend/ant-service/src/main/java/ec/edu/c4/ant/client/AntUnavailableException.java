package ec.edu.c4.ant.client;

/** La ANT no entrego una respuesta adecuada (caida, timeout, 5xx, pagina de error). */
public class AntUnavailableException extends RuntimeException {
    public AntUnavailableException(String message) {
        super(message);
    }

    public AntUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}

package ec.edu.c4.ant.api;

import ec.edu.c4.ant.client.AntUnavailableException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.net.URI;
import java.time.Instant;

/**
 * Contrato de errores del ANT Service (RFC 9457 Problem Details + codigo/mensaje/timestamp).
 * Ver docs/api-contrato.md.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    /** Segundos sugeridos para reintentar (coincide con la espera del circuit breaker). */
    static final String REINTENTAR_EN = "60";

    static ResponseEntity<ProblemDetail> problema(HttpStatus status, String codigo, String mensaje, HttpServletRequest req) {
        ProblemDetail p = ProblemDetail.forStatusAndDetail(status, mensaje);
        p.setTitle(status.getReasonPhrase());
        p.setInstance(URI.create(req.getRequestURI()));
        p.setProperty("codigo", codigo);
        p.setProperty("mensaje", mensaje);
        p.setProperty("timestamp", Instant.now().toString());
        return ResponseEntity.status(status).body(p);
    }

    @ExceptionHandler(AntUnavailableException.class)
    ResponseEntity<ProblemDetail> antCaida(AntUnavailableException e, HttpServletRequest req) {
        ResponseEntity<ProblemDetail> r = problema(HttpStatus.SERVICE_UNAVAILABLE, "ANT_NO_DISPONIBLE", e.getMessage(), req);
        r.getBody().setProperty("reintentarEnSegundos", Integer.parseInt(REINTENTAR_EN));
        return ResponseEntity.status(r.getStatusCode()).header(HttpHeaders.RETRY_AFTER, REINTENTAR_EN).body(r.getBody());
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    ResponseEntity<ProblemDetail> validacion(HandlerMethodValidationException e, HttpServletRequest req) {
        String msg = e.getAllValidationResults().stream()
                .flatMap(r -> r.getResolvableErrors().stream())
                .map(MessageSourceResolvable::getDefaultMessage)
                .findFirst().orElse("Datos inválidos");
        return problema(HttpStatus.BAD_REQUEST, codigoPorMensaje(msg), msg, req);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ProblemDetail> restricciones(ConstraintViolationException e, HttpServletRequest req) {
        String msg = e.getConstraintViolations().stream()
                .map(jakarta.validation.ConstraintViolation::getMessage)
                .findFirst().orElse("Datos inválidos");
        return problema(HttpStatus.BAD_REQUEST, codigoPorMensaje(msg), msg, req);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ProblemDetail> cuerpoInvalido(HttpMessageNotReadableException e, HttpServletRequest req) {
        return problema(HttpStatus.BAD_REQUEST, "CUERPO_INVALIDO",
                "El cuerpo de la petición no es un JSON válido. Ejemplo: {\"activo\": true, \"antCaida\": false}", req);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ProblemDetail> tipoInvalido(MethodArgumentTypeMismatchException e, HttpServletRequest req) {
        return problema(HttpStatus.BAD_REQUEST, "PARAMETRO_INVALIDO", "El parámetro '" + e.getName() + "' tiene un valor inválido", req);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ProblemDetail> rutaInexistente(NoResourceFoundException e, HttpServletRequest req) {
        return problema(HttpStatus.NOT_FOUND, "RUTA_NO_ENCONTRADA", "La ruta " + req.getRequestURI() + " no existe", req);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ProblemDetail> metodo(HttpRequestMethodNotSupportedException e, HttpServletRequest req) {
        return problema(HttpStatus.METHOD_NOT_ALLOWED, "METODO_NO_PERMITIDO",
                "El método " + e.getMethod() + " no está permitido en esta ruta", req);
    }

    private static String codigoPorMensaje(String msg) {
        String m = msg.toLowerCase();
        if (m.contains("placa")) return "PLACA_INVALIDA";
        if (m.contains("cédula") || m.contains("cedula")) return "CEDULA_INVALIDA";
        return "DATOS_INVALIDOS";
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> inesperado(Exception e, HttpServletRequest req) {
        log.error("Error no controlado en {}", req.getRequestURI(), e);
        return problema(HttpStatus.INTERNAL_SERVER_ERROR, "ERROR_INTERNO", "Ocurrió un error inesperado. Intenta de nuevo.", req);
    }
}

package ec.edu.c4.sri.api;

import ec.edu.c4.sri.service.BusinessRuleException;
import ec.edu.c4.sri.service.NotFoundException;
import ec.edu.c4.sri.service.ValidacionException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.RestClientException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.net.URI;
import java.time.Instant;
import java.util.Map;

/**
 * Contrato de errores del SRI Service (RFC 9457 Problem Details + campos propios).
 * Todas las respuestas de error tienen la forma:
 * <pre>{ "status": 404, "codigo": "RUC_NO_REGISTRADO", "mensaje": "...", "title": "...", "detail": "...",
 *   "instance": "/api/sri/...", "timestamp": "..." }</pre>
 * Ver docs/api-contrato.md.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    private static final Map<String, String> PARAMETROS = Map.of(
            "email", "El correo electrónico es obligatorio");

    static ResponseEntity<ProblemDetail> problema(HttpStatus status, String codigo, String mensaje, HttpServletRequest req) {
        ProblemDetail p = ProblemDetail.forStatusAndDetail(status, mensaje);
        p.setTitle(status.getReasonPhrase());
        p.setInstance(URI.create(req.getRequestURI()));
        p.setProperty("codigo", codigo);
        p.setProperty("mensaje", mensaje);
        p.setProperty("timestamp", Instant.now().toString());
        return ResponseEntity.status(status).body(p);
    }

    @ExceptionHandler(ValidacionException.class)
    ResponseEntity<ProblemDetail> validacion(ValidacionException e, HttpServletRequest req) {
        return problema(HttpStatus.BAD_REQUEST, e.codigo(), e.getMessage(), req);
    }

    @ExceptionHandler(NotFoundException.class)
    ResponseEntity<ProblemDetail> noEncontrado(NotFoundException e, HttpServletRequest req) {
        return problema(HttpStatus.NOT_FOUND, e.codigo(), e.getMessage(), req);
    }

    @ExceptionHandler(BusinessRuleException.class)
    ResponseEntity<ProblemDetail> reglaNegocio(BusinessRuleException e, HttpServletRequest req) {
        return problema(HttpStatus.UNPROCESSABLE_ENTITY, e.codigo(), e.getMessage(), req);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    ResponseEntity<ProblemDetail> validacionParametros(HandlerMethodValidationException e, HttpServletRequest req) {
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

    @ExceptionHandler(MissingServletRequestParameterException.class)
    ResponseEntity<ProblemDetail> parametroFaltante(MissingServletRequestParameterException e, HttpServletRequest req) {
        String msg = PARAMETROS.getOrDefault(e.getParameterName(), "Falta el parámetro obligatorio '" + e.getParameterName() + "'");
        String codigo = "email".equals(e.getParameterName()) ? "CORREO_REQUERIDO" : "PARAMETRO_FALTANTE";
        return problema(HttpStatus.BAD_REQUEST, codigo, msg, req);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ProblemDetail> tipoInvalido(MethodArgumentTypeMismatchException e, HttpServletRequest req) {
        return problema(HttpStatus.BAD_REQUEST, "PARAMETRO_INVALIDO",
                "El parámetro '" + e.getName() + "' tiene un valor inválido", req);
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

    @ExceptionHandler(RestClientException.class)
    ResponseEntity<ProblemDetail> sriCaido(RestClientException e, HttpServletRequest req) {
        log.warn("El SRI no respondió correctamente: {}", e.getMessage());
        return problema(HttpStatus.BAD_GATEWAY, "SRI_NO_DISPONIBLE",
                "El servicio del SRI no respondió correctamente. Intenta de nuevo en unos minutos.", req);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> inesperado(Exception e, HttpServletRequest req) {
        log.error("Error no controlado en {}", req.getRequestURI(), e);
        return problema(HttpStatus.INTERNAL_SERVER_ERROR, "ERROR_INTERNO",
                "Ocurrió un error inesperado. Intenta de nuevo.", req);
    }

    private static String codigoPorMensaje(String msg) {
        String m = msg.toLowerCase();
        if (m.contains("correo")) return m.contains("obligatorio") ? "CORREO_REQUERIDO" : "CORREO_INVALIDO";
        if (m.contains("placa")) return "PLACA_INVALIDA";
        if (m.contains("cédula") || m.contains("ruc")) return "IDENTIFICACION_INVALIDA";
        return "DATOS_INVALIDOS";
    }
}

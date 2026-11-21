package com.akhana.akhana_admin.exception;

import com.akhana.akhana_admin.dto.ErrorResponse;
import com.akhana.akhana_admin.service.ProductImportService;
import com.akhana.akhana_admin.service.ProductPhotoService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    static final String UNREADABLE_REQUEST = "Los datos enviados no son válidos; revise el formulario e intente de nuevo.";
    static final String INVALID_PARAMETER = "El valor de «%s» no es válido.";
    static final String MISSING_PARAMETER = "Falta el dato «%s».";
    static final String CONFLICT_WITH_ANOTHER_SAVE = "No se pudo guardar: otro registro igual se guardó al mismo tiempo. Actualice la pantalla y revise antes de intentar de nuevo.";
    static final String UNEXPECTED_ERROR = "Ocurrió un error inesperado. Intente de nuevo; si se repite, avise al administrador.";
    static final String NOT_FOUND = "No existe la dirección solicitada.";
    static final String METHOD_NOT_ALLOWED = "La operación no está permitida en esta dirección.";
    static final String MISSING_FILE = "Falta el archivo «%s».";
    static final String OPERATION_IN_PROGRESS = "Otra operación está en curso, intente de nuevo.";
    static final String FILE_TOO_LARGE = "El archivo supera el tamaño máximo de 10 MB.";
    private static final String OWN_PACKAGE = "com.akhana.akhana_admin.";

    // Each upload by the prefix of its path, with the message of what it accepts
    private static final List<Map.Entry<Pattern, String>> UPLOAD_MESSAGES = List.of(
        Map.entry(Pattern.compile("^/api/products/import(/|$)"), ProductImportService.INVALID_FILE),
        Map.entry(Pattern.compile("^/api/products/[^/]+/photo(/|$)"), ProductPhotoService.INVALID_FILE),
        Map.entry(Pattern.compile("^/api/purchases/[^/]+/photo(/|$)"), ProductPhotoService.INVALID_FILE),
        Map.entry(Pattern.compile("^/api/purchases/readings(/|$)"), ProductPhotoService.INVALID_FILE));

    @ExceptionHandler(AuthenticationFailedException.class)
    public ResponseEntity<ErrorResponse> handleAuthenticationFailed(AuthenticationFailedException ex) {
        return ResponseEntity
            .status(HttpStatus.UNAUTHORIZED)
            .body(ErrorResponse.unauthorized(ex.getMessage()));
    }

    @ExceptionHandler(LoginBlockedException.class)
    public ResponseEntity<ErrorResponse> handleLoginBlocked(LoginBlockedException ex) {
        return ResponseEntity
            .status(HttpStatus.TOO_MANY_REQUESTS)
            .body(ErrorResponse.tooManyRequests(ex.getMessage()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity
            .status(HttpStatus.FORBIDDEN)
            .body(ErrorResponse.forbidden("No tiene permiso para realizar esta acción."));
    }

    @ExceptionHandler(DuplicateResourceException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateResource(DuplicateResourceException ex) {
        return ResponseEntity
            .status(HttpStatus.CONFLICT)
            .body(ErrorResponse.conflict(ex.getMessage()));
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleResourceNotFound(ResourceNotFoundException ex) {
        return ResponseEntity
            .status(HttpStatus.NOT_FOUND)
            .body(ErrorResponse.notFound(ex.getMessage()));
    }

    // A business rule of this application writes its message in Spanish for the person: thrown from our own code, it
    // goes as is. One thrown from a library (the JDK, Spring, POI, Jackson) is internal and goes as the generic one.
    // Our own internal errors use InternalErrorException, never these two
    @ExceptionHandler({IllegalStateException.class, IllegalArgumentException.class})
    public ResponseEntity<ErrorResponse> handleIllegalState(RuntimeException ex) {
        if (!thrownByOwnCode(ex)) {
            log.warn("Internal error answered with the generic message", ex);
            return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.badRequest(UNEXPECTED_ERROR));
        }
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse.badRequest(ex.getMessage()));
    }

    static boolean thrownByOwnCode(Throwable ex) {
        StackTraceElement[] stack = ex.getStackTrace();
        return ex.getMessage() != null && stack.length > 0 && stack[0].getClassName().startsWith(OWN_PACKAGE);
    }

    // Each upload (CAT-06 import, CAT-12 product photo, COM-19 purchase photo, COM-20 reading) with its own message
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleMaxUploadSize(MaxUploadSizeExceededException ex,
                                                             HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        String message = UPLOAD_MESSAGES.stream()
            .filter(entry -> entry.getKey().matcher(path).find())
            .map(Map.Entry::getValue)
            .findFirst()
            .orElse(FILE_TOO_LARGE);
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse.badRequest(message));
    }

    @ExceptionHandler(ProductPhotoStorageException.class)
    public ResponseEntity<ErrorResponse> handleProductPhotoStorage(ProductPhotoStorageException ex) {
        log.error("Could not save a product photo", ex);
        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ErrorResponse.internalError(ex.getMessage()));
    }

    // One message per field (the first broken rule), so a blank field does not also report its length
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationErrors(MethodArgumentNotValidException ex) {
        Map<String, String> byField = new LinkedHashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            byField.putIfAbsent(error.getField(), error.getDefaultMessage());
        }
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse.badRequest(String.join(" ", byField.values())));
    }

    // A body that is not valid JSON or has a value of the wrong type, such as an unknown option
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadable(HttpMessageNotReadableException ex) {
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse.badRequest(UNREADABLE_REQUEST));
    }

    // A query or path parameter that cannot be read, such as a date that is not yyyy-mm-dd
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse.badRequest(String.format(INVALID_PARAMETER, ex.getName())));
    }

    // A unique index caught what the service check missed (two saves at the same time): a conflict, not a crash
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrity(DataIntegrityViolationException ex) {
        return ResponseEntity
            .status(HttpStatus.CONFLICT)
            .body(ErrorResponse.conflict(CONFLICT_WITH_ANOTHER_SAVE));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParameter(MissingServletRequestParameterException ex) {
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse.badRequest(String.format(MISSING_PARAMETER, ex.getParameterName())));
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ErrorResponse> handleMissingPart(MissingServletRequestPartException ex) {
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse.badRequest(String.format(MISSING_FILE, ex.getRequestPartName())));
    }

    @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class})
    public ResponseEntity<ErrorResponse> handleNoRoute(Exception ex) {
        return ResponseEntity
            .status(HttpStatus.NOT_FOUND)
            .body(ErrorResponse.notFound(NOT_FOUND));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        return ResponseEntity
            .status(HttpStatus.METHOD_NOT_ALLOWED)
            .body(ErrorResponse.methodNotAllowed(METHOD_NOT_ALLOWED));
    }

    // A row lock that could not be taken in time (lock_timeout, a deadlock): the other operation wins, this one retries
    @ExceptionHandler(PessimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handleLockFailure(PessimisticLockingFailureException ex) {
        log.warn("A lock could not be acquired: {}", ex.getMessage());
        return ResponseEntity
            .status(HttpStatus.CONFLICT)
            .body(ErrorResponse.conflict(OPERATION_IN_PROGRESS));
    }

    // Anything else: logged with its trace, and the person sees a message in Spanish without internal details
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request) {
        // Spring's own request errors (a media type it does not read, a ResponseStatusException) keep their status
        if (ex instanceof org.springframework.web.ErrorResponse springError
            && springError.getStatusCode().is4xxClientError()) {
            log.warn("Request rejected on {} {}: {}", request.getMethod(), request.getRequestURI(), ex.getMessage());
            return ResponseEntity
                .status(springError.getStatusCode())
                .body(new ErrorResponse(HttpStatus.valueOf(springError.getStatusCode().value()).getReasonPhrase(),
                    UNREADABLE_REQUEST, java.time.Instant.now()));
        }
        log.error("Unexpected error on {} {}", request.getMethod(), request.getRequestURI(), ex);
        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ErrorResponse.internalError(UNEXPECTED_ERROR));
    }
}

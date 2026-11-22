package com.sdas.server.exception;

import com.sdas.server.common.BusinessException;
import com.sdas.server.common.ErrorCode;
import com.sdas.server.common.Result;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 全局异常处理器：把异常统一转换为 {@code { code, message, data }} 响应。
 *
 * <p>HTTP 状态与 body 中的 code 一致；所有 message 取自 {@link ErrorCode}。</p>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * 处理业务异常。
     *
     * @param exception 业务异常
     * @return 对应错误码的响应
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result<Void>> handleBusinessException(BusinessException exception) {
        LOGGER.warn("Business exception, code={}, traceId={}",
                exception.getErrorCode().getCode(), MDC.get("traceId"));
        return response(exception.getErrorCode());
    }

    /**
     * 处理参数校验类异常。
     *
     * @param exception 参数异常
     * @return 400 响应
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<Void>> handleValidation(MethodArgumentNotValidException exception) {
        LOGGER.warn("Invalid request field, traceId={}", MDC.get("traceId"));
        FieldError fieldError = exception.getBindingResult().getFieldError();
        String field = fieldError == null ? "" : fieldError.getField();
        return response(mapFormatError(field));
    }

    @ExceptionHandler({
            ConstraintViolationException.class,
            BindException.class,
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class,
            HttpMessageNotReadableException.class
    })
    public ResponseEntity<Result<Void>> handleBadRequest(Exception exception) {
        LOGGER.warn("Invalid request, exception={}, traceId={}",
                exception.getClass().getSimpleName(), MDC.get("traceId"));
        return response(ErrorCode.BAD_REQUEST);
    }

    /**
     * 处理 MySQL / Redis 不可用或超时，统一降级响应。
     *
     * @param exception 依赖异常
     * @return 503 响应
     */
    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<Result<Void>> handleDependencyFailure(DataAccessException exception) {
        LOGGER.warn("Dependency unavailable, exception={}, traceId={}",
                exception.getClass().getSimpleName(), MDC.get("traceId"));
        return response(ErrorCode.SERVICE_UNAVAILABLE);
    }

    /**
     * 处理请求方法不支持。
     *
     * @param exception 方法不支持异常
     * @return 405 响应
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Result<Void>> handleMethodNotSupported(HttpRequestMethodNotSupportedException exception) {
        LOGGER.warn("Method not supported, traceId={}", MDC.get("traceId"));
        return response(ErrorCode.METHOD_NOT_ALLOWED);
    }

    /**
     * 处理资源不存在。
     *
     * @param exception 资源未找到异常
     * @return 404 响应
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Result<Void>> handleNotFound(NoResourceFoundException exception) {
        LOGGER.warn("Resource not found, traceId={}", MDC.get("traceId"));
        return response(ErrorCode.NOT_FOUND);
    }

    /**
     * 兜底异常处理，仅记录日志，不向客户端泄漏堆栈与连接信息。
     *
     * @param exception 未预期异常
     * @return 500 响应
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleUnexpectedException(Exception exception) {
        LOGGER.error("Unexpected exception, traceId={}", MDC.get("traceId"), exception);
        return response(ErrorCode.INTERNAL_ERROR);
    }

    private ErrorCode mapFormatError(String field) {
        return switch (field) {
            case "phone" -> ErrorCode.INVALID_PHONE_FORMAT;
            case "password", "newPassword" -> ErrorCode.INVALID_PASSWORD_FORMAT;
            case "verificationCode" -> ErrorCode.INVALID_VERIFICATION_CODE_FORMAT;
            case "realName" -> ErrorCode.INVALID_NAME_FORMAT;
            case "idCardNumber" -> ErrorCode.INVALID_ID_CARD_FORMAT;
            case "agreementAccepted", "agreementVersion" -> ErrorCode.AGREEMENT_NOT_ACCEPTED;
            default -> ErrorCode.BAD_REQUEST;
        };
    }

    private ResponseEntity<Result<Void>> response(ErrorCode errorCode) {
        return ResponseEntity.status(errorCode.getCode()).body(Result.<Void>failure(errorCode));
    }
}

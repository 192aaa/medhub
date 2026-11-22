package com.sdas.server.common;

/**
 * 业务异常，仅携带 {@link ErrorCode}，不接收自定义 message。
 */
public class BusinessException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final ErrorCode errorCode;

    /**
     * 构造业务异常。
     *
     * @param errorCode 错误码枚举
     */
    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    /** @return 错误码枚举 */
    public ErrorCode getErrorCode() {
        return errorCode;
    }
}

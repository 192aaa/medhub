package com.sdas.server.common;

import java.io.Serializable;

/**
 * 统一响应体 {@code { code, message, data }}。
 *
 * <p>契约来源：specs/health-check/contracts/health-api.md；枚举与 code 定义见
 * specs/health-check/data-model.md。</p>
 *
 * @param <T> 响应数据类型
 */
public class Result<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    private int code;

    private String message;

    private T data;

    /** 默认构造，供 Jackson 反序列化使用。 */
    public Result() {
    }

    private Result(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    /**
     * 构造无数据的成功响应。
     *
     * @param <T> 响应数据类型
     * @return code=200 的响应
     */
    public static <T> Result<T> success() {
        return new Result<>(ErrorCode.SUCCESS.getCode(), ErrorCode.SUCCESS.getMessage(), null);
    }

    /**
     * 构造带数据的成功响应。
     *
     * @param data 响应数据
     * @param <T>  响应数据类型
     * @return code=200 的响应
     */
    public static <T> Result<T> success(T data) {
        return new Result<>(ErrorCode.SUCCESS.getCode(), ErrorCode.SUCCESS.getMessage(), data);
    }

    /**
     * 构造失败响应，message 统一取自 ErrorCode。
     *
     * @param errorCode 错误码枚举，禁止传入自定义文案
     * @param <T>       响应数据类型
     * @return 失败响应
     */
    public static <T> Result<T> failure(ErrorCode errorCode) {
        return new Result<>(errorCode.getCode(), errorCode.getMessage(), null);
    }

    /**
     * 构造带降级数据的失败响应（如健康检查报告）。
     *
     * @param errorCode 错误码枚举
     * @param data      附加数据
     * @param <T>       响应数据类型
     * @return 失败响应
     */
    public static <T> Result<T> failure(ErrorCode errorCode, T data) {
        return new Result<>(errorCode.getCode(), errorCode.getMessage(), data);
    }

    /** @return 业务状态码 */
    public int getCode() {
        return code;
    }

    /** @param code 业务状态码 */
    public void setCode(int code) {
        this.code = code;
    }

    /** @return 提示信息 */
    public String getMessage() {
        return message;
    }

    /** @param message 提示信息 */
    public void setMessage(String message) {
        this.message = message;
    }

    /** @return 响应数据 */
    public T getData() {
        return data;
    }

    /** @param data 响应数据 */
    public void setData(T data) {
        this.data = data;
    }
}

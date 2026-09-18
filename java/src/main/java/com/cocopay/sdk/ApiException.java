package com.cocopay.sdk;

/** 网关返回的业务错误（HTTP 200 且 code=200 之外的情况） */
public class ApiException extends RuntimeException {
    private final int code;
    private final String reason;

    public ApiException(int code, String reason, String message) {
        super("cocopay: 请求失败 code=" + code + " reason=" + (reason == null ? "" : reason) + " message=" + message);
        this.code = code;
        this.reason = reason == null ? "" : reason;
    }

    public int getCode() {
        return code;
    }

    public String getReason() {
        return reason;
    }
}

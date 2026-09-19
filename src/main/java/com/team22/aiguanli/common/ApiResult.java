package com.team22.aiguanli.common;

public class ApiResult<T> {
    private boolean ok;
    private String message;
    private T data;

    public static <T> ApiResult<T> success(T data) {
        ApiResult<T> r = new ApiResult<>();
        r.ok = true;
        r.message = "ok";
        r.data = data;
        return r;
    }

    public static ApiResult<Void> fail(String message) {
        ApiResult<Void> r = new ApiResult<>();
        r.ok = false;
        r.message = message;
        return r;
    }

    public boolean isOk() {
        return ok;
    }

    public String getMessage() {
        return message;
    }

    public T getData() {
        return data;
    }
}

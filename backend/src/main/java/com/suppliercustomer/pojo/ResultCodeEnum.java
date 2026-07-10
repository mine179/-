package com.suppliercustomer.pojo;

public enum ResultCodeEnum {
    SUCCESS(1, "success"),
    SYSTEM_ERROR(0, "请求失败"),
    TOKEN_INVALID_ERROR(401, "登录已过期，请重新登录"),
    NO_PERMISSION(403, "当前账号没有权限"),
    PARAM_ERROR(400, "参数错误");

    public final Integer code;
    public final String msg;

    ResultCodeEnum(Integer code, String msg) {
        this.code = code;
        this.msg = msg;
    }
}

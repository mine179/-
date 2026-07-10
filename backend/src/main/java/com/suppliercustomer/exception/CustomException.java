package com.suppliercustomer.exception;

import com.suppliercustomer.pojo.ResultCodeEnum;

public class CustomException extends RuntimeException {
    private Integer code;
    private String msg;

    public CustomException(Integer code, String msg) {
        this.code = code;
        this.msg = msg;
    }

    public CustomException(String msg) {
        this.code = ResultCodeEnum.SYSTEM_ERROR.code;
        this.msg = msg;
    }

    public CustomException(ResultCodeEnum resultCodeEnum) {
        this.code = resultCodeEnum.code;
        this.msg = resultCodeEnum.msg;
    }

    public Integer getCode() {
        return code;
    }

    public void setCode(Integer code) {
        this.code = code;
    }

    public String getMsg() {
        return msg;
    }

    public void setMsg(String msg) {
        this.msg = msg;
    }
}

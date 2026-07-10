package com.suppliercustomer.exception;

import cn.dev33.satoken.exception.SaTokenException;
import com.suppliercustomer.pojo.Result;
import com.suppliercustomer.pojo.ResultCodeEnum;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.servlet.http.HttpServletRequest;

@ControllerAdvice(basePackages = "com.suppliercustomer.controller")
public class GlobalExceptionHandler {
    @ExceptionHandler(SaTokenException.class)
    @ResponseBody
    public Result saTokenError(HttpServletRequest request, SaTokenException e) {
        return Result.error(ResultCodeEnum.TOKEN_INVALID_ERROR.code, ResultCodeEnum.TOKEN_INVALID_ERROR.msg);
    }

    @ExceptionHandler(CustomException.class)
    @ResponseBody
    public Result customError(HttpServletRequest request, CustomException e) {
        return Result.error(e.getCode(), e.getMsg());
    }

    @ExceptionHandler(Exception.class)
    @ResponseBody
    public Result error(HttpServletRequest request, Exception e) {
        e.printStackTrace();
        return Result.error(ResultCodeEnum.SYSTEM_ERROR.code, e.getMessage() == null ? "请求失败" : e.getMessage());
    }
}

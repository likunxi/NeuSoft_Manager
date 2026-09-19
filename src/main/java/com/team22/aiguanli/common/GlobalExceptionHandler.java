package com.team22.aiguanli.common;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.ModelAndView;

@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BizException.class)
    public Object handleBiz(BizException ex, HttpServletRequest request) {
        if (wantsJson(request)) {
            return ResponseEntity.status(ex.getStatus()).body(ApiResult.fail(ex.getMessage()));
        }
        ModelAndView mv = new ModelAndView("error");
        mv.setStatus(HttpStatus.valueOf(ex.getStatus()));
        mv.addObject("message", ex.getMessage());
        return mv;
    }

    private boolean wantsJson(HttpServletRequest request) {
        String accept = request.getHeader("Accept");
        String uri = request.getRequestURI();
        return uri.startsWith("/api") || (accept != null && accept.contains("application/json"));
    }
}

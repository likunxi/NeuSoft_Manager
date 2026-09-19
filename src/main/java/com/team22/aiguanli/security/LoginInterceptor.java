package com.team22.aiguanli.security;

import com.team22.aiguanli.common.BizException;
import com.team22.aiguanli.common.Constants;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class LoginInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (!(handler instanceof HandlerMethod method)) {
            return true;
        }
        HttpSession session = request.getSession(false);
        LoginUser user = session == null ? null : (LoginUser) session.getAttribute(Constants.SESSION_USER);
        if (user == null) {
            if (request.getRequestURI().startsWith("/api")) {
                throw new BizException(401, "未登录");
            }
            response.sendRedirect("/login");
            return false;
        }
        RequirePerm require = method.getMethodAnnotation(RequirePerm.class);
        if (require != null) {
            boolean ok = false;
            for (String perm : require.value()) {
                if (user.has(perm)) {
                    ok = true;
                    break;
                }
            }
            if (!ok) {
                throw new BizException(403, "没有权限：" + String.join(",", require.value()));
            }
        }
        return true;
    }
}

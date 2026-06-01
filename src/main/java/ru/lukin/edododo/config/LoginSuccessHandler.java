package ru.lukin.edododo.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

/**
 * После входа перенаправляет на фронтенд (SPA), а не на корень Spring Boot без статики.
 */
@Component
public class LoginSuccessHandler extends SavedRequestAwareAuthenticationSuccessHandler {

    public LoginSuccessHandler(@Value("${app.security.login-success-url}") String loginSuccessUrl) {
        setDefaultTargetUrl(loginSuccessUrl);
        setAlwaysUseDefaultTargetUrl(true);
        setTargetUrlParameter(null);
    }
}

package ru.lukin.edododo.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Корень backend не отдаёт SPA; перенаправляем на фронтенд (избегаем 500 «No static resource»).
 */
@Controller
public class HomeController {

    @Value("${app.security.login-success-url}")
    private final String loginSuccessUrl;

    public HomeController(@Value("${app.security.login-success-url}") String loginSuccessUrl) {
        this.loginSuccessUrl = loginSuccessUrl;
    }

    @GetMapping("/")
    public String redirectToFrontend() {
        return "redirect:" + loginSuccessUrl;
    }
}

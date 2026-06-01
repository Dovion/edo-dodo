package ru.lukin.edododo.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class LoginPageController {

    @GetMapping("/login")
    public String loginPage(HttpServletRequest request, org.springframework.ui.Model model) {
        CsrfToken csrf = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        if (csrf != null) {
            model.addAttribute("csrfParameterName", csrf.getParameterName());
            model.addAttribute("csrfToken", csrf.getToken());
        }
        return "login";
    }
}

package com.agridabao.api.auth;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register/request")
    public CodeRequestResponse requestRegister(@Valid @RequestBody RegisterRequest request) {
        return authService.requestRegister(request);
    }

    @PostMapping("/register/verify")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse verifyRegister(@Valid @RequestBody VerifyCodeRequest request) {
        return authService.verifyRegister(request);
    }

    @PostMapping("/login/request")
    public CodeRequestResponse requestLogin(@Valid @RequestBody LoginRequest request) {
        return authService.requestLogin(request);
    }

    @PostMapping("/login/verify")
    public AuthResponse verifyLogin(@Valid @RequestBody VerifyCodeRequest request) {
        return authService.verifyLogin(request);
    }
}

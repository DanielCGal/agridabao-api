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

    // Password recovery, in three steps: prove the account exists and email a
    // code, confirm the code for a short-lived ticket, then spend the ticket on
    // a new password. Splitting the last two means the player types the new
    // password on its own screen, after the code has already been accepted.

    @PostMapping("/password/forgot/request")
    public CodeRequestResponse requestPasswordReset(@Valid @RequestBody ForgotPasswordRequest request) {
        return authService.requestPasswordReset(request);
    }

    @PostMapping("/password/forgot/verify")
    public PasswordResetTicketResponse verifyPasswordReset(
            @Valid @RequestBody ForgotPasswordVerifyRequest request) {
        return authService.verifyPasswordReset(request);
    }

    @PostMapping("/password/forgot/reset")
    public AuthResponse resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        return authService.resetPassword(request);
    }
}

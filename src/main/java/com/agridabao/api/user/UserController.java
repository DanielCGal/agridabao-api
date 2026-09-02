package com.agridabao.api.user;

import com.agridabao.api.auth.AuthResponse;
import com.agridabao.api.auth.CodeRequestResponse;
import com.agridabao.api.auth.UserResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/users")
public class UserController {
    private final AccountService accountService;

    public UserController(AccountService accountService) {
        this.accountService = accountService;
    }

    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal Jwt jwt) {
        return accountService.me(userId(jwt));
    }

    @PostMapping("/me/email/request")
    public CodeRequestResponse requestEmailChange(@AuthenticationPrincipal Jwt jwt,
                                                  @Valid @RequestBody ChangeEmailRequest request) {
        return accountService.requestEmailChange(userId(jwt), request);
    }

    @PostMapping("/me/email/verify")
    public AuthResponse confirmEmailChange(@AuthenticationPrincipal Jwt jwt,
                                           @Valid @RequestBody ConfirmEmailChangeRequest request) {
        return accountService.confirmEmailChange(userId(jwt), request);
    }

    @PostMapping("/me/password")
    public AuthResponse changePassword(@AuthenticationPrincipal Jwt jwt,
                                       @Valid @RequestBody ChangePasswordRequest request) {
        return accountService.changePassword(userId(jwt), request);
    }

    @PostMapping("/me/display-name")
    public UserResponse changeDisplayName(@AuthenticationPrincipal Jwt jwt,
                                          @Valid @RequestBody ChangeDisplayNameRequest request) {
        return accountService.changeDisplayName(userId(jwt), request);
    }

    private static UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}

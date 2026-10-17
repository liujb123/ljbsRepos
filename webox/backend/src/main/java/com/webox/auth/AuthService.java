package com.webox.auth;

import com.webox.auth.dto.AuthResponse;
import com.webox.auth.dto.LoginRequest;
import com.webox.auth.dto.RegisterRequest;
import com.webox.auth.dto.UserResponse;
import com.webox.common.BusinessException;
import com.webox.common.ErrorCode;
import com.webox.security.JwtService;
import com.webox.user.User;
import com.webox.user.UserRepository;
import com.webox.user.UserRole;
import java.util.Locale;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    /** Normalizes an email exactly once, at every entry point. */
    public static String normalizeEmail(String raw) {
        return raw == null ? null : raw.trim().toLowerCase(Locale.ROOT);
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = normalizeEmail(request.email());
        String displayName = request.displayName().trim();

        if (userRepository.existsByEmail(email)) {
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_REGISTERED);
        }

        User user = new User(email, passwordEncoder.encode(request.password()), displayName, UserRole.EMPLOYEE);
        try {
            user = userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException ex) {
            // Two concurrent registrations for the same address: the unique index wins.
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_REGISTERED);
        }
        return issue(user);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        String email = normalizeEmail(request.email());
        User user = userRepository.findByEmail(email)
                // Same error for "unknown email" and "wrong password": no account enumeration.
                .orElseThrow(() -> new BusinessException(ErrorCode.AUTH_INVALID_CREDENTIALS));
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.AUTH_INVALID_CREDENTIALS);
        }
        return issue(user);
    }

    @Transactional(readOnly = true)
    public UserResponse currentUser(Long userId) {
        return userRepository.findById(userId)
                .map(UserResponse::from)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHENTICATED));
    }

    private AuthResponse issue(User user) {
        return AuthResponse.bearer(jwtService.issue(user), jwtService.ttlSeconds(), UserResponse.from(user));
    }
}

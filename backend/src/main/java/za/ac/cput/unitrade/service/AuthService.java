package za.ac.cput.unitrade.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.ac.cput.unitrade.domain.User;
import za.ac.cput.unitrade.dto.AuthResponse;
import za.ac.cput.unitrade.dto.LoginRequest;
import za.ac.cput.unitrade.dto.RegisterRequest;
import za.ac.cput.unitrade.dto.UserResponse;
import za.ac.cput.unitrade.exception.ApiException;
import za.ac.cput.unitrade.repository.UserRepository;
import za.ac.cput.unitrade.security.JwtService;

/** FR1: register and log in students. */
@Service
public class AuthService {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final StudentEmailPolicy emailPolicy;

    public AuthService(UserRepository users, PasswordEncoder passwordEncoder, JwtService jwtService,
                       StudentEmailPolicy emailPolicy) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.emailPolicy = emailPolicy;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = StudentEmailPolicy.normalise(request.email());
        if (!emailPolicy.isAllowed(email)) {
            throw ApiException.badRequest("email", "Use your university email address ending in " + emailPolicy.requiredSuffix());
        }
        if (users.existsByEmail(email)) {
            throw ApiException.conflict("An account with this email already exists. Try logging in instead.");
        }
        User user = users.save(User.builder()
                .fullName(request.fullName().trim())
                .email(email)
                .passwordHash(passwordEncoder.encode(request.password()))
                .build());
        return new AuthResponse(jwtService.createToken(user), UserResponse.from(user));
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        // Same message for "no such email" and "wrong password", so nobody can probe which emails are registered.
        User user = users.findByEmail(StudentEmailPolicy.normalise(request.email()))
                .filter(found -> passwordEncoder.matches(request.password(), found.getPasswordHash()))
                .orElseThrow(() -> ApiException.unauthorized("Incorrect email or password."));
        return new AuthResponse(jwtService.createToken(user), UserResponse.from(user));
    }

    @Transactional(readOnly = true)
    public UserResponse currentUser(Long userId) {
        // The token was valid but the account may have been deleted since: treat that as "not logged in".
        return users.findById(userId)
                .map(UserResponse::from)
                .orElseThrow(() -> ApiException.unauthorized("Please log in to continue."));
    }
}

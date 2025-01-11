package com.comp301project.SkyFly.Service;

import com.comp301project.SkyFly.DTO.AuthRequest;
import com.comp301project.SkyFly.DTO.AuthResponse;
import com.comp301project.SkyFly.DTO.RegisterRequest;
import com.comp301project.SkyFly.DTO.UserDTO;
import com.comp301project.SkyFly.Model.User;
import com.comp301project.SkyFly.Repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.modelmapper.ModelMapper;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {
    private static final Logger logger = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final ModelMapper modelMapper;
    private final AuthenticationManager authenticationManager;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        logger.info("Processing registration request for email: {}", request.getEmail());

        // E-posta kontrolü
        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            logger.error("Email already exists: {}", request.getEmail());
            throw new RuntimeException("Bu email adresi zaten kayıtlı");
        }

        try {
            // Şifreyi hashle
            String hashedPassword = passwordEncoder.encode(request.getPassword());
            logger.debug("Password hashed successfully");

            // Yeni kullanıcı oluştur
            User user = User.builder()
                    .firstName(request.getFirstName())
                    .lastName(request.getLastName())
                    .email(request.getEmail())
                    .password(hashedPassword)
                    .phoneNumber(request.getPhoneNumber())
                    .build();

            // Kullanıcıyı kaydet
            user = userRepository.save(user);
            logger.info("User saved successfully: {}", user.getEmail());

            // Token oluştur
            String token = jwtService.generateToken(user);

            // Response için UserDTO oluştur
            UserDTO userDTO = modelMapper.map(user, UserDTO.class);

            return AuthResponse.builder()
                    .token(token)
                    .user(userDTO)
                    .build();

        } catch (Exception e) {
            logger.error("Registration failed for email: " + request.getEmail(), e);
            throw new RuntimeException("Kayıt işlemi başarısız oldu");
        }
    }

    @Transactional
    public AuthResponse login(AuthRequest request) {
        logger.info("Processing login request for email: {}", request.getEmail());

        try {
            // Kullanıcıyı bul
            User user = userRepository.findByEmail(request.getEmail())
                    .orElseThrow(() -> {
                        logger.error("User not found with email: {}", request.getEmail());
                        return new RuntimeException("E-posta veya şifre hatalı");
                    });

            // Şifre kontrolü
            if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
                logger.error("Password mismatch for user: {}", request.getEmail());
                throw new RuntimeException("E-posta veya şifre hatalı");
            }

            // Authentication
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
            );

            SecurityContextHolder.getContext().setAuthentication(authentication);

            // Token oluştur
            String token = jwtService.generateToken(user);

            // Response için UserDTO oluştur
            UserDTO userDTO = modelMapper.map(user, UserDTO.class);

            logger.info("Login successful for user: {}", request.getEmail());

            return AuthResponse.builder()
                    .token(token)
                    .user(userDTO)
                    .build();

        } catch (Exception e) {
            logger.error("Login failed for user: " + request.getEmail(), e);
            throw new RuntimeException("E-posta veya şifre hatalı");
        }
    }

    @Transactional(readOnly = true)
    public UserDTO getCurrentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        logger.debug("Fetching current user details for email: {}", email);

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> {
                    logger.error("User not found with email: {}", email);
                    return new RuntimeException("Kullanıcı bulunamadı");
                });

        return modelMapper.map(user, UserDTO.class);
    }

    public void logout() {
        SecurityContextHolder.clearContext();
        logger.info("User logged out successfully");
    }
}
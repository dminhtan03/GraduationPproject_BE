package com.finalProject.BookingMeetingRoom.service.user;

import jakarta.mail.MessagingException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class UserServiceTest_register {

    @InjectMocks
    private UserServiceImpl userService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private UserInfoRepository userInfoRepository;

    @Mock
    private UserMapper userMapper;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private RedisService redisService;

    @Mock
    private UserOtpRepository userOtpRepository;

    @Mock
    private EmailService emailService;

    private RegistrationRequest request;
    private User user;
    private UserInfo userInfo;
    private Role role;
    private RegistrationResponse response;

    @BeforeEach
    public void setup() {
        // Initialize RegistrationRequest
        request = new RegistrationRequest();
        request.setEmail("test@example.com");
        request.setPassword("password123");
        request.setRole("USER");
        request.setFirstName("John");
        request.setLastName("Doe");
        request.setPhoneNumber("1234567890");
        request.setAddress("123 Main St");
        request.setGender("Male");
        request.setDepartment("IT");

        // Initialize UserInfo
        userInfo = new UserInfo();
        userInfo.setId(UUID.randomUUID().toString());
        userInfo.setEmail("test@example.com");
        userInfo.setFirstName("John");
        userInfo.setLastName("Doe");
        userInfo.setPhoneNumber("1234567890");
        userInfo.setAddress("123 Main St");
        userInfo.setGender("Male");
        userInfo.setDepartment("IT");
        userInfo.setCreatedAt(LocalDateTime.now());

        // Initialize User
        user = new User();
        user.setId(UUID.randomUUID().toString());
        user.setPassword("encodedPassword");
        user.setEnabled(false);
        user.setLocked(false);
        user.setLoginCount(0);
        user.setUserInfo(userInfo);
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        user.setRoles(new HashSet<>());
        user.setRefreshTokens(new ArrayList<>());
        user.setReservations(new ArrayList<>());

        // Initialize Role
        role = new Role();
        role.setName("USER");

        // Initialize RegistrationResponse
        response = new RegistrationResponse();
        response.setEmail(userInfo.getEmail());
        response.setFirstName(userInfo.getFirstName());
        response.setLastName(userInfo.getLastName());
        response.setPhoneNumber(userInfo.getPhoneNumber());
        response.setAddress(userInfo.getAddress());
        response.setGender(userInfo.getGender());
        response.setDepartment(userInfo.getDepartment());
    }

    @Test
    void register_Success() {
        // Arrange
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.empty());
        when(roleRepository.findRoleByName("USER")).thenReturn(Optional.of(role));
        when(userMapper.toUserInfo(request)).thenReturn(userInfo);
        when(userInfoRepository.save(any(UserInfo.class))).thenReturn(userInfo);
        when(userMapper.toUser(request)).thenReturn(user);
        when(passwordEncoder.encode("password123")).thenReturn("encodedPassword");
        when(userRepository.save(any(User.class))).thenReturn(user);
        when(userMapper.toRegistrationResponse(userInfo)).thenReturn(response);

        // Act
        RegistrationResponse result = userService.register(request);

        // Assert
        verify(userRepository).findByEmail("test@example.com");
        verify(roleRepository).findRoleByName("USER");
        verify(userMapper).toUserInfo(request);
        verify(userInfoRepository).save(any(UserInfo.class));
        verify(userMapper).toUser(request);
        verify(passwordEncoder).encode("password123");
        verify(userRepository).save(any(User.class));
        verify(userMapper).toRegistrationResponse(userInfo);
        assertEquals(response.getEmail(), result.getEmail());
        assertEquals(response.getFirstName(), result.getFirstName());
        assertEquals(response.getLastName(), result.getLastName());
    }

    @Test
    void register_EmailAlreadyExists_ThrowsEmailAlreadyExistsException() {
        // Arrange
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));

        // Act & Assert
        CustomException exception = assertThrows(CustomException.class, () -> userService.register(request));
        assertEquals(ResponseCode.EMAIL_ALREADY_EXISTS, exception.getResponseCode());
        verify(userRepository).findByEmail("test@example.com");
        verify(roleRepository, never()).findRoleByName(anyString());
        verify(userMapper, never()).toUserInfo(any());
        verify(userInfoRepository, never()).save(any());
        verify(userMapper, never()).toUser(any());
        verify(passwordEncoder, never()).encode(anyString());
        verify(userRepository, never()).save(any());
        verify(userMapper, never()).toRegistrationResponse(any());
    }

    @Test
    void register_RoleNotFound_ThrowsRoleNotFoundException() {
        // Arrange
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.empty());
        when(roleRepository.findRoleByName("USER")).thenReturn(Optional.empty());

        // Act & Assert
        CustomException exception = assertThrows(CustomException.class, () -> userService.register(request));
        assertEquals(ResponseCode.ROLE_NOT_FOUND, exception.getResponseCode());
        verify(userRepository).findByEmail("test@example.com");
        verify(roleRepository).findRoleByName("USER");
        verify(userMapper, never()).toUserInfo(any());
        verify(userInfoRepository, never()).save(any());
        verify(userMapper, never()).toUser(any());
        verify(passwordEncoder, never()).encode(anyString());
        verify(userRepository, never()).save(any());
        verify(userMapper, never()).toRegistrationResponse(any());
    }

    @Test
    void register_NullUserInfo_ThrowsInternalServerError() {
        // Arrange
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.empty());
        when(roleRepository.findRoleByName("USER")).thenReturn(Optional.of(role));
        when(userMapper.toUserInfo(request)).thenReturn(null);

        // Act & Assert
        CustomException exception = assertThrows(CustomException.class, () -> userService.register(request));
        assertEquals(ResponseCode.INTERNAL_SERVER_ERROR, exception.getResponseCode());
        verify(userRepository).findByEmail("test@example.com");
        verify(roleRepository).findRoleByName("USER");
        verify(userMapper).toUserInfo(request);
        verify(userInfoRepository, never()).save(any());
        verify(userMapper, never()).toUser(any());
        verify(passwordEncoder, never()).encode(anyString());
        verify(userRepository, never()).save(any());
        verify(userMapper, never()).toRegistrationResponse(any());
    }

    @Test
    void register_NullUser_ThrowsInternalServerError() {
        // Arrange
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.empty());
        when(roleRepository.findRoleByName("USER")).thenReturn(Optional.of(role));
        when(userMapper.toUserInfo(request)).thenReturn(userInfo);
        when(userInfoRepository.save(any(UserInfo.class))).thenReturn(userInfo);
        when(userMapper.toUser(request)).thenReturn(null);

        // Act & Assert
        CustomException exception = assertThrows(CustomException.class, () -> userService.register(request));
        assertEquals(ResponseCode.INTERNAL_SERVER_ERROR, exception.getResponseCode());
        verify(userRepository).findByEmail("test@example.com");
        verify(roleRepository).findRoleByName("USER");
        verify(userMapper).toUserInfo(request);
        verify(userInfoRepository).save(any(UserInfo.class));
        verify(userMapper).toUser(request);
        verify(passwordEncoder, never()).encode(anyString());
        verify(userRepository, never()).save(any());
        verify(userMapper, never()).toRegistrationResponse(any());
    }

    @Test
    void register_PasswordEncoderThrowsException_ThrowsInternalServerError() {
        // Arrange
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.empty());
        when(roleRepository.findRoleByName("USER")).thenReturn(Optional.of(role));
        when(userMapper.toUserInfo(request)).thenReturn(userInfo);
        when(userInfoRepository.save(any(UserInfo.class))).thenReturn(userInfo);
        when(userMapper.toUser(request)).thenReturn(user);
        when(passwordEncoder.encode("password123")).thenThrow(new RuntimeException("Password encoding failed"));

        // Act & Assert
        CustomException exception = assertThrows(CustomException.class, () -> userService.register(request));
        assertEquals(ResponseCode.INTERNAL_SERVER_ERROR, exception.getResponseCode());
        verify(userRepository).findByEmail("test@example.com");
        verify(roleRepository).findRoleByName("USER");
        verify(userMapper).toUserInfo(request);
        verify(userInfoRepository).save(any(UserInfo.class));
        verify(userMapper).toUser(request);
        verify(passwordEncoder).encode("password123");
        verify(userRepository, never()).save(any());
        verify(userMapper, never()).toRegistrationResponse(any());
    }

    @Test
    void register_UserInfoRepositorySaveThrowsException_ThrowsInternalServerError() {
        // Arrange
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.empty());
        when(roleRepository.findRoleByName("USER")).thenReturn(Optional.of(role));
        when(userMapper.toUserInfo(request)).thenReturn(userInfo);
        when(userInfoRepository.save(any(UserInfo.class))).thenThrow(new RuntimeException("Failed to save UserInfo"));

        // Act & Assert
        CustomException exception = assertThrows(CustomException.class, () -> userService.register(request));
        assertEquals(ResponseCode.INTERNAL_SERVER_ERROR, exception.getResponseCode());
        verify(userRepository).findByEmail("test@example.com");
        verify(roleRepository).findRoleByName("USER");
        verify(userMapper).toUserInfo(request);
        verify(userInfoRepository).save(any(UserInfo.class));
        verify(userMapper, never()).toUser(any());
        verify(passwordEncoder, never()).encode(anyString());
        verify(userRepository, never()).save(any());
        verify(userMapper, never()).toRegistrationResponse(any());
    }

    @Test
    void register_UserRepositorySaveThrowsException_ThrowsInternalServerError() {
        // Arrange
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.empty());
        when(roleRepository.findRoleByName("USER")).thenReturn(Optional.of(role));
        when(userMapper.toUserInfo(request)).thenReturn(userInfo);
        when(userInfoRepository.save(any(UserInfo.class))).thenReturn(userInfo);
        when(userMapper.toUser(request)).thenReturn(user);
        when(passwordEncoder.encode("password123")).thenReturn("encodedPassword");
        when(userRepository.save(any(User.class))).thenThrow(new RuntimeException("Failed to save User"));

        // Act & Assert
        CustomException exception = assertThrows(CustomException.class, () -> userService.register(request));
        assertEquals(ResponseCode.INTERNAL_SERVER_ERROR, exception.getResponseCode());
        verify(userRepository).findByEmail("test@example.com");
        verify(roleRepository).findRoleByName("USER");
        verify(userMapper).toUserInfo(request);
        verify(userInfoRepository).save(any(UserInfo.class));
        verify(userMapper).toUser(request);
        verify(passwordEncoder).encode("password123");
        verify(userRepository).save(any(User.class));
        verify(userMapper, never()).toRegistrationResponse(any());
    }


    @Test
    void register_SendValidationEmailThrowsMessagingException_ThrowsInternalServerError() throws MessagingException {
        // Arrange
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.empty());
        when(roleRepository.findRoleByName("USER")).thenReturn(Optional.of(role));
        when(userMapper.toUserInfo(request)).thenReturn(userInfo);
        when(userInfoRepository.save(any(UserInfo.class))).thenReturn(userInfo);
        when(userMapper.toUser(request)).thenReturn(user);
        when(passwordEncoder.encode("password123")).thenReturn("encodedPassword");
        when(userRepository.save(any(User.class))).thenReturn(user);

        // Set up activation URL value using reflection since it's injected via @Value
        ReflectionTestUtils.setField(userService, "activationUrl", "http://localhost:8080/activate");

        // Mock Redis and OTP repository
        doNothing().when(redisService).setValue(anyString(), any(), anyLong(), any());

        // Mock email service to throw exception - using proper Mockito syntax for void methods
        doThrow(new MessagingException("Failed to send email"))
                .when(emailService)
                .sendEmail(
                        anyString(),
                        anyString(),
                        any(EmailTemplateName.class),
                        anyString(),
                        anyString(),
                        anyString()
                );

        // Act & Assert
        CustomException exception = assertThrows(CustomException.class, () -> userService.register(request));
        assertEquals(ResponseCode.INTERNAL_SERVER_ERROR, exception.getResponseCode());

        // Verify all the method calls
        verify(userRepository).findByEmail("test@example.com");
        verify(roleRepository).findRoleByName("USER");
        verify(userMapper).toUserInfo(request);
        verify(userInfoRepository).save(any(UserInfo.class));
        verify(userMapper).toUser(request);
        verify(passwordEncoder).encode("password123");
        verify(userRepository).save(any(User.class));
        verify(redisService).setValue(anyString(), any(), anyLong(), any());
        verify(userOtpRepository).save(any(UserOtp.class));
        verify(userMapper, never()).toRegistrationResponse(any());
    }

}

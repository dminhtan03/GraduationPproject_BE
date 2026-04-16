package com.finalProject.BookingMeetingRoom.service.user;

import com.finalProject.BookingMeetingRoom.common.exception.CustomException;
import com.finalProject.BookingMeetingRoom.common.payload.ResponseCode;
import com.finalProject.BookingMeetingRoom.mapper.UserMapper;
import com.finalProject.BookingMeetingRoom.model.entity.Role;
import com.finalProject.BookingMeetingRoom.model.entity.User;
import com.finalProject.BookingMeetingRoom.model.entity.UserInfo;
import com.finalProject.BookingMeetingRoom.model.request.RegistrationRequest;
import com.finalProject.BookingMeetingRoom.model.response.RegistrationResponse;
import com.finalProject.BookingMeetingRoom.repository.RoleRepository;
import com.finalProject.BookingMeetingRoom.repository.UserInfoRepository;
import com.finalProject.BookingMeetingRoom.repository.UserOtpRepository;
import com.finalProject.BookingMeetingRoom.repository.UserRepository;
import com.finalProject.BookingMeetingRoom.service.EmailService;
import com.finalProject.BookingMeetingRoom.service.RedisService;
import com.finalProject.BookingMeetingRoom.service.impl.UserServiceImpl;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class UserServiceImplAdminAddUserTest {

    @Test
    void shouldCreateEnabledUserAndUserInfo_whenValidRequest() {
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        UserRepository userRepository = mock(UserRepository.class);
        UserInfoRepository userInfoRepository = mock(UserInfoRepository.class);
        EmailService emailService = mock(EmailService.class);
        UserOtpRepository userOtpRepository = mock(UserOtpRepository.class);
        RedisService redisService = mock(RedisService.class);
        RoleRepository roleRepository = mock(RoleRepository.class);
        UserMapper userMapper = mock(UserMapper.class);

        UserServiceImpl service = new UserServiceImpl(
                passwordEncoder,
                userRepository,
                userInfoRepository,
                emailService,
                userOtpRepository,
                redisService,
                roleRepository,
                userMapper
        );

        RegistrationRequest request = new RegistrationRequest();
        request.setFirstName("Nguyễn");
        request.setLastName("An");
        request.setPhoneNumber("+84 0912 345 678");
        request.setAddress("Hà Nội - Việt Nam");
        request.setDepartment("R&D / AI");
        request.setEmail("new.user_01+admin@example.com");
        request.setGender("Nam");
        request.setPassword("P@ssw0rd!2026");
        request.setRole("ADMIN");

        when(userRepository.findByEmail(request.getEmail())).thenReturn(Optional.empty());
        Role role = new Role();
        role.setId("R1");
        role.setName("ADMIN");
        when(roleRepository.findRoleByName(request.getRole())).thenReturn(Optional.of(role));

        UserInfo mappedInfo = new UserInfo();
        mappedInfo.setEmail(request.getEmail());
        mappedInfo.setFirstName(request.getFirstName());
        mappedInfo.setLastName(request.getLastName());
        mappedInfo.setPhoneNumber(request.getPhoneNumber());
        mappedInfo.setAddress(request.getAddress());
        mappedInfo.setDepartment(request.getDepartment());
        mappedInfo.setGender(request.getGender());
        when(userMapper.toUserInfo(request)).thenReturn(mappedInfo);

        when(userMapper.toUser(request)).thenReturn(new User());
        when(passwordEncoder.encode(request.getPassword())).thenReturn("ENC(P@ssw0rd!2026)");

        RegistrationResponse response = new RegistrationResponse();
        response.setEmail(request.getEmail());
        when(userMapper.toRegistrationResponse(any(UserInfo.class))).thenReturn(response);

        RegistrationResponse res = service.adminAddUser(request);
        assertNotNull(res);
        assertEquals("new.user_01+admin@example.com", res.getEmail());

        verify(userInfoRepository).save(any(UserInfo.class));

        ArgumentCaptor<User> savedUserCap = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(savedUserCap.capture());
        User savedUser = savedUserCap.getValue();
        assertTrue(savedUser.isEnabled());
        assertFalse(savedUser.isLocked());
        assertFalse(savedUser.isReset());
        assertNotNull(savedUser.getUserInfo());
        assertEquals("ENC(P@ssw0rd!2026)", savedUser.getPassword());
        assertNotNull(savedUser.getRoles());
        assertEquals(1, savedUser.getRoles().size());
    }

    @Test
    void shouldThrowEmailAlreadyExists_whenEmailPresent() {
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        UserRepository userRepository = mock(UserRepository.class);
        UserInfoRepository userInfoRepository = mock(UserInfoRepository.class);
        EmailService emailService = mock(EmailService.class);
        UserOtpRepository userOtpRepository = mock(UserOtpRepository.class);
        RedisService redisService = mock(RedisService.class);
        RoleRepository roleRepository = mock(RoleRepository.class);
        UserMapper userMapper = mock(UserMapper.class);

        UserServiceImpl service = new UserServiceImpl(
                passwordEncoder,
                userRepository,
                userInfoRepository,
                emailService,
                userOtpRepository,
                redisService,
                roleRepository,
                userMapper
        );

        RegistrationRequest request = new RegistrationRequest();
        request.setEmail("Existing.User@example.com");
        request.setRole("ADMIN");

        when(userRepository.findByEmail(request.getEmail())).thenReturn(Optional.of(new User()));

        CustomException ex = assertThrows(CustomException.class, () -> service.adminAddUser(request));
        assertEquals(ResponseCode.EMAIL_ALREADY_EXISTS, ex.getResponseCode());
    }

    @Test
    void shouldThrowRoleNotFound_whenRoleMissing() {
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        UserRepository userRepository = mock(UserRepository.class);
        UserInfoRepository userInfoRepository = mock(UserInfoRepository.class);
        EmailService emailService = mock(EmailService.class);
        UserOtpRepository userOtpRepository = mock(UserOtpRepository.class);
        RedisService redisService = mock(RedisService.class);
        RoleRepository roleRepository = mock(RoleRepository.class);
        UserMapper userMapper = mock(UserMapper.class);

        UserServiceImpl service = new UserServiceImpl(
                passwordEncoder,
                userRepository,
                userInfoRepository,
                emailService,
                userOtpRepository,
                redisService,
                roleRepository,
                userMapper
        );

        RegistrationRequest request = new RegistrationRequest();
        request.setEmail("new@example.com");
        request.setRole("UNKNOWN");

        when(userRepository.findByEmail(request.getEmail())).thenReturn(Optional.empty());
        when(roleRepository.findRoleByName(request.getRole())).thenReturn(Optional.empty());

        CustomException ex = assertThrows(CustomException.class, () -> service.adminAddUser(request));
        assertEquals(ResponseCode.ROLE_NOT_FOUND, ex.getResponseCode());
    }

    @Test
    void shouldThrowInternalServerError_whenUserInfoMappingFails() {
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        UserRepository userRepository = mock(UserRepository.class);
        UserInfoRepository userInfoRepository = mock(UserInfoRepository.class);
        EmailService emailService = mock(EmailService.class);
        UserOtpRepository userOtpRepository = mock(UserOtpRepository.class);
        RedisService redisService = mock(RedisService.class);
        RoleRepository roleRepository = mock(RoleRepository.class);
        UserMapper userMapper = mock(UserMapper.class);

        UserServiceImpl service = new UserServiceImpl(
                passwordEncoder,
                userRepository,
                userInfoRepository,
                emailService,
                userOtpRepository,
                redisService,
                roleRepository,
                userMapper
        );

        RegistrationRequest request = new RegistrationRequest();
        request.setEmail("new@example.com");
        request.setRole("ADMIN");

        when(userRepository.findByEmail(request.getEmail())).thenReturn(Optional.empty());
        when(roleRepository.findRoleByName(request.getRole())).thenReturn(Optional.of(new Role()));
        when(userMapper.toUserInfo(request)).thenReturn(null);

        CustomException ex = assertThrows(CustomException.class, () -> service.adminAddUser(request));
        assertEquals(ResponseCode.INTERNAL_SERVER_ERROR, ex.getResponseCode());
    }

    @Test
    void shouldThrowInternalServerError_whenUserMappingFails() {
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        UserRepository userRepository = mock(UserRepository.class);
        UserInfoRepository userInfoRepository = mock(UserInfoRepository.class);
        EmailService emailService = mock(EmailService.class);
        UserOtpRepository userOtpRepository = mock(UserOtpRepository.class);
        RedisService redisService = mock(RedisService.class);
        RoleRepository roleRepository = mock(RoleRepository.class);
        UserMapper userMapper = mock(UserMapper.class);

        UserServiceImpl service = new UserServiceImpl(
                passwordEncoder,
                userRepository,
                userInfoRepository,
                emailService,
                userOtpRepository,
                redisService,
                roleRepository,
                userMapper
        );

        RegistrationRequest request = new RegistrationRequest();
        request.setEmail("new@example.com");
        request.setRole("ADMIN");

        when(userRepository.findByEmail(request.getEmail())).thenReturn(Optional.empty());
        when(roleRepository.findRoleByName(request.getRole())).thenReturn(Optional.of(new Role()));
        when(userMapper.toUserInfo(request)).thenReturn(new UserInfo());
        when(userMapper.toUser(request)).thenReturn(null);

        CustomException ex = assertThrows(CustomException.class, () -> service.adminAddUser(request));
        assertEquals(ResponseCode.INTERNAL_SERVER_ERROR, ex.getResponseCode());
    }

    @Test
    void shouldThrowInternalServerError_whenDatabaseErrorOccurs() {
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        UserRepository userRepository = mock(UserRepository.class);
        UserInfoRepository userInfoRepository = mock(UserInfoRepository.class);
        EmailService emailService = mock(EmailService.class);
        UserOtpRepository userOtpRepository = mock(UserOtpRepository.class);
        RedisService redisService = mock(RedisService.class);
        RoleRepository roleRepository = mock(RoleRepository.class);
        UserMapper userMapper = mock(UserMapper.class);

        UserServiceImpl service = new UserServiceImpl(
                passwordEncoder,
                userRepository,
                userInfoRepository,
                emailService,
                userOtpRepository,
                redisService,
                roleRepository,
                userMapper
        );

        RegistrationRequest request = new RegistrationRequest();
        request.setEmail("invalid-email");
        request.setRole("ADMIN");

        when(userRepository.findByEmail(request.getEmail())).thenReturn(Optional.empty());
        when(roleRepository.findRoleByName(request.getRole())).thenReturn(Optional.of(new Role()));
        when(userMapper.toUserInfo(request)).thenReturn(new UserInfo());
        when(userMapper.toUser(request)).thenReturn(new User());

        // Simulate a generic database exception (Type B)
        when(userRepository.save(any(User.class))).thenThrow(new RuntimeException("Database connection failure"));

        CustomException ex = assertThrows(CustomException.class, () -> service.adminAddUser(request));
        assertEquals(ResponseCode.INTERNAL_SERVER_ERROR, ex.getResponseCode());
    }
}

package com.finalProject.BookingMeetingRoom.service.user;

import com.finalProject.BookingMeetingRoom.common.exception.CustomException;
import com.finalProject.BookingMeetingRoom.common.payload.ResponseCode;
import com.finalProject.BookingMeetingRoom.mapper.UserMapper;
import com.finalProject.BookingMeetingRoom.model.entity.Role;
import com.finalProject.BookingMeetingRoom.model.entity.User;
import com.finalProject.BookingMeetingRoom.model.entity.UserInfo;
import com.finalProject.BookingMeetingRoom.model.entity.UserOtp;
import com.finalProject.BookingMeetingRoom.model.request.ChangePasswordRequest;
import com.finalProject.BookingMeetingRoom.model.request.ForgotPasswordRequest;
import com.finalProject.BookingMeetingRoom.model.request.ForgotPasswordVerifyRequest;
import com.finalProject.BookingMeetingRoom.model.request.RegistrationRequest;
import com.finalProject.BookingMeetingRoom.model.request.ResendOtpRequest;
import com.finalProject.BookingMeetingRoom.model.request.UpdateUserInfoRequest;
import com.finalProject.BookingMeetingRoom.model.response.RegistrationResponse;
import com.finalProject.BookingMeetingRoom.repository.RoleRepository;
import com.finalProject.BookingMeetingRoom.repository.UserInfoRepository;
import com.finalProject.BookingMeetingRoom.repository.UserOtpRepository;
import com.finalProject.BookingMeetingRoom.repository.UserRepository;
import com.finalProject.BookingMeetingRoom.service.EmailService;
import com.finalProject.BookingMeetingRoom.service.RedisService;
import com.finalProject.BookingMeetingRoom.service.impl.UserServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CreationHelper;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserInfoRepository userInfoRepository;

    @Mock
    private EmailService emailService;

    @Mock
    private UserOtpRepository userOtpRepository;

    @Mock
    private RedisService redisService;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private UserMapper userMapper;

    @Mock
    private Authentication authentication;

    @Spy
    @InjectMocks
    private UserServiceImpl userService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(userService, "character", "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789");
        ReflectionTestUtils.setField(userService, "activationUrl", "http://localhost:3000/activate");
    }

    @Test
    void adminAddUser_shouldCreateEnabledUser() {
        RegistrationRequest request = new RegistrationRequest();
        request.setEmail("new.user@example.com");
        request.setPassword("P@ssw0rd");
        request.setRole("ADMIN");

        Role role = new Role();
        role.setName("ADMIN");

        UserInfo mappedInfo = new UserInfo();
        mappedInfo.setEmail(request.getEmail());

        User mappedUser = new User();

        RegistrationResponse response = new RegistrationResponse();
        response.setEmail(request.getEmail());

        when(userRepository.findByEmail(request.getEmail())).thenReturn(Optional.empty());
        when(roleRepository.findRoleByName("ADMIN")).thenReturn(Optional.of(role));
        when(userMapper.toUserInfo(request)).thenReturn(mappedInfo);
        when(userMapper.toUser(request)).thenReturn(mappedUser);
        when(passwordEncoder.encode("P@ssw0rd")).thenReturn("ENC_PASS");
        when(userMapper.toRegistrationResponse(mappedInfo)).thenReturn(response);

        RegistrationResponse result = userService.adminAddUser(request);

        assertEquals("new.user@example.com", result.getEmail());

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());

        User saved = userCaptor.getValue();
        assertTrue(saved.isEnabled());
        assertFalse(saved.isLocked());
        assertFalse(saved.isReset());
        assertEquals("ENC_PASS", saved.getPassword());
        assertNotNull(saved.getRoles());
        assertEquals(1, saved.getRoles().size());
        assertEquals("ADMIN", saved.getRoles().stream().findFirst().orElseThrow().getName());
    }

    @Test
    void adminAddUser_shouldThrowEmailAlreadyExists_whenEmailUsed() {
        RegistrationRequest request = new RegistrationRequest();
        request.setEmail("exists@example.com");

        when(userRepository.findByEmail("exists@example.com")).thenReturn(Optional.of(new User()));

        CustomException ex = assertThrows(CustomException.class, () -> userService.adminAddUser(request));

        assertEquals(ResponseCode.EMAIL_ALREADY_EXISTS, ex.getResponseCode());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void updateUserInfo_shouldPersistPhoneAndAddress() {
        UserInfo info = new UserInfo();
        info.setPhoneNumber("000");
        info.setAddress("old");

        User principal = new User();
        principal.setUserInfo(info);

        UpdateUserInfoRequest request = new UpdateUserInfoRequest();
        request.setPhoneNumber("0123456789");
        request.setAddress("new address");

        when(authentication.getPrincipal()).thenReturn(principal);

        userService.updateUserInfo(request, authentication);

        assertEquals("0123456789", info.getPhoneNumber());
        assertEquals("new address", info.getAddress());
        verify(userInfoRepository).save(info);
    }

    @Test
    void updateUserInfo_shouldThrowUserNotFound_whenUserInfoNull() {
        User principal = new User();
        principal.setUserInfo(null);
        when(authentication.getPrincipal()).thenReturn(principal);

        UpdateUserInfoRequest request = new UpdateUserInfoRequest();
        request.setPhoneNumber("0123");
        request.setAddress("x");

        CustomException ex = assertThrows(CustomException.class,
                () -> userService.updateUserInfo(request, authentication));

        assertEquals(ResponseCode.USER_NOT_FOUND, ex.getResponseCode());
    }

    @Test
    void generateAndActivateCode_shouldStoreOtpInRedisAndDatabase() {
        UserInfo info = new UserInfo();
        info.setEmail("otp@test.com");

        User user = new User();
        user.setUserInfo(info);

        String code = userService.generateAndActivateCode(user);

        assertNotNull(code);
        assertEquals(6, code.length());
        verify(redisService).setValue(eq("otp:otp@test.com"), eq(code), eq(1L), eq(TimeUnit.MINUTES));
        verify(userOtpRepository).save(any(UserOtp.class));
    }

    @Test
    void verifyForgotPassword_shouldResetPassword_whenRedisOtpMatches() {
        UserInfo info = new UserInfo();
        info.setEmail("user@test.com");

        User user = new User();
        user.setUserInfo(info);
        user.setLocked(true);

        ForgotPasswordVerifyRequest request = new ForgotPasswordVerifyRequest();
        request.setEmail("user@test.com");
        request.setOtp("654321");

        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));
        when(redisService.getValue("otp:user@test.com")).thenReturn("654321");
        doReturn("RANDOM12").when(userService).generateRandomPassword();
        when(passwordEncoder.encode("RANDOM12")).thenReturn("ENC_RANDOM");
        doNothing().when(userService).sendChangePasswordEmail(user, "RANDOM12");

        userService.verifyForgotPassword(request);

        assertEquals("ENC_RANDOM", user.getPassword());
        assertFalse(user.isLocked());
        assertTrue(user.isReset());
        verify(redisService).delete("otp:user@test.com");
        verify(userRepository).save(user);
    }

    @Test
    void verifyForgotPassword_shouldThrowInvalidOtp_whenRedisOtpMismatched() {
        UserInfo info = new UserInfo();
        info.setEmail("user@test.com");

        User user = new User();
        user.setUserInfo(info);

        ForgotPasswordVerifyRequest request = new ForgotPasswordVerifyRequest();
        request.setEmail("user@test.com");
        request.setOtp("111111");

        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));
        when(redisService.getValue("otp:user@test.com")).thenReturn("222222");

        CustomException ex = assertThrows(CustomException.class,
                () -> userService.verifyForgotPassword(request));

        assertEquals(ResponseCode.INVALID_OTP, ex.getResponseCode());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void activateAccount_shouldThrowExpiredOtp_whenOtpExpired() {
        UserOtp otp = new UserOtp();
        otp.setOtpCode("123456");
        otp.setExpiresAt(LocalDateTime.now().minusMinutes(1));

        when(userOtpRepository.findByOtpCode("123456")).thenReturn(Optional.of(otp));

        CustomException ex = assertThrows(CustomException.class, () -> userService.activateAccount("123456"));

        assertEquals(ResponseCode.EXPIRED_OTP, ex.getResponseCode());
    }

    @Test
    void resendOtp_shouldThrowUserNotFound_whenEmailUnknown() {
        ResendOtpRequest request = new ResendOtpRequest();
        request.setEmail("missing@test.com");

        when(userRepository.findByEmail("missing@test.com")).thenReturn(Optional.empty());

        CustomException ex = assertThrows(CustomException.class, () -> userService.resendOtp(request));

        assertEquals(ResponseCode.USER_NOT_FOUND, ex.getResponseCode());
    }

    @Test
    void resendOtp_shouldSendValidationEmail_whenUserExists() throws Exception {
        ResendOtpRequest request = new ResendOtpRequest();
        request.setEmail("ok@test.com");

        User user = new User();
        UserInfo info = new UserInfo();
        info.setEmail("ok@test.com");
        user.setUserInfo(info);

        when(userRepository.findByEmail("ok@test.com")).thenReturn(Optional.of(user));
        doNothing().when(userService).sendValidationEmail(user);

        userService.resendOtp(request);

        verify(userService).sendValidationEmail(user);
    }

    @Test
    void register_shouldCreateDisabledUserAndSendValidationEmail() throws Exception {
        RegistrationRequest request = new RegistrationRequest();
        request.setEmail("reg@test.com");
        request.setPassword("Pass123");
        request.setRole("USER");

        Role role = new Role();
        role.setName("USER");

        UserInfo mappedInfo = new UserInfo();
        mappedInfo.setEmail("reg@test.com");

        User mappedUser = new User();
        RegistrationResponse mappedResponse = new RegistrationResponse();

        when(userRepository.findByEmail("reg@test.com")).thenReturn(Optional.empty());
        when(roleRepository.findRoleByName("USER")).thenReturn(Optional.of(role));
        when(userMapper.toUserInfo(request)).thenReturn(mappedInfo);
        when(userMapper.toUser(request)).thenReturn(mappedUser);
        when(passwordEncoder.encode("Pass123")).thenReturn("ENC123");
        doNothing().when(userService).sendValidationEmail(mappedUser);
        when(userMapper.toRegistrationResponse(mappedInfo)).thenReturn(mappedResponse);

        RegistrationResponse actual = userService.register(request);

        assertEquals(mappedResponse, actual);
        assertFalse(mappedUser.isEnabled());
        assertFalse(mappedUser.isLocked());
        assertFalse(mappedUser.isReset());
        assertEquals("ENC123", mappedUser.getPassword());
        verify(userRepository).save(mappedUser);
        verify(userService).sendValidationEmail(mappedUser);
    }

    @Test
    void register_shouldThrowRoleNotFound_whenRoleMissing() {
        RegistrationRequest request = new RegistrationRequest();
        request.setEmail("reg@test.com");
        request.setRole("MISSING_ROLE");

        when(userRepository.findByEmail("reg@test.com")).thenReturn(Optional.empty());
        when(roleRepository.findRoleByName("MISSING_ROLE")).thenReturn(Optional.empty());

        CustomException ex = assertThrows(CustomException.class, () -> userService.register(request));

        assertEquals(ResponseCode.ROLE_NOT_FOUND, ex.getResponseCode());
    }

    @Test
    void register_shouldThrowEmailAlreadyExists_whenEmailUsed() {
        RegistrationRequest request = new RegistrationRequest();
        request.setEmail("exists@test.com");

        when(userRepository.findByEmail("exists@test.com")).thenReturn(Optional.of(new User()));

        CustomException ex = assertThrows(CustomException.class, () -> userService.register(request));

        assertEquals(ResponseCode.EMAIL_ALREADY_EXISTS, ex.getResponseCode());
    }

    @Test
    void register_shouldThrowInternalServerError_whenMapperReturnsNullUserInfo() {
        RegistrationRequest request = new RegistrationRequest();
        request.setEmail("reg@test.com");
        request.setRole("USER");

        Role role = new Role();
        role.setName("USER");

        when(userRepository.findByEmail("reg@test.com")).thenReturn(Optional.empty());
        when(roleRepository.findRoleByName("USER")).thenReturn(Optional.of(role));
        when(userMapper.toUserInfo(request)).thenReturn(null);

        CustomException ex = assertThrows(CustomException.class, () -> userService.register(request));

        assertEquals(ResponseCode.INTERNAL_SERVER_ERROR, ex.getResponseCode());
    }

    @Test
    void register_shouldThrowInternalServerError_whenSendValidationEmailFails() throws Exception {
        RegistrationRequest request = new RegistrationRequest();
        request.setEmail("reg@test.com");
        request.setPassword("Pass123");
        request.setRole("USER");

        Role role = new Role();
        role.setName("USER");

        UserInfo mappedInfo = new UserInfo();
        mappedInfo.setEmail("reg@test.com");

        User mappedUser = new User();

        when(userRepository.findByEmail("reg@test.com")).thenReturn(Optional.empty());
        when(roleRepository.findRoleByName("USER")).thenReturn(Optional.of(role));
        when(userMapper.toUserInfo(request)).thenReturn(mappedInfo);
        when(userMapper.toUser(request)).thenReturn(mappedUser);
        when(passwordEncoder.encode("Pass123")).thenReturn("ENC123");
        doThrow(new RuntimeException("mail fail")).when(userService).sendValidationEmail(mappedUser);

        CustomException ex = assertThrows(CustomException.class, () -> userService.register(request));

        assertEquals(ResponseCode.INTERNAL_SERVER_ERROR, ex.getResponseCode());
    }

    @Test
    void changePassword_shouldThrowInvalidCurrentPassword_whenCurrentPasswordWrong() {
        User principal = new User();
        principal.setPassword("hashed-current");

        UserInfo userInfo = new UserInfo();
        userInfo.setEmail("u@test.com");
        userInfo.setFirstName("U");
        userInfo.setLastName("Test");
        principal.setUserInfo(userInfo);

        ChangePasswordRequest request = new ChangePasswordRequest();
        request.setCurrentPassword("wrong");
        request.setNewPassword("new");
        request.setConfirmPassword("new");

        when(authentication.getPrincipal()).thenReturn(principal);
        when(passwordEncoder.matches("wrong", "hashed-current")).thenReturn(false);

        CustomException ex = assertThrows(CustomException.class,
                () -> userService.changePassword(request, authentication));

        assertEquals(ResponseCode.INVALID_CURRENT_PASSWORD, ex.getResponseCode());
    }

    @Test
    void changePassword_shouldThrowPasswordConfirmNotMatch_whenConfirmInvalid() {
        User principal = new User();
        principal.setPassword("hashed-current");

        ChangePasswordRequest request = new ChangePasswordRequest();
        request.setCurrentPassword("old");
        request.setNewPassword("new1");
        request.setConfirmPassword("new2");

        when(authentication.getPrincipal()).thenReturn(principal);
        when(passwordEncoder.matches("old", "hashed-current")).thenReturn(true);

        CustomException ex = assertThrows(CustomException.class,
                () -> userService.changePassword(request, authentication));

        assertEquals(ResponseCode.PASSWORD_CONFIRM_NOT_MATCH, ex.getResponseCode());
    }

    @Test
    void changePassword_shouldSaveAndSendEmail_whenInputValid() throws Exception {
        UserInfo info = new UserInfo();
        info.setEmail("u@test.com");
        info.setFirstName("User");
        info.setLastName("Test");

        User principal = new User();
        principal.setPassword("hashed-current");
        principal.setReset(true);
        principal.setUserInfo(info);

        ChangePasswordRequest request = new ChangePasswordRequest();
        request.setCurrentPassword("old");
        request.setNewPassword("new-pass");
        request.setConfirmPassword("new-pass");

        when(authentication.getPrincipal()).thenReturn(principal);
        when(passwordEncoder.matches("old", "hashed-current")).thenReturn(true);
        when(passwordEncoder.encode("new-pass")).thenReturn("hashed-new");

        userService.changePassword(request, authentication);

        assertEquals("hashed-new", principal.getPassword());
        assertFalse(principal.isReset());
        verify(userRepository).save(principal);
        verify(emailService).sendEmail(eq("u@test.com"), eq("User Test"), any(), anyString(), anyString(), eq("Change password"));
    }

    @Test
    void handleForgotPassword_shouldThrowUserNotFound_whenEmailMissing() {
        ForgotPasswordRequest request = new ForgotPasswordRequest();
        request.setEmail("missing@test.com");

        when(userRepository.findByEmail("missing@test.com")).thenReturn(Optional.empty());

        CustomException ex = assertThrows(CustomException.class, () -> userService.handleForgotPassword(request));

        assertEquals(ResponseCode.USER_NOT_FOUND, ex.getResponseCode());
    }

    @Test
    void handleForgotPassword_shouldThrowInternalServerError_whenUserInfoMissing() {
        ForgotPasswordRequest request = new ForgotPasswordRequest();
        request.setEmail("x@test.com");

        User user = new User();
        user.setUserInfo(null);

        when(userRepository.findByEmail("x@test.com")).thenReturn(Optional.of(user));

        CustomException ex = assertThrows(CustomException.class, () -> userService.handleForgotPassword(request));

        assertEquals(ResponseCode.INTERNAL_SERVER_ERROR, ex.getResponseCode());
    }

    @Test
    void verifyForgotPassword_shouldResetPassword_whenOtpComesFromDatabase() {
        UserInfo info = new UserInfo();
        info.setEmail("db@test.com");
        info.setFirstName("Db");
        info.setLastName("User");

        User user = new User();
        user.setUserInfo(info);

        UserOtp otp = new UserOtp();
        otp.setOtpCode("123456");
        otp.setUsed(false);
        otp.setExpiresAt(LocalDateTime.now().plusMinutes(1));

        ForgotPasswordVerifyRequest request = new ForgotPasswordVerifyRequest();
        request.setEmail("db@test.com");
        request.setOtp("123456");

        when(userRepository.findByEmail("db@test.com")).thenReturn(Optional.of(user));
        when(redisService.getValue("otp:db@test.com")).thenReturn(null);
        when(userOtpRepository.findValidOtp("db@test.com", "otp:db@test.com")).thenReturn(Optional.of(otp));
        doReturn("RANDOM12").when(userService).generateRandomPassword();
        when(passwordEncoder.encode("RANDOM12")).thenReturn("ENC_RANDOM");
        doNothing().when(userService).sendChangePasswordEmail(user, "RANDOM12");

        userService.verifyForgotPassword(request);

        assertTrue(otp.isUsed());
        assertNotNull(otp.getValidatedAt());
        verify(userOtpRepository).save(otp);
        verify(userRepository).save(user);
    }

    @Test
    void verifyForgotPassword_shouldThrowOtpAlreadyUsed_whenDbOtpUsed() {
        UserInfo info = new UserInfo();
        info.setEmail("db@test.com");

        User user = new User();
        user.setUserInfo(info);

        UserOtp otp = new UserOtp();
        otp.setUsed(true);
        otp.setExpiresAt(LocalDateTime.now().plusMinutes(1));

        ForgotPasswordVerifyRequest request = new ForgotPasswordVerifyRequest();
        request.setEmail("db@test.com");
        request.setOtp("123456");

        when(userRepository.findByEmail("db@test.com")).thenReturn(Optional.of(user));
        when(redisService.getValue("otp:db@test.com")).thenReturn(null);
        when(userOtpRepository.findValidOtp("db@test.com", "otp:db@test.com")).thenReturn(Optional.of(otp));

        CustomException ex = assertThrows(CustomException.class, () -> userService.verifyForgotPassword(request));

        assertEquals(ResponseCode.OTP_ALREADY_USED, ex.getResponseCode());
    }

    @Test
    void verifyForgotPassword_shouldThrowUserNotFound_whenEmailUnknown() {
        ForgotPasswordVerifyRequest request = new ForgotPasswordVerifyRequest();
        request.setEmail("missing@test.com");
        request.setOtp("123456");

        when(userRepository.findByEmail("missing@test.com")).thenReturn(Optional.empty());

        CustomException ex = assertThrows(CustomException.class, () -> userService.verifyForgotPassword(request));

        assertEquals(ResponseCode.USER_NOT_FOUND, ex.getResponseCode());
    }

    @Test
    void sendChangePasswordEmail_shouldThrowInternalServerError_whenEmailFails() throws Exception {
        UserInfo info = new UserInfo();
        info.setEmail("u@test.com");
        info.setFirstName("User");
        info.setLastName("Test");

        User user = new User();
        user.setUserInfo(info);

        doThrow(new RuntimeException("mail")).when(emailService)
                .sendEmail(anyString(), anyString(), any(), anyString(), anyString(), anyString());

        CustomException ex = assertThrows(CustomException.class,
                () -> userService.sendChangePasswordEmail(user, "new-pass"));

        assertEquals(ResponseCode.INTERNAL_SERVER_ERROR, ex.getResponseCode());
    }

    @Test
    void activateAccount_shouldThrowInvalidOtp_whenOtpMissing() {
        when(userOtpRepository.findByOtpCode("bad")).thenReturn(Optional.empty());

        CustomException ex = assertThrows(CustomException.class, () -> userService.activateAccount("bad"));

        assertEquals(ResponseCode.INVALID_OTP, ex.getResponseCode());
    }

    @Test
    void activateAccount_shouldThrowUserNotFound_whenOtpHasNoUser() {
        UserOtp otp = new UserOtp();
        otp.setOtpCode("123456");
        otp.setExpiresAt(LocalDateTime.now().plusMinutes(1));
        otp.setUser(null);

        when(userOtpRepository.findByOtpCode("123456")).thenReturn(Optional.of(otp));

        CustomException ex = assertThrows(CustomException.class, () -> userService.activateAccount("123456"));

        assertEquals(ResponseCode.USER_NOT_FOUND, ex.getResponseCode());
    }

    @Test
    void activateAccount_shouldEnableUserAndMarkOtpUsed_whenValid() {
        User user = new User();
        user.setEnabled(false);

        UserOtp otp = new UserOtp();
        otp.setOtpCode("123456");
        otp.setExpiresAt(LocalDateTime.now().plusMinutes(1));
        otp.setUser(user);

        when(userOtpRepository.findByOtpCode("123456")).thenReturn(Optional.of(otp));

        userService.activateAccount("123456");

        assertTrue(user.isEnabled());
        assertTrue(otp.isUsed());
        assertNotNull(otp.getValidatedAt());
        verify(userRepository).save(user);
        verify(userOtpRepository).save(otp);
    }

    @Test
    void generateRandomPassword_shouldReturnEightCharactersFromConfiguredCharset() {
        String result = userService.generateRandomPassword();

        assertEquals(8, result.length());
        assertTrue(result.matches("[A-Z0-9]{8}"));
    }

    @Test
    void generateActivationCode_shouldReturnSixDigits() {
        String code = userService.generateActivationCode();

        assertEquals(6, code.length());
        assertTrue(code.matches("\\d{6}"));
    }

    @Test
    void sendValidationEmail_shouldSendOtpEmail() throws Exception {
        UserInfo info = new UserInfo();
        info.setEmail("u@test.com");
        info.setFirstName("User");
        info.setLastName("Test");

        User user = new User();
        user.setUserInfo(info);

        doReturn("654321").when(userService).generateAndActivateCode(user);

        userService.sendValidationEmail(user);

        verify(emailService).sendEmail(eq("u@test.com"), eq("User Test"), any(), anyString(), eq("654321"), eq("Activate your account"));
    }

    @Test
    void importUsersFromExcel_shouldSkipEmptyEmailAndImportValidRows() throws Exception {
        byte[] content = buildWorkbookBytes();
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "users.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                content
        );

        doReturn(new RegistrationResponse()).when(userService).adminAddUser(any(RegistrationRequest.class));

        userService.importUsersFromExcel(file);

        verify(userService).adminAddUser(any(RegistrationRequest.class));
    }

    @Test
    void importUsersFromExcel_shouldThrowValidationFailed_whenAnyRowInvalid() throws Exception {
        byte[] content = buildWorkbookBytes();
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "users.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                content
        );

        doThrow(new CustomException(ResponseCode.USER_NOT_FOUND, "user missing"))
                .when(userService).adminAddUser(any(RegistrationRequest.class));

        CustomException ex = assertThrows(CustomException.class, () -> userService.importUsersFromExcel(file));

        assertEquals(ResponseCode.VALIDATION_FAILED, ex.getResponseCode());
    }

    @Test
    void importUsersFromExcel_shouldThrowInternalServerError_whenCannotReadFile() throws Exception {
        org.springframework.web.multipart.MultipartFile file = org.mockito.Mockito.mock(org.springframework.web.multipart.MultipartFile.class);
        when(file.getInputStream()).thenThrow(new IOException("read fail"));

        CustomException ex = assertThrows(CustomException.class, () -> userService.importUsersFromExcel(file));

        assertEquals(ResponseCode.INTERNAL_SERVER_ERROR, ex.getResponseCode());
    }

    @Test
    void register_shouldThrowInternalServerError_whenMapperReturnsNullUser() {
        RegistrationRequest request = new RegistrationRequest();
        request.setEmail("reg2@test.com");
        request.setRole("USER");

        Role role = new Role();
        role.setName("USER");

        UserInfo mappedInfo = new UserInfo();

        when(userRepository.findByEmail("reg2@test.com")).thenReturn(Optional.empty());
        when(roleRepository.findRoleByName("USER")).thenReturn(Optional.of(role));
        when(userMapper.toUserInfo(request)).thenReturn(mappedInfo);
        when(userMapper.toUser(request)).thenReturn(null);

        CustomException ex = assertThrows(CustomException.class, () -> userService.register(request));

        assertEquals(ResponseCode.INTERNAL_SERVER_ERROR, ex.getResponseCode());
    }

    @Test
    void changePassword_shouldThrowInternalServerError_whenEmailServiceFails() throws Exception {
        UserInfo info = new UserInfo();
        info.setEmail("u@test.com");
        info.setFirstName("User");
        info.setLastName("Test");

        User principal = new User();
        principal.setPassword("hashed-current");
        principal.setUserInfo(info);

        ChangePasswordRequest request = new ChangePasswordRequest();
        request.setCurrentPassword("old");
        request.setNewPassword("new-pass");
        request.setConfirmPassword("new-pass");

        when(authentication.getPrincipal()).thenReturn(principal);
        when(passwordEncoder.matches("old", "hashed-current")).thenReturn(true);
        when(passwordEncoder.encode("new-pass")).thenReturn("hashed-new");
        doThrow(new RuntimeException("mail fail")).when(emailService)
                .sendEmail(anyString(), anyString(), any(), anyString(), anyString(), anyString());

        CustomException ex = assertThrows(CustomException.class,
                () -> userService.changePassword(request, authentication));

        assertEquals(ResponseCode.INTERNAL_SERVER_ERROR, ex.getResponseCode());
    }

    @Test
    void updateUserInfo_shouldThrowInternalServerError_whenAuthenticationFails() {
        UpdateUserInfoRequest request = new UpdateUserInfoRequest();
        request.setPhoneNumber("0123");
        request.setAddress("x");

        when(authentication.getPrincipal()).thenThrow(new RuntimeException("auth broken"));

        CustomException ex = assertThrows(CustomException.class,
                () -> userService.updateUserInfo(request, authentication));

        assertEquals(ResponseCode.INTERNAL_SERVER_ERROR, ex.getResponseCode());
    }

    @Test
    void handleForgotPassword_shouldThrowInternalServerError_whenEmailIsNull() {
        ForgotPasswordRequest request = new ForgotPasswordRequest();
        request.setEmail("x@test.com");

        UserInfo info = new UserInfo();
        info.setEmail(null);

        User user = new User();
        user.setUserInfo(info);

        when(userRepository.findByEmail("x@test.com")).thenReturn(Optional.of(user));

        CustomException ex = assertThrows(CustomException.class, () -> userService.handleForgotPassword(request));

        assertEquals(ResponseCode.INTERNAL_SERVER_ERROR, ex.getResponseCode());
    }

    @Test
    void handleForgotPassword_shouldThrowInternalServerError_whenSendValidationFails() throws Exception {
        ForgotPasswordRequest request = new ForgotPasswordRequest();
        request.setEmail("ok@test.com");

        UserInfo info = new UserInfo();
        info.setEmail("ok@test.com");

        User user = new User();
        user.setUserInfo(info);

        when(userRepository.findByEmail("ok@test.com")).thenReturn(Optional.of(user));
        doReturn("111111").when(userService).generateAndActivateCode(user);
        doThrow(new RuntimeException("mail fail")).when(userService).sendValidationEmail(user);

        CustomException ex = assertThrows(CustomException.class, () -> userService.handleForgotPassword(request));

        assertEquals(ResponseCode.INTERNAL_SERVER_ERROR, ex.getResponseCode());
    }

    @Test
    void handleForgotPassword_shouldStoreOtpAndSendValidationEmail_whenValid() throws Exception {
        ForgotPasswordRequest request = new ForgotPasswordRequest();
        request.setEmail("ok2@test.com");

        UserInfo info = new UserInfo();
        info.setEmail("ok2@test.com");

        User user = new User();
        user.setUserInfo(info);

        when(userRepository.findByEmail("ok2@test.com")).thenReturn(Optional.of(user));
        doReturn("123456").when(userService).generateAndActivateCode(user);
        doNothing().when(userService).sendValidationEmail(user);

        userService.handleForgotPassword(request);

        verify(redisService).setValue("otp:ok2@test.com", "123456", 5, TimeUnit.MINUTES);
        verify(userService).sendValidationEmail(user);
    }

    @Test
    void verifyForgotPassword_shouldThrowExpiredOtp_whenDbOtpExpired() {
        UserInfo info = new UserInfo();
        info.setEmail("db@test.com");

        User user = new User();
        user.setUserInfo(info);

        UserOtp otp = new UserOtp();
        otp.setUsed(false);
        otp.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        otp.setOtpCode("123456");

        ForgotPasswordVerifyRequest request = new ForgotPasswordVerifyRequest();
        request.setEmail("db@test.com");
        request.setOtp("123456");

        when(userRepository.findByEmail("db@test.com")).thenReturn(Optional.of(user));
        when(redisService.getValue("otp:db@test.com")).thenReturn(null);
        when(userOtpRepository.findValidOtp("db@test.com", "otp:db@test.com")).thenReturn(Optional.of(otp));

        CustomException ex = assertThrows(CustomException.class, () -> userService.verifyForgotPassword(request));

        assertEquals(ResponseCode.EXPIRED_OTP, ex.getResponseCode());
    }

    @Test
    void verifyForgotPassword_shouldThrowInvalidOtp_whenDbOtpNotFound() {
        UserInfo info = new UserInfo();
        info.setEmail("db@test.com");

        User user = new User();
        user.setUserInfo(info);

        ForgotPasswordVerifyRequest request = new ForgotPasswordVerifyRequest();
        request.setEmail("db@test.com");
        request.setOtp("123456");

        when(userRepository.findByEmail("db@test.com")).thenReturn(Optional.of(user));
        when(redisService.getValue("otp:db@test.com")).thenReturn(null);
        when(userOtpRepository.findValidOtp("db@test.com", "otp:db@test.com")).thenReturn(Optional.empty());

        CustomException ex = assertThrows(CustomException.class, () -> userService.verifyForgotPassword(request));

        assertEquals(ResponseCode.INVALID_OTP, ex.getResponseCode());
    }

    @Test
    void verifyForgotPassword_shouldThrowInvalidOtp_whenDbOtpCodeMismatch() {
        UserInfo info = new UserInfo();
        info.setEmail("db@test.com");

        User user = new User();
        user.setUserInfo(info);

        UserOtp otp = new UserOtp();
        otp.setUsed(false);
        otp.setExpiresAt(LocalDateTime.now().plusMinutes(1));
        otp.setOtpCode("999999");

        ForgotPasswordVerifyRequest request = new ForgotPasswordVerifyRequest();
        request.setEmail("db@test.com");
        request.setOtp("123456");

        when(userRepository.findByEmail("db@test.com")).thenReturn(Optional.of(user));
        when(redisService.getValue("otp:db@test.com")).thenReturn(null);
        when(userOtpRepository.findValidOtp("db@test.com", "otp:db@test.com")).thenReturn(Optional.of(otp));

        CustomException ex = assertThrows(CustomException.class, () -> userService.verifyForgotPassword(request));

        assertEquals(ResponseCode.INVALID_OTP, ex.getResponseCode());
    }

    @Test
    void verifyForgotPassword_shouldThrowInternalServerError_whenSendEmailFailsAfterReset() {
        UserInfo info = new UserInfo();
        info.setEmail("user@test.com");

        User user = new User();
        user.setUserInfo(info);

        ForgotPasswordVerifyRequest request = new ForgotPasswordVerifyRequest();
        request.setEmail("user@test.com");
        request.setOtp("654321");

        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));
        when(redisService.getValue("otp:user@test.com")).thenReturn("654321");
        doReturn("RANDOM12").when(userService).generateRandomPassword();
        when(passwordEncoder.encode("RANDOM12")).thenReturn("ENC_RANDOM");
        doThrow(new RuntimeException("mail fail")).when(userService).sendChangePasswordEmail(user, "RANDOM12");

        CustomException ex = assertThrows(CustomException.class, () -> userService.verifyForgotPassword(request));

        assertEquals(ResponseCode.INTERNAL_SERVER_ERROR, ex.getResponseCode());
    }

    @Test
    void activateAccount_shouldThrowInternalServerError_whenSavingUserFails() {
        User user = new User();

        UserOtp otp = new UserOtp();
        otp.setOtpCode("123456");
        otp.setExpiresAt(LocalDateTime.now().plusMinutes(1));
        otp.setUser(user);

        when(userOtpRepository.findByOtpCode("123456")).thenReturn(Optional.of(otp));
        when(userRepository.save(user)).thenThrow(new RuntimeException("db fail"));

        CustomException ex = assertThrows(CustomException.class, () -> userService.activateAccount("123456"));

        assertEquals(ResponseCode.INTERNAL_SERVER_ERROR, ex.getResponseCode());
    }

    @Test
    void resendOtp_shouldThrowInternalServerError_whenSendValidationFails() throws Exception {
        ResendOtpRequest request = new ResendOtpRequest();
        request.setEmail("ok@test.com");

        User user = new User();
        UserInfo info = new UserInfo();
        info.setEmail("ok@test.com");
        user.setUserInfo(info);

        when(userRepository.findByEmail("ok@test.com")).thenReturn(Optional.of(user));
        doThrow(new RuntimeException("mail fail")).when(userService).sendValidationEmail(user);

        CustomException ex = assertThrows(CustomException.class, () -> userService.resendOtp(request));

        assertEquals(ResponseCode.INTERNAL_SERVER_ERROR, ex.getResponseCode());
    }

    @Test
    void adminAddUser_shouldThrowRoleNotFound_whenRoleMissing() {
        RegistrationRequest request = new RegistrationRequest();
        request.setEmail("new.user@example.com");
        request.setRole("MISSING");

        when(userRepository.findByEmail("new.user@example.com")).thenReturn(Optional.empty());
        when(roleRepository.findRoleByName("MISSING")).thenReturn(Optional.empty());

        CustomException ex = assertThrows(CustomException.class, () -> userService.adminAddUser(request));

        assertEquals(ResponseCode.ROLE_NOT_FOUND, ex.getResponseCode());
    }

    @Test
    void adminAddUser_shouldThrowInternalServerError_whenUserMapperReturnsNull() {
        RegistrationRequest request = new RegistrationRequest();
        request.setEmail("new.user@example.com");
        request.setRole("USER");

        Role role = new Role();
        role.setName("USER");

        UserInfo mappedInfo = new UserInfo();

        when(userRepository.findByEmail("new.user@example.com")).thenReturn(Optional.empty());
        when(roleRepository.findRoleByName("USER")).thenReturn(Optional.of(role));
        when(userMapper.toUserInfo(request)).thenReturn(mappedInfo);
        when(userMapper.toUser(request)).thenReturn(null);

        CustomException ex = assertThrows(CustomException.class, () -> userService.adminAddUser(request));

        assertEquals(ResponseCode.INTERNAL_SERVER_ERROR, ex.getResponseCode());
    }

    @Test
    void adminAddUser_shouldThrowInternalServerError_whenUserInfoMapperReturnsNull() {
        RegistrationRequest request = new RegistrationRequest();
        request.setEmail("new.user@example.com");
        request.setRole("USER");

        Role role = new Role();
        role.setName("USER");

        when(userRepository.findByEmail("new.user@example.com")).thenReturn(Optional.empty());
        when(roleRepository.findRoleByName("USER")).thenReturn(Optional.of(role));
        when(userMapper.toUserInfo(request)).thenReturn(null);

        CustomException ex = assertThrows(CustomException.class, () -> userService.adminAddUser(request));

        assertEquals(ResponseCode.INTERNAL_SERVER_ERROR, ex.getResponseCode());
    }

    @Test
    void adminAddUser_shouldThrowInternalServerError_whenUnexpectedExceptionOccurs() {
        RegistrationRequest request = new RegistrationRequest();
        request.setEmail("new.user@example.com");
        request.setPassword("P@ssw0rd");
        request.setRole("ADMIN");

        Role role = new Role();
        role.setName("ADMIN");

        UserInfo mappedInfo = new UserInfo();
        mappedInfo.setEmail(request.getEmail());

        when(userRepository.findByEmail(request.getEmail())).thenReturn(Optional.empty());
        when(roleRepository.findRoleByName("ADMIN")).thenReturn(Optional.of(role));
        when(userMapper.toUserInfo(request)).thenReturn(mappedInfo);
        when(userInfoRepository.save(mappedInfo)).thenThrow(new RuntimeException("db fail"));

        CustomException ex = assertThrows(CustomException.class, () -> userService.adminAddUser(request));

        assertEquals(ResponseCode.INTERNAL_SERVER_ERROR, ex.getResponseCode());
    }

    @Test
    void sendChangePasswordEmail_shouldSendEmail_whenValid() throws Exception {
        UserInfo info = new UserInfo();
        info.setEmail("u@test.com");
        info.setFirstName("User");
        info.setLastName("Test");

        User user = new User();
        user.setUserInfo(info);

        userService.sendChangePasswordEmail(user, "new-pass");

        verify(emailService).sendEmail(eq("u@test.com"), eq("User Test"), any(), anyString(), eq("new-pass"), eq("New password"));
    }

    @Test
    void importUsersFromExcel_shouldCollectUnknownError_whenRowProcessingFailsUnexpectedly() throws Exception {
        byte[] content = buildWorkbookBytes();
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "users.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                content
        );

        doAnswer(invocation -> {
            throw new RuntimeException("unknown");
        }).when(userService).adminAddUser(any(RegistrationRequest.class));

        CustomException ex = assertThrows(CustomException.class, () -> userService.importUsersFromExcel(file));

        assertEquals(ResponseCode.VALIDATION_FAILED, ex.getResponseCode());
    }

    @Test
    void importUsersFromExcel_shouldConvertNumericBooleanAndBlankCells() throws Exception {
        byte[] content = buildWorkbookWithMixedCellTypes();
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "mixed.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                content
        );

        doReturn(new RegistrationResponse()).when(userService).adminAddUser(any(RegistrationRequest.class));

        userService.importUsersFromExcel(file);

        ArgumentCaptor<RegistrationRequest> requestCaptor = ArgumentCaptor.forClass(RegistrationRequest.class);
        verify(userService).adminAddUser(requestCaptor.capture());

        RegistrationRequest captured = requestCaptor.getValue();
        assertEquals("123", captured.getPhoneNumber());
        assertEquals("12.5", captured.getAddress());
        assertEquals("true", captured.getDepartment());
        assertEquals("", captured.getGender());
    }

    private byte[] buildWorkbookBytes() throws IOException {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("users");

            Row header = sheet.createRow(0);
            for (int i = 0; i <= 8; i++) {
                header.createCell(i).setCellValue("h" + i);
            }

            Row validRow = sheet.createRow(1);
            validRow.createCell(0).setCellValue("John");
            validRow.createCell(1).setCellValue("Doe");
            validRow.createCell(2).setCellValue("0123");
            validRow.createCell(3).setCellValue("Addr");
            validRow.createCell(4).setCellValue("IT");
            validRow.createCell(5).setCellValue("john.doe@test.com");
            validRow.createCell(6).setCellValue("MALE");
            validRow.createCell(7).setCellValue("Password1");
            validRow.createCell(8).setCellValue("USER");

            Row emptyEmailRow = sheet.createRow(2);
            emptyEmailRow.createCell(0).setCellValue("Jane");
            emptyEmailRow.createCell(1).setCellValue("Doe");
            emptyEmailRow.createCell(5).setCellValue("");

            workbook.write(out);
            return out.toByteArray();
        }
    }

    private byte[] buildWorkbookWithMixedCellTypes() throws IOException {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("users");

            Row header = sheet.createRow(0);
            for (int i = 0; i <= 8; i++) {
                header.createCell(i).setCellValue("h" + i);
            }

            Row row = sheet.createRow(1);
            row.createCell(0).setCellValue("John");
            row.createCell(1).setCellValue("Doe");
            row.createCell(2).setCellValue(123.0);
            row.createCell(3).setCellValue(12.5);
            row.createCell(4).setCellValue(true);
            row.createCell(5).setCellValue("john.doe@test.com");
            row.createCell(6, org.apache.poi.ss.usermodel.CellType.BLANK);

            CreationHelper creationHelper = workbook.getCreationHelper();
            CellStyle dateStyle = workbook.createCellStyle();
            dateStyle.setDataFormat(creationHelper.createDataFormat().getFormat("m/d/yy h:mm"));
            row.createCell(7).setCellValue(java.util.Date.from(java.time.Instant.parse("2026-04-24T00:00:00Z")));
            row.getCell(7).setCellStyle(dateStyle);

            row.createCell(8).setCellValue("USER");

            workbook.write(out);
            return out.toByteArray();
        }
    }
}

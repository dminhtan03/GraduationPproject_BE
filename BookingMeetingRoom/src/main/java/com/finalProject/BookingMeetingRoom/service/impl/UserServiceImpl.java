package com.finalProject.BookingMeetingRoom.service.impl;

import com.finalProject.BookingMeetingRoom.common.enums.EmailTemplateName;
import com.finalProject.BookingMeetingRoom.common.exception.CustomException;
import com.finalProject.BookingMeetingRoom.common.payload.ResponseCode;
import com.finalProject.BookingMeetingRoom.mapper.UserMapper;
import com.finalProject.BookingMeetingRoom.model.response.RegistrationResponse;
import com.finalProject.BookingMeetingRoom.model.response.UserResponse;
import com.finalProject.BookingMeetingRoom.model.entity.User;
import com.finalProject.BookingMeetingRoom.model.entity.UserInfo;
import com.finalProject.BookingMeetingRoom.model.entity.UserOtp;
import com.finalProject.BookingMeetingRoom.model.request.*;
import com.finalProject.BookingMeetingRoom.repository.RoleRepository;
import com.finalProject.BookingMeetingRoom.repository.UserInfoRepository;
import com.finalProject.BookingMeetingRoom.repository.UserOtpRepository;
import com.finalProject.BookingMeetingRoom.repository.UserRepository;
import com.finalProject.BookingMeetingRoom.service.EmailService;
import com.finalProject.BookingMeetingRoom.service.RedisService;
import com.finalProject.BookingMeetingRoom.service.UserService;
// start add excel and registration imports
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.web.multipart.MultipartFile;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.Set;
// end add excel and registration imports
import jakarta.mail.MessagingException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final PasswordEncoder passwordEncoder;
    private final UserRepository userRepository;
    private final UserInfoRepository userInfoRepository;
    private final EmailService emailService;
    private final UserOtpRepository userOtpRepository;
    private final RedisService redisService;
    private final RoleRepository roleRepository;
    private final UserMapper userMapper;

    @Value("${application.character.value}")
    private String character;

    @Value("${application.mailing.frontend.activation-url}")
    private String activationUrl;

    private static final Logger log = LoggerFactory.getLogger(UserServiceImpl.class);

    @Transactional
    public RegistrationResponse register(RegistrationRequest request) {
        try {
            var user = userRepository.findByEmail(request.getEmail());
            if (user.isPresent()) {
                throw new CustomException(ResponseCode.EMAIL_ALREADY_EXISTS);
            }

            var role = roleRepository.findRoleByName(request.getRole());
            if (role.isEmpty()) {
                throw new CustomException(ResponseCode.ROLE_NOT_FOUND);
            }

            var userInfo = userMapper.toUserInfo(request);
            if (userInfo == null) {
                log.error("Failed to map registration request to UserInfo");
                throw new CustomException(ResponseCode.INTERNAL_SERVER_ERROR);
            }
            userInfo.setId(UUID.randomUUID().toString());
            userInfo.setCreatedAt(LocalDateTime.now());
            userInfoRepository.save(userInfo);

            var newUser = userMapper.toUser(request);
            if (newUser == null) {
                log.error("Failed to map registration request to User");
                throw new CustomException(ResponseCode.INTERNAL_SERVER_ERROR);
            }
            newUser.setId(UUID.randomUUID().toString());
            newUser.setPassword(passwordEncoder.encode(request.getPassword()));
            newUser.setEnabled(false);
            newUser.setLoginCount(0);
            newUser.setLocked(false);
            newUser.setReset(false);
            newUser.setRoles(Set.of(role.get()));
            newUser.setUserInfo(userInfo);
            newUser.setCreatedAt(LocalDateTime.now());
            newUser.setUpdatedAt(LocalDateTime.now());

            userRepository.save(newUser);

            sendValidationEmail(newUser);
            return userMapper.toRegistrationResponse(userInfo);
        } catch (CustomException e) {
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error during registration", e);
            throw new CustomException(ResponseCode.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Changes the password of the currently authenticated user.
     *
     * @param request        the request containing current and new passwords
     * @param authentication the authentication object containing user details
     */
    @Transactional
    public void changePassword(ChangePasswordRequest request, Authentication authentication) {
        try {
            var user = (User) authentication.getPrincipal();

            if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
                throw new CustomException(ResponseCode.INVALID_CURRENT_PASSWORD);
            }

            if (!request.getNewPassword().equals(request.getConfirmPassword())) {
                throw new CustomException(ResponseCode.PASSWORD_CONFIRM_NOT_MATCH);
            }

            user.setPassword(passwordEncoder.encode(request.getNewPassword()));
            user.setReset(false);

            userRepository.save(user);

            emailService.sendEmail(
                    user.getUserInfo().getEmail(),
                    user.getUserInfo().getFullName(),
                    EmailTemplateName.ACTIVATE_ACCOUNT,
                    activationUrl,
                    "Change password successfully. Changed password: " + request.getNewPassword(),
                    "Change password"
            );
        } catch (CustomException e) {
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error during password change", e);
            throw new CustomException(ResponseCode.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Updates phone number and address of the currently authenticated user.
     */
    @Transactional
    public void updateUserInfo(UpdateUserInfoRequest request, Authentication authentication) {
        try {
            var user = (User) authentication.getPrincipal();
            var info = user.getUserInfo();
            if (info == null) {
                throw new CustomException(ResponseCode.USER_NOT_FOUND);
            }
            info.setPhoneNumber(request.getPhoneNumber());
            info.setAddress(request.getAddress());
            userInfoRepository.save(info);
        } catch (CustomException e) {
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error during updating user info", e);
            throw new CustomException(ResponseCode.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Handles the forgot password process by generating an OTP and sending a validation email.
     *
     * @param request the request containing the user's email
     */
    @Transactional
    public void handleForgotPassword(ForgotPasswordRequest request) {
        try {
            var user = userRepository.findByEmail(request.getEmail())
                    .orElseThrow(() -> new CustomException(ResponseCode.USER_NOT_FOUND));

            if (user.getUserInfo() == null || user.getUserInfo().getEmail() == null) {
                throw new CustomException(ResponseCode.INTERNAL_SERVER_ERROR);
            }

            String otp = generateAndActivateCode(user);
            redisService.setValue("otp:" + user.getUserInfo().getEmail(), otp, 5, TimeUnit.MINUTES);

            sendValidationEmail(user);
        } catch (CustomException e) {
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error during forgot password", e);
            throw new CustomException(ResponseCode.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Verifies the OTP for forgot password and resets the user's password if valid.
     *
     * @param request the request containing the email and OTP
     */
    @Transactional
    public void verifyForgotPassword(ForgotPasswordVerifyRequest request) {
        try {
            var userOpt = userRepository.findByEmail(request.getEmail())
                    .orElseThrow(() -> new CustomException(ResponseCode.USER_NOT_FOUND));

            var userEmail = userOpt.getUserInfo().getEmail();
            var otpKey = "otp:" + userEmail;
            var otpInput = request.getOtp();

            Object redisOtp = redisService.getValue(otpKey);

            if (redisOtp != null) {
                String otpInRedis = redisOtp.toString();
                if (!otpInRedis.equals(otpInput)) {
                    throw new CustomException(ResponseCode.INVALID_OTP);
                }

                redisService.delete(otpKey);
            } else {
                var otpInDbOpt = userOtpRepository.findValidOtp(userEmail, otpKey)
                        .orElseThrow(() -> new CustomException(ResponseCode.INVALID_OTP));

                if (otpInDbOpt.isUsed()) {
                    throw new CustomException(ResponseCode.OTP_ALREADY_USED);
                }

                if (LocalDateTime.now().isAfter(otpInDbOpt.getExpiresAt())) {
                    throw new CustomException(ResponseCode.EXPIRED_OTP);
                }

                if (!otpInDbOpt.getOtpCode().equals(otpInput)) {
                    throw new CustomException(ResponseCode.INVALID_OTP);
                }
                otpInDbOpt.setUsed(true);
                otpInDbOpt.setValidatedAt(LocalDateTime.now());
                userOtpRepository.save(otpInDbOpt);
            }

            var newPassword = generateRandomPassword();
            userOpt.setPassword(passwordEncoder.encode(newPassword));
            userOpt.setLocked(false);
            userOpt.setReset(true);
            userRepository.save(userOpt);

            sendChangePasswordEmail(userOpt, newPassword);
        } catch (CustomException e) {
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error during forgot password verification", e);
            throw new CustomException(ResponseCode.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Sends a change password email to the user with the new password.
     *
     * @param userOpt     the user whose password has been changed
     * @param newPassword the new password to be sent
     */
    public void sendChangePasswordEmail(User userOpt, String newPassword) {
        try {
            emailService.sendEmail(
                    userOpt.getUserInfo().getEmail(),
                    userOpt.getUserInfo().getFullName(),
                    EmailTemplateName.ACTIVATE_ACCOUNT,
                    activationUrl,
                    newPassword,
                    "New password"
            );
        } catch (Exception e) {
            throw new CustomException(ResponseCode.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Activates the user account using a valid OTP.
     *
     * @param validOtp the valid OTP code
     */
    @Transactional
    public void activateAccount(String validOtp) {
        try {
            var otp = userOtpRepository.findByOtpCode(validOtp)
                    .orElseThrow(() -> new CustomException(ResponseCode.INVALID_OTP));

            if (LocalDateTime.now().isAfter(otp.getExpiresAt())) {
                throw new CustomException(ResponseCode.EXPIRED_OTP);
            }

            var user = otp.getUser();
            if (user == null) {
                throw new CustomException(ResponseCode.USER_NOT_FOUND);
            }

            user.setEnabled(true);
            userRepository.save(user);
            otp.setValidatedAt(LocalDateTime.now());
            otp.setUsed(true);
            userOtpRepository.save(otp);
        } catch (CustomException e) {
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error during account activation", e);
            throw new CustomException(ResponseCode.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Resends the OTP for account activation to the user's email.
     *
     * @param request the request containing the user's email
     */
    public void resendOtp(ResendOtpRequest request) {
        try {
            var user = userRepository.findByEmail(request.getEmail())
                    .orElseThrow(() -> new CustomException(ResponseCode.USER_NOT_FOUND));

            sendValidationEmail(user);
        } catch (CustomException e) {
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error during OTP resend", e);
            throw new CustomException(ResponseCode.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Generates a random password of length 8 using characters defined in the application properties.
     *
     * @return a randomly generated password
     */
    public String generateRandomPassword() {
        int length = 8;
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < length; i++) {
            sb.append(character.charAt(random.nextInt(character.length())));
        }
        return sb.toString();
    }

    /**
     * Sends a validation email to the user for account activation.
     *
     * @param user the user to whom the validation email will be sent
     * @throws MessagingException if there is an error sending the email
     */
    public void sendValidationEmail(User user) throws MessagingException {
        var validOtp = generateAndActivateCode(user);
        emailService.sendEmail(
                user.getUserInfo().getEmail(),
                user.getUserInfo().getFullName(),
                EmailTemplateName.ACTIVATE_ACCOUNT,
                activationUrl,
                validOtp,
                "Activate your account"
        );
    }

    /**
     * Generates an activation code, saves it in Redis and the database, and returns the code.
     *
     * @param user the user for whom the activation code is generated
     * @return the generated activation code
     */
    public String generateAndActivateCode(User user) {
        var actCode = generateActivationCode();

        String otpKey = "otp:" + user.getUserInfo().getEmail();
        redisService.setValue(otpKey, actCode, 1, TimeUnit.MINUTES);

        UserOtp otp = new UserOtp();
        otp.setId(UUID.randomUUID().toString());
        otp.setOtpCode(actCode);
        otp.setExpiresAt(LocalDateTime.now().plusMinutes(1));
        otp.setIssuedAt(LocalDateTime.now());
        otp.setValidatedAt(null);
        otp.setUsed(false);
        otp.setUser(user);
        userOtpRepository.save(otp);
        return actCode;
    }

    /**
     * Generates a random activation code consisting of 6 digits.
     *
     * @return a 6-digit activation code
     */
    public String generateActivationCode() {
        String character = "0123456789";
        StringBuilder codeBuilder = new StringBuilder();
        SecureRandom random = new SecureRandom();

        for (int i = 0; i < 6; i++) {
            int randomIndex = random.nextInt(character.length());
            char randomChar = character.charAt(randomIndex);
            codeBuilder.append(randomChar);
        }

        return codeBuilder.toString();
    }

    // start add adminAddUser and importUsersFromExcel implementation
    @Override
    @Transactional
    public RegistrationResponse adminAddUser(RegistrationRequest request) {
        try {
            var user = userRepository.findByEmail(request.getEmail());
            if (user.isPresent()) {
                throw new CustomException(ResponseCode.EMAIL_ALREADY_EXISTS);
            }

            var role = roleRepository.findRoleByName(request.getRole());
            if (role.isEmpty()) {
                throw new CustomException(ResponseCode.ROLE_NOT_FOUND);
            }

            var userInfo = userMapper.toUserInfo(request);
            if (userInfo == null) {
                log.error("Failed to map registration request to UserInfo");
                throw new CustomException(ResponseCode.INTERNAL_SERVER_ERROR);
            }
            userInfo.setId(UUID.randomUUID().toString());
            userInfo.setCreatedAt(LocalDateTime.now());
            userInfoRepository.save(userInfo);

            var newUser = userMapper.toUser(request);
            if (newUser == null) {
                log.error("Failed to map registration request to User");
                throw new CustomException(ResponseCode.INTERNAL_SERVER_ERROR);
            }
            newUser.setId(UUID.randomUUID().toString());
            newUser.setPassword(passwordEncoder.encode(request.getPassword()));
            newUser.setEnabled(true); // Directly enabled by admin
            newUser.setLoginCount(0);
            newUser.setLocked(false);
            newUser.setReset(false);
            newUser.setRoles(Set.of(role.get()));
            newUser.setUserInfo(userInfo);
            newUser.setCreatedAt(LocalDateTime.now());
            newUser.setUpdatedAt(LocalDateTime.now());

            userRepository.save(newUser);
            return userMapper.toRegistrationResponse(userInfo);
        } catch (CustomException e) {
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error during admin adding user", e);
            throw new CustomException(ResponseCode.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    @Transactional
    public void importUsersFromExcel(MultipartFile file) {
        try (InputStream is = file.getInputStream();
             Workbook workbook = new XSSFWorkbook(is)) {

            Sheet sheet = workbook.getSheetAt(0);
            for (Row row : sheet) {
                // Skip header row
                if (row.getRowNum() == 0) continue;

                try {
                    String firstName = getCellValue(row.getCell(0));
                    String lastName = getCellValue(row.getCell(1));
                    String phoneNumber = getCellValue(row.getCell(2));
                    String address = getCellValue(row.getCell(3));
                    String department = getCellValue(row.getCell(4));
                    String email = getCellValue(row.getCell(5));
                    String gender = getCellValue(row.getCell(6));
                    String password = getCellValue(row.getCell(7));
                    String role = getCellValue(row.getCell(8));

                    if (email.isEmpty()) continue;

                    RegistrationRequest request = new RegistrationRequest();
                    request.setFirstName(firstName);
                    request.setLastName(lastName);
                    request.setPhoneNumber(phoneNumber);
                    request.setAddress(address);
                    request.setDepartment(department);
                    request.setEmail(email);
                    request.setGender(gender);
                    request.setPassword(password);
                    request.setRole(role);

                    adminAddUser(request); // Use adminAddUser to enable immediately
                } catch (Exception e) {
                    log.error("Error processing user row " + row.getRowNum() + ": " + e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("Error importing users from excel: " + e.getMessage(), e);
            throw new CustomException(ResponseCode.INTERNAL_SERVER_ERROR);
        }
    }

    private String getCellValue(Cell cell) {
        if (cell == null) return "";
        switch (cell.getCellType()) {
            case STRING: return cell.getStringCellValue();
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    return cell.getLocalDateTimeCellValue().toString();
                }
                double numericValue = cell.getNumericCellValue();
                // Kiểm tra nếu là số nguyên thì loại bỏ phần thập phân .0
                if (numericValue == (long) numericValue) {
                    return String.valueOf((long) numericValue);
                }
                return String.valueOf(numericValue);
            case BOOLEAN: return String.valueOf(cell.getBooleanCellValue());
            default: return "";
        }
    }
    // end add adminAddUser and importUsersFromExcel implementation
}

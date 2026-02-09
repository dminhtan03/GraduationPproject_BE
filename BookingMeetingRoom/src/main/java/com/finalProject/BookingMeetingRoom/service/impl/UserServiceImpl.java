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
        }
        catch (Exception e) {
            log.error("Unexpected error during password change", e);
            throw new CustomException(ResponseCode.INTERNAL_SERVER_ERROR);
        }
    }

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

    private void sendChangePasswordEmail(User userOpt, String newPassword) {
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

    @Transactional
    public void activateAccount(String validOtp) {
        try {
            var otp = userOtpRepository.findByOtpCode(validOtp)
                    .orElseThrow(() -> new CustomException(ResponseCode.INVALID_OTP));

            if(LocalDateTime.now().isAfter(otp.getExpiresAt())) {
                sendValidationEmail(otp.getUser());
                throw new CustomException(ResponseCode.EXPIRED_OTP);
            }

            var user = otp.getUser();
            if(user == null) {
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

    public List<UserResponse> getAllUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if(!authentication.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_ADMIN"))) {
            throw new CustomException(ResponseCode.ACCESS_DENIED);
        }

        List<UserInfo> userInfos = userInfoRepository.findAll();
        return userInfos.stream()
                .map(userMapper::toUserResponse)
                .collect(Collectors.toList());
    }

    private String generateRandomPassword() {
        int length = 8;
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder();

        for(int i = 0; i < length; i++) {
            sb.append(character.charAt(random.nextInt(character.length())));
        }
        return sb.toString();
    }

    private void sendValidationEmail(User user) throws MessagingException {
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

    private String generateAndActivateCode(User user) {
        var actCode = generateActivationCode();

        String otpKey = "otp:" + user.getUserInfo().getEmail();
        redisService.setValue(otpKey, actCode, 5, TimeUnit.MINUTES);

        UserOtp otp = new UserOtp();
        otp.setId(UUID.randomUUID().toString());
        otp.setOtpCode(actCode);
        otp.setExpiresAt(LocalDateTime.now().plusMinutes(3));
        otp.setIssuedAt(LocalDateTime.now());
        otp.setValidatedAt(null);
        otp.setUsed(false);
        otp.setUser(user);
        userOtpRepository.save(otp);
        return actCode;
    }

    private String generateActivationCode() {
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
}

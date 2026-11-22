package com.sdas.server.identity.integration;

import static com.sdas.server.identity.contract.IdentityContractFixtures.accessToken;
import static com.sdas.server.identity.contract.IdentityContractFixtures.freshPhone;
import static com.sdas.server.identity.contract.IdentityContractFixtures.identityStatus;
import static com.sdas.server.identity.contract.IdentityContractFixtures.login;
import static com.sdas.server.identity.contract.IdentityContractFixtures.passwordReset;
import static com.sdas.server.identity.contract.IdentityContractFixtures.register;
import static com.sdas.server.identity.contract.IdentityContractFixtures.registerRequest;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sdas.server.identity.model.VerificationPurpose;
import com.sdas.server.identity.repository.PatientAccountRepository;
import com.sdas.server.identity.service.CryptoService;
import com.sdas.server.identity.support.IdentityTestSupport;
import com.sdas.server.identity.support.VerificationCodeStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.TransientDataAccessResourceException;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * T094：重置密码主流程、会话撤销、旧凭证失效、跨用途隔离、统一文案与依赖异常。
 */
class PasswordResetFlowIT extends IdentityTestSupport {

    private static final String OLD_PASSWORD = "abc12345";

    private static final String NEW_PASSWORD = "xyz98765";

    private static final String FIXED_CODE = "888888";

    @Autowired
    private VerificationCodeStore verificationCodeStore;

    @Autowired
    private CryptoService cryptoService;

    @MockitoSpyBean
    private PatientAccountRepository accountRepository;

    private void prepareResetCode(String phone) {
        verificationCodeStore.save(VerificationPurpose.RESET_PASSWORD,
                cryptoService.phoneDigest(phone), cryptoService.codeDigest(FIXED_CODE), 0);
    }

    @Test
    void resetInvalidatesOldPasswordAndSessions() throws Exception {
        String phone = freshPhone();
        var registration = register(mockMvc, phone);
        String token = accessToken(registration);
        prepareResetCode(phone);

        mockMvc.perform(passwordReset(phone, FIXED_CODE, NEW_PASSWORD))
                .andExpect(status().isOk());

        // 旧密码登录失败（统一 AUTHENTICATION_FAILED），新密码登录成功
        mockMvc.perform(login(phone, OLD_PASSWORD))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("账号或密码错误，或验证信息无效"));
        mockMvc.perform(login(phone, NEW_PASSWORD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        // 注册时签发的 access token 因会话被撤销而失效
        mockMvc.perform(identityStatus(token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    void resetCodeCannotBeReusedForRegistration() throws Exception {
        String phone = freshPhone();
        register(mockMvc, phone);
        prepareResetCode(phone);

        // 用 RESET_PASSWORD 用途验证码走注册流程（REGISTER 用途），应因跨用途失败
        mockMvc.perform(registerRequest(phone))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("账号或密码错误，或验证信息无效"));
    }

    @Test
    void unregisteredPhoneAndWrongCodeShareUnifiedMessage() throws Exception {
        String phone = freshPhone();
        register(mockMvc, phone);
        prepareResetCode(phone);
        String unified = "账号或密码错误，或验证信息无效";

        mockMvc.perform(passwordReset(phone, "000000", NEW_PASSWORD))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(unified));

        String other = freshPhone();
        prepareResetCode(other);
        mockMvc.perform(passwordReset(other, FIXED_CODE, NEW_PASSWORD))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(unified));
    }

    @Test
    void mysqlUnavailableLeavesPasswordAndSessionsUnchanged() throws Exception {
        String phone = freshPhone();
        var registration = register(mockMvc, phone);
        prepareResetCode(phone);

        // 注册完成后令账号查询抛依赖异常，仅影响重置流程
        org.mockito.Mockito.doThrow(new TransientDataAccessResourceException("mysql down"))
                .when(accountRepository).findByPhoneHash(org.mockito.ArgumentMatchers.anyString());

        mockMvc.perform(passwordReset(phone, FIXED_CODE, NEW_PASSWORD))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value(503));

        // 清除依赖异常桩，验证异常前已注册账号仍可凭旧密码登录（密码未变、会话未撤销）
        org.mockito.Mockito.reset(accountRepository);
        String token = accessToken(registration);
        mockMvc.perform(login(phone, OLD_PASSWORD)).andExpect(status().isOk());
        mockMvc.perform(identityStatus(token)).andExpect(status().isOk());
    }
}

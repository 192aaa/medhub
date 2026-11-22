package com.sdas.server.identity.contract;

import static com.sdas.server.identity.contract.IdentityContractFixtures.freshPhone;
import static com.sdas.server.identity.contract.IdentityContractFixtures.passwordReset;
import static com.sdas.server.identity.contract.IdentityContractFixtures.register;
import static com.sdas.server.identity.contract.IdentityContractFixtures.withCsrf;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
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
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * T093：端点 6 正向与异常（格式 / 统一失败 / CSRF / 503）。
 */
class PasswordResetContractTest extends IdentityTestSupport {

    private static final String FIXED_CODE = "888888";

    private static final String UNIFIED_MESSAGE = "账号或密码错误，或验证信息无效";

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
    void resetSucceedsWithValidResetCode() throws Exception {
        String phone = freshPhone();
        register(mockMvc, phone);
        prepareResetCode(phone);

        mockMvc.perform(passwordReset(phone, FIXED_CODE, "xyz98765"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("成功"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void invalidFormatsReturnDedicatedCodes() throws Exception {
        String phone = freshPhone();
        register(mockMvc, phone);

        mockMvc.perform(passwordReset("12345", FIXED_CODE, "xyz98765"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("手机号须为 11 位中国大陆号码"));
        mockMvc.perform(passwordReset(phone, "12345", "xyz98765"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("验证码须为 6 位数字"));
        mockMvc.perform(passwordReset(phone, FIXED_CODE, "abcdef"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        "密码须为 6-20 位字母数字组合，且至少含一个字母和一个数字"));
    }

    @Test
    void wrongCodeAndUnregisteredPhoneShareUnifiedMessage() throws Exception {
        String phone = freshPhone();
        register(mockMvc, phone);
        prepareResetCode(phone);

        mockMvc.perform(passwordReset(phone, "000000", "xyz98765"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value(UNIFIED_MESSAGE));

        String other = freshPhone();
        prepareResetCode(other);
        mockMvc.perform(passwordReset(other, FIXED_CODE, "xyz98765"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(UNIFIED_MESSAGE));
    }

    @Test
    void missingCsrfReturns403() throws Exception {
        var builder = MockMvcRequestBuilders.post("/api/v1/password-resets")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"phone\":\"13800138000\",\"verificationCode\":\"888888\","
                        + "\"newPassword\":\"xyz98765\"}");
        mockMvc.perform(builder)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    void dependencyFailureReturns503() throws Exception {
        String phone = freshPhone();
        register(mockMvc, phone);
        prepareResetCode(phone);
        // 注册完成后才令账号查询抛依赖异常，仅影响后续重置
        doThrow(new TransientDataAccessResourceException("mysql down"))
                .when(accountRepository).findByPhoneHash(anyString());

        mockMvc.perform(passwordReset(phone, FIXED_CODE, "xyz98765"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value(503))
                .andExpect(jsonPath("$.message").value("服务暂时不可用，请稍后重试"));
    }
}

package com.sdas.server.identity.contract;

import static com.sdas.server.identity.contract.IdentityContractFixtures.accessToken;
import static com.sdas.server.identity.contract.IdentityContractFixtures.freshPhone;
import static com.sdas.server.identity.contract.IdentityContractFixtures.identityStatus;
import static com.sdas.server.identity.contract.IdentityContractFixtures.register;
import static com.sdas.server.identity.contract.IdentityContractFixtures.verify;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sdas.server.identity.repository.PatientAccountRepository;
import com.sdas.server.identity.service.CryptoService;
import com.sdas.server.identity.support.IdentityFixture;
import com.sdas.server.identity.support.IdentityTestSupport;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.TransientDataAccessResourceException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * T078：端点 8 未认证（三个 null 字段仍存在）/ 已认证响应、401、503 与严格联动。
 */
class IdentityStatusContractTest extends IdentityTestSupport {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private CryptoService cryptoService;

    @MockitoSpyBean
    private PatientAccountRepository accountRepository;

    private final List<Long> whitelistIds = new ArrayList<>();

    private IdentityFixture fixture;

    @BeforeEach
    void setUp() {
        fixture = new IdentityFixture(jdbcTemplate);
    }

    @AfterEach
    void tearDown() {
        whitelistIds.forEach(fixture::deleteWhitelist);
        whitelistIds.clear();
    }

    @Test
    void unverifiedStatusKeepsThreeNullFields() throws Exception {
        String token = accessToken(register(mockMvc, freshPhone()));

        mockMvc.perform(identityStatus(token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.authStatus").value("UNVERIFIED"))
                .andExpect(jsonPath("$.data.consultationAllowed").value(false))
                // value(nullValue()) 同时要求路径存在且值为 null，缺失字段会抛 PathNotFoundException
                .andExpect(jsonPath("$.data.verifiedAt").value(nullValue()))
                .andExpect(jsonPath("$.data.maskedName").value(nullValue()))
                .andExpect(jsonPath("$.data.maskedIdCard").value(nullValue()))
                .andExpect(jsonPath("$.data.idCardHash").doesNotExist());
    }

    @Test
    void verifiedStatusHasStrictlyLinkedConsultationAllowed() throws Exception {
        String token = accessToken(register(mockMvc, freshPhone()));
        String name = fixture.syntheticName();
        String idCard = fixture.randomIdCardNumber();
        insertWhitelist(name, idCard);
        mockMvc.perform(verify(token, name, idCard)).andExpect(status().isOk());

        mockMvc.perform(identityStatus(token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.authStatus").value("VERIFIED"))
                .andExpect(jsonPath("$.data.consultationAllowed").value(true))
                .andExpect(jsonPath("$.data.verifiedAt").isString())
                .andExpect(jsonPath("$.data.maskedName").value(name.substring(0, 1) + "*"))
                .andExpect(jsonPath("$.data.maskedIdCard").value(
                        idCard.substring(0, 3) + "***********" + idCard.substring(14)));
    }

    @Test
    void missingBearerReturns401() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/patients/current/identity-status"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    void mysqlFailureReturns503() throws Exception {
        String token = accessToken(register(mockMvc, freshPhone()));
        doThrow(new TransientDataAccessResourceException("mysql down"))
                .when(accountRepository).findById(anyLong());

        mockMvc.perform(identityStatus(token))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value(503))
                .andExpect(jsonPath("$.message").value("服务暂时不可用，请稍后重试"));
    }

    private void insertWhitelist(String name, String idCard) {
        String digest = cryptoService.idCardDigest(idCard);
        byte[] cipher = cryptoService.encrypt(idCard.getBytes(StandardCharsets.UTF_8));
        whitelistIds.add(fixture.insertWhitelist(name, digest, cipher));
    }
}

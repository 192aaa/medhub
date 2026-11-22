package com.sdas.server.appointment.contract;

import static com.sdas.server.appointment.support.AppointmentFixtures.CSRF;
import static com.sdas.server.appointment.support.AppointmentFixtures.body;
import static com.sdas.server.appointment.support.AppointmentFixtures.verifiedPatient;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.sdas.server.appointment.support.AppointmentFixtures;
import com.sdas.server.appointment.support.AppointmentTestSupport;
import com.sdas.server.appointment.support.AppointmentFixtures.PatientSession;
import jakarta.servlet.http.Cookie;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * 端点 7 契约测试（tasks.md T044）：图片上传正向字段、格式/大小校验、认证与 CSRF。
 */
class ImageUploadContractTest extends AppointmentTestSupport {

    private static final String UPLOAD_PATH = "/api/v1/consultations/images";

    @BeforeEach
    void setUp() {
        AppointmentFixtures.seedCatalog(jdbcTemplate);
    }

    @Test
    void uploadPngReturnsContractFields() throws Exception {
        PatientSession patient = verifiedPatient(mockMvc, jdbcTemplate);
        byte[] content = pngBytes();

        MvcResult result = mockMvc.perform(upload(patient.accessToken(),
                        new MockMultipartFile("file", "report.png", "image/png", content)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();

        JsonNode data = body(result).path("data");
        assertTrue(data.path("imageId").asLong() > 0);
        assertTrue(data.path("url").asText().startsWith("/static/consultation-images/"));
        assertEquals("image/png", data.path("contentType").asText());
        assertEquals(content.length, data.path("sizeBytes").asLong());
    }

    @Test
    void uploadRejectsUnsupportedContentType() throws Exception {
        PatientSession patient = verifiedPatient(mockMvc, jdbcTemplate);

        mockMvc.perform(upload(patient.accessToken(),
                        new MockMultipartFile("file", "notes.txt", "text/plain",
                                "hello".getBytes())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("图片格式不支持"));
    }

    @Test
    void uploadRejectsDisguisedFormat() throws Exception {
        PatientSession patient = verifiedPatient(mockMvc, jdbcTemplate);

        mockMvc.perform(upload(patient.accessToken(),
                        new MockMultipartFile("file", "fake.png", "image/png",
                                "not-an-image".getBytes())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("图片格式不支持"));
    }

    @Test
    void uploadRejectsOversizeImage() throws Exception {
        PatientSession patient = verifiedPatient(mockMvc, jdbcTemplate);
        byte[] oversize = new byte[5 * 1024 * 1024 + 1];

        mockMvc.perform(upload(patient.accessToken(),
                        new MockMultipartFile("file", "big.png", "image/png", oversize)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("单张图片不得超过 5 MB"));
    }

    @Test
    void uploadRequiresAuthentication() throws Exception {
        mockMvc.perform(upload(null, new MockMultipartFile("file", "a.png", "image/png",
                        pngBytes())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    void uploadWithoutCsrfIsRejected() throws Exception {
        PatientSession patient = verifiedPatient(mockMvc, jdbcTemplate);

        mockMvc.perform(MockMvcRequestBuilders.multipart(UPLOAD_PATH)
                        .file(new MockMultipartFile("file", "a.png", "image/png", pngBytes()))
                        .header("Authorization", "Bearer " + patient.accessToken()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    private MockHttpServletRequestBuilder upload(String token, MockMultipartFile file) {
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.multipart(UPLOAD_PATH)
                .file(file);
        builder.cookie(new Cookie("sdas_csrf", CSRF)).header("X-CSRF-Token", CSRF);
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        return builder;
    }

    private byte[] pngBytes() throws Exception {
        BufferedImage image = new BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }
}

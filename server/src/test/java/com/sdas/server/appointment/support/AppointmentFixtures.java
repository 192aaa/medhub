package com.sdas.server.appointment.support;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sdas.server.identity.contract.IdentityContractFixtures;
import jakarta.servlet.http.Cookie;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * 002 契约 / 集成测试共享夹具：科室与医生目录、患者会话、预约与支付请求构造。
 *
 * <p>目录使用 801+ / 1101+ / 1201+ 高位主键，避免与 001、003 及 dev 种子数据冲突；
 * 所有预置语句幂等（{@code INSERT IGNORE} 或先删后插），可在共享测试库中反复执行。</p>
 */
public final class AppointmentFixtures {

    /** 双重 Cookie 校验用的 CSRF 值（与 001 契约夹具一致）。 */
    public static final String CSRF = "contract-csrf-token";

    /** 内科（启用）。 */
    public static final long DEPT_INTERNAL = 801L;

    /** 外科（启用）。 */
    public static final long DEPT_SURGERY = 802L;

    /** 停用科室（不应出现在可选列表）。 */
    public static final long DEPT_DISABLED = 803L;

    /** 在线医生，挂号费 30.00。 */
    public static final long DOCTOR_ONLINE = 1101L;

    /** 离线医生，挂号费 20.00。 */
    public static final long DOCTOR_OFFLINE = 1104L;

    /** 可约排班（属在线医生）。 */
    public static final long SCHEDULE_BOOKABLE = 1201L;

    /** 已约满排班（属在线医生）。 */
    public static final long SCHEDULE_FULL = 1202L;

    /** 离线医生的排班。 */
    public static final long SCHEDULE_OFFLINE_DOCTOR = 1203L;

    /** 在线医生挂号费（与预置一致）。 */
    public static final BigDecimal ONLINE_FEE = new BigDecimal("30.00");

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private AppointmentFixtures() {
    }

    /** 患者会话：主键 + access token。 */
    public record PatientSession(long patientId, String accessToken) {
    }

    /**
     * 预置科室 / 医生 / 排班 / 知识库目录（幂等）。
     *
     * @param jdbcTemplate JDBC 模板
     */
    public static void seedCatalog(JdbcTemplate jdbcTemplate) {
        jdbcTemplate.update("INSERT IGNORE INTO department (id, name, description, sort_order, enabled) "
                + "VALUES (801, '内科', '常见内科疾病', 1, 1), (802, '外科', '外科疾病', 2, 1), "
                + "(803, '停用科室', '不可见', 3, 0)");
        jdbcTemplate.update("INSERT IGNORE INTO doctor (id, department_id, name, title, hospital, "
                + "years_of_experience, good_at, consultation_fee, rating, consultation_count, "
                + "online_status) VALUES "
                + "(1101, 801, '张*', '主任医师', '示例医院', 15, '呼吸系统疾病', 30.00, 4.8, 1200, 'ONLINE'), "
                + "(1104, 802, '李*', '主治医师', '示例医院', 8, '外伤处理', 20.00, 4.5, 300, 'OFFLINE')");
        jdbcTemplate.update("INSERT IGNORE INTO doctor_schedule (id, doctor_id, work_date, period, "
                + "slots_total, slots_booked) VALUES "
                + "(1201, 1101, DATE_ADD(CURDATE(), INTERVAL 2 DAY), 'MORNING', 20, 5), "
                + "(1202, 1101, DATE_ADD(CURDATE(), INTERVAL 3 DAY), 'AFTERNOON', 10, 10), "
                + "(1203, 1104, DATE_ADD(CURDATE(), INTERVAL 2 DAY), 'EVENING', 10, 0)");
        jdbcTemplate.update("DELETE FROM department_disease_kb WHERE department_id = 801 "
                + "AND disease_keyword = '咳嗽'");
        jdbcTemplate.update("INSERT INTO department_disease_kb (department_id, disease_keyword, "
                + "disease_name, source, kb_version) VALUES (801, '咳嗽', '咳嗽', 'PRESET', 'PRESET')");
    }

    /**
     * 注册新患者（未实名）。
     *
     * @param mockMvc     MockMvc
     * @param jdbcTemplate JDBC 模板（保留参数以便子类扩展）
     * @return 患者会话
     * @throws Exception 请求失败
     */
    public static PatientSession newPatient(MockMvc mockMvc, JdbcTemplate jdbcTemplate)
            throws Exception {
        MvcResult result = IdentityContractFixtures.register(mockMvc,
                IdentityContractFixtures.freshPhone());
        return new PatientSession(IdentityContractFixtures.patientId(result),
                IdentityContractFixtures.accessToken(result));
    }

    /**
     * 注册并完成实名 + 历史就诊预置的患者（满足复诊门禁）。
     *
     * @param mockMvc     MockMvc
     * @param jdbcTemplate JDBC 模板
     * @return 患者会话
     * @throws Exception 请求失败
     */
    public static PatientSession verifiedPatient(MockMvc mockMvc, JdbcTemplate jdbcTemplate)
            throws Exception {
        PatientSession session = newPatient(mockMvc, jdbcTemplate);
        jdbcTemplate.update("UPDATE patient_account SET auth_status = 'VERIFIED' WHERE id = ?",
                session.patientId());
        jdbcTemplate.update("INSERT INTO patient_history_visit (patient_id, visit_date, "
                + "department_id, doctor_id, summary) VALUES (?, CURDATE(), 801, 1101, '复诊记录')",
                session.patientId());
        return session;
    }

    /**
     * 构造带 Bearer 与双重 Cookie CSRF 的 JSON POST 请求。
     *
     * @param path  URI
     * @param token access token（可空，用于匿名分支）
     * @param body  JSON 请求体
     * @return 请求构造器
     */
    public static MockHttpServletRequestBuilder postJson(String path, String token, String body) {
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.post(path)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body == null ? "" : body);
        builder.cookie(new Cookie("sdas_csrf", CSRF)).header("X-CSRF-Token", CSRF);
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        return builder;
    }

    /**
     * 构造带 Bearer 的 GET 请求（GET 不校验 CSRF）。
     *
     * @param path  URI
     * @param token access token（可空）
     * @return 请求构造器
     */
    public static MockHttpServletRequestBuilder get(String path, String token) {
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.get(path);
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        return builder;
    }

    /**
     * 创建挂号单。
     *
     * @param mockMvc    MockMvc
     * @param token      access token
     * @param doctorId   医生主键
     * @param scheduleId 排班主键
     * @return 挂号单主键
     * @throws Exception 请求失败
     */
    public static long createAppointment(MockMvc mockMvc, String token, long doctorId,
                                         long scheduleId) throws Exception {
        MvcResult result = mockMvc.perform(postJson("/api/v1/appointments", token,
                        "{\"doctorId\":" + doctorId + ",\"scheduleId\":" + scheduleId + "}"))
                .andExpect(status().isOk()).andReturn();
        return body(result).path("data").path("appointmentId").asLong();
    }

    /**
     * 创建并支付挂号单（MOCK 渠道）。
     *
     * @param mockMvc    MockMvc
     * @param token      access token
     * @param doctorId   医生主键
     * @param scheduleId 排班主键
     * @param fee        挂号费（须与医生咨询费一致）
     * @return 挂号单主键
     * @throws Exception 请求失败
     */
    public static long createPaidAppointment(MockMvc mockMvc, String token, long doctorId,
                                             long scheduleId, BigDecimal fee) throws Exception {
        long appointmentId = createAppointment(mockMvc, token, doctorId, scheduleId);
        MvcResult payment = mockMvc.perform(postJson(
                        "/api/v1/appointments/" + appointmentId + "/payments", token,
                        "{\"channel\":\"MOCK\",\"amount\":" + fee.toPlainString() + "}"))
                .andExpect(status().isOk())
                .andReturn();
        long paymentId = body(payment).path("data").path("paymentId").asLong();
        mockMvc.perform(postJson("/api/v1/appointments/" + appointmentId + "/payments/"
                        + paymentId + "/confirm", token, ""))
                .andExpect(status().isOk());
        return appointmentId;
    }

    /**
     * 解析响应体。
     *
     * @param result MVC 结果
     * @return JSON 根节点
     * @throws Exception 解析失败
     */
    public static JsonNode body(MvcResult result) throws Exception {
        return MAPPER.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    /**
     * 统计患者挂号单数量。
     *
     * @param jdbcTemplate JDBC 模板
     * @param patientId    患者主键
     * @return 数量
     */
    public static int appointmentCount(JdbcTemplate jdbcTemplate, long patientId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM appointment WHERE patient_id = ?", Integer.class, patientId);
        return count == null ? 0 : count;
    }

    /**
     * 统计审计事件条数。
     *
     * @param jdbcTemplate JDBC 模板
     * @param eventType    事件类型
     * @param patientId    患者主键（可空）
     * @return 数量
     */
    public static int auditCount(JdbcTemplate jdbcTemplate, String eventType, Long patientId) {
        Integer count;
        if (patientId == null) {
            count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(1) FROM security_audit_event WHERE event_type = ?",
                    Integer.class, eventType);
        } else {
            count = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM security_audit_event "
                    + "WHERE event_type = ? AND actor_patient_id = ?", Integer.class, eventType,
                    patientId);
        }
        return count == null ? 0 : count;
    }
}

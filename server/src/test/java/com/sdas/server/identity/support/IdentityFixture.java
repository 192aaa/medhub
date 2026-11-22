package com.sdas.server.identity.support;

import java.security.SecureRandom;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * 测试夹具：动态生成随机手机号、符合 GB 11643 校验位的证件号与 BCrypt 密码。
 *
 * <p>禁止硬编码真实身份信息；白名单记录由用例插入并在结束后回滚（原则 VI）。</p>
 */
public final class IdentityFixture {

    private static final SecureRandom RANDOM = new SecureRandom();

    private static final AtomicLong SEQUENCE = new AtomicLong();

    private static final int[] WEIGHTS = {7, 9, 10, 5, 8, 4, 2, 1, 6, 3, 7, 9, 10, 5, 8, 4, 2};

    private static final char[] CHECK_CODES = {'1', '0', 'X', '9', '8', '7', '6', '5', '4', '3', '2'};

    private final JdbcTemplate jdbcTemplate;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder(10);

    /**
     * 构造夹具。
     *
     * @param jdbcTemplate Spring JDBC 模板
     */
    public IdentityFixture(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** @return 随机 11 位中国大陆手机号 */
    public String randomPhone() {
        long suffix = RANDOM.nextInt(900_000_000) + 100_000_000;
        return "13" + (RANDOM.nextInt(10)) + suffix;
    }

    /** @return 算法构造的 18 位证件号，末位校验位符合 GB 11643 */
    public String randomIdCardNumber() {
        StringBuilder prefix = new StringBuilder("110105");
        int year = 1950 + RANDOM.nextInt(50);
        int month = 1 + RANDOM.nextInt(12);
        int day = 1 + RANDOM.nextInt(28);
        prefix.append(String.format("%04d%02d%02d", year, month, day));
        prefix.append(String.format("%03d", RANDOM.nextInt(1000)));
        int sum = 0;
        for (int i = 0; i < WEIGHTS.length; i++) {
            sum += (prefix.charAt(i) - '0') * WEIGHTS[i];
        }
        return prefix.append(CHECK_CODES[sum % 11]).toString();
    }

    /** @return 合成演示姓名，不含真实身份信息 */
    public String syntheticName() {
        return "测试姓名" + SEQUENCE.incrementAndGet();
    }

    /**
     * 生成密码哈希。
     *
     * @param rawPassword 明文密码
     * @return BCrypt 哈希（strength = 10）
     */
    public String bcrypt(String rawPassword) {
        return passwordEncoder.encode(rawPassword);
    }

    /**
     * 向白名单插入一条动态记录。
     *
     * @param realName      合成姓名
     * @param idCardHash    证件号 HMAC 摘要
     * @param idCardCipher  证件号密文
     * @return 新记录主键
     */
    public long insertWhitelist(String realName, String idCardHash, byte[] idCardCipher) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            var statement = connection.prepareStatement(
                    "INSERT INTO hospital_identity_whitelist "
                            + "(real_name, id_card_hash, id_card_cipher, enabled, source, "
                            + "created_at, updated_at, version) "
                            + "VALUES (?, ?, ?, 1, 'PRESET', NOW(), NOW(), 0)",
                    java.sql.Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, realName);
            statement.setString(2, idCardHash);
            statement.setBytes(3, idCardCipher);
            return statement;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("Failed to obtain whitelist record id");
        }
        return key.longValue();
    }

    /**
     * 回滚夹具写入的白名单记录。
     *
     * @param id 记录主键
     */
    public void deleteWhitelist(long id) {
        jdbcTemplate.update("DELETE FROM hospital_identity_whitelist WHERE id = ?", id);
    }
}

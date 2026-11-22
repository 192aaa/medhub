package com.sdas.server.identity.model.converter;

/**
 * 状态枚举的显式转换器。
 *
 * <p>本模块使用 JdbcTemplate，不引入 jakarta.persistence-api（架构评审清单外依赖），
 * 故以与 JPA AttributeConverter 相同方法签名的接口实现显式转换：
 * 不使用 @Enumerated(EnumType.STRING)，且对 null / 未知值一律降级为安全默认值。</p>
 *
 * @param <E> 枚举类型
 */
public interface SafeEnumConverter<E extends Enum<E>> {

    /** @return 枚举类型 */
    Class<E> enumType();

    /** @return 未知值 / null 时的安全默认值 */
    E defaultValue();

    /**
     * 枚举转数据库列值。
     *
     * @param attribute 枚举值，可为 null
     * @return 数据库字符串
     */
    default String convertToDatabaseColumn(E attribute) {
        return attribute == null ? defaultValue().name() : attribute.name();
    }

    /**
     * 数据库列值转枚举，未知值降级为安全默认值。
     *
     * @param dbData 数据库字符串，可为 null
     * @return 枚举值
     */
    default E convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return defaultValue();
        }
        try {
            return Enum.valueOf(enumType(), dbData.trim());
        } catch (IllegalArgumentException exception) {
            return defaultValue();
        }
    }
}

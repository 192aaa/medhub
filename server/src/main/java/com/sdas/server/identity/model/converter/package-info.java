/**
 * 状态枚举的显式 JPA AttributeConverter（autoApply=false）。
 *
 * <p>禁止 @Enumerated(EnumType.STRING)；须处理 null 与未知值的安全降级。</p>
 */
package com.sdas.server.identity.model.converter;

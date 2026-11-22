package com.sdas.server.appointment.entity;

import com.sdas.server.appointment.model.PaymentChannel;
import com.sdas.server.appointment.model.PaymentRecordStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 支付流水实体（MVP 仅 MOCK 渠道）。
 *
 * <p>表结构来源：specs/002-appointment-consultation/data-model.md 2.7。
 * UNIQUE(appointment_id) 保证幂等；out_trade_no 仅为内部流水号，不返回客户端。</p>
 *
 * @param id          主键
 * @param appointmentId 挂号单
 * @param channel     支付渠道
 * @param amount      支付金额
 * @param outTradeNo  内部交易号（禁止返回客户端）
 * @param status      流水状态
 * @param paidAt      支付时间
 * @param createdAt   创建时间
 * @param updatedAt   更新时间
 * @param version     乐观锁
 */
public record PaymentRecord(Long id, Long appointmentId, PaymentChannel channel,
                            BigDecimal amount, String outTradeNo, PaymentRecordStatus status,
                            LocalDateTime paidAt, LocalDateTime createdAt, LocalDateTime updatedAt,
                            int version) {
}

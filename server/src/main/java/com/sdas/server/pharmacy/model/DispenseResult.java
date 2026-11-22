package com.sdas.server.pharmacy.model;

/**
 * 核销结果（派生，不落库）。
 *
 * <p>唯一来源：specs/004-prescription-dispensing/data-model.md 1.3。
 * 重复核销是正常业务分支，返回 200 而非 409。</p>
 */
public enum DispenseResult {
    /** 本次核销成功，写入核销记录（idempotent=false）。 */
    DISPENSED,
    /** 已存在核销记录（重复或并发），未写入（idempotent=true）。 */
    ALREADY_DISPENSED
}

package com.sdas.server.healthconsultation.service;

import com.sdas.server.common.PageResult;
import com.sdas.server.healthconsultation.dto.ClearAllResponse;
import com.sdas.server.healthconsultation.dto.MessageItem;
import com.sdas.server.healthconsultation.dto.SessionCreateResponse;
import com.sdas.server.healthconsultation.dto.SessionDeleteResponse;
import com.sdas.server.healthconsultation.dto.SessionRenameResponse;
import com.sdas.server.healthconsultation.dto.SessionSummary;

/**
 * AI健康咨询会话管理。
 *
 * <p>全部方法以 {@code patientId} 作为唯一归属依据（来自 {@code PatientIdExtractor}），
 * 越权与不存在统一抛 {@code SESSION_NOT_FOUND} 并写 {@code AI_HEALTH_ACCESS_DENIED} 审计。</p>
 */
public interface HealthSessionService {

    /**
     * 新建会话。
     *
     * @param patientId 患者主键
     * @return 会话信息
     */
    SessionCreateResponse create(long patientId);

    /**
     * 分页查询会话列表（最近活跃倒序）。
     *
     * @param patientId 患者主键
     * @param page      页码（从 1 起）
     * @param pageSize  每页条数
     * @return 分页结果
     */
    PageResult<SessionSummary> list(long patientId, int page, int pageSize);

    /**
     * 分页查询会话消息历史。
     *
     * @param patientId 患者主键
     * @param sessionId 会话主键
     * @param page      页码
     * @param pageSize  每页条数
     * @return 分页结果
     */
    PageResult<MessageItem> messages(long patientId, long sessionId, int page, int pageSize);

    /**
     * 重命名会话。
     *
     * @param patientId 患者主键
     * @param sessionId 会话主键
     * @param title     新标题（1–64 字符）
     * @return 重命名结果
     */
    SessionRenameResponse rename(long patientId, long sessionId, String title);

    /**
     * 删除单个会话（逻辑删除 + 清理热缓存）。
     *
     * @param patientId 患者主键
     * @param sessionId 会话主键
     * @return 删除结果
     */
    SessionDeleteResponse delete(long patientId, long sessionId);

    /**
     * 清空患者全部历史（不可恢复，写审计）。
     *
     * @param patientId 患者主键
     * @return 删除数量
     */
    ClearAllResponse clearAll(long patientId);
}

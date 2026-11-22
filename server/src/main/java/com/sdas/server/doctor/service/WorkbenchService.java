package com.sdas.server.doctor.service;

import com.sdas.server.appointment.model.ConsultationStatus;
import com.sdas.server.doctor.dto.AcceptResponse;
import com.sdas.server.doctor.dto.ConsultationDetailResponse;
import com.sdas.server.doctor.dto.ConsultationListItem;
import com.sdas.server.doctor.dto.MessageItem;
import com.sdas.server.doctor.dto.MessageResponse;
import java.util.List;

/**
 * 医生工作台服务（待接诊/已接诊列表、问诊单详情、接诊、对话回复）。
 */
public interface WorkbenchService {

    /**
     * 查询归属医生的问诊单列表。
     *
     * @param doctorId 医生主键
     * @param status   问诊单状态（PENDING/IN_PROGRESS）
     * @return 问诊单列表
     */
    List<ConsultationListItem> list(long doctorId, ConsultationStatus status);

    /**
     * 查询问诊单详情（含图片与 AI 摘要），越权拒绝。
     *
     * @param doctorId       医生主键
     * @param consultationId 问诊单主键
     * @return 问诊单详情
     */
    ConsultationDetailResponse detail(long doctorId, long consultationId);

    /**
     * 接诊（状态 CAS：PENDING → IN_PROGRESS），并发仅一人成功。
     *
     * @param doctorId       医生主键
     * @param consultationId 问诊单主键
     * @return 接诊结果
     */
    AcceptResponse accept(long doctorId, long consultationId);

    /**
     * 查询对话消息（越权拒绝）。
     *
     * @param doctorId       医生主键
     * @param consultationId 问诊单主键
     * @return 消息列表
     */
    List<MessageItem> listMessages(long doctorId, long consultationId);

    /**
     * 发送文字回复（单条 ≤ 1000 字，服务端二次校验）。
     *
     * @param doctorId       医生主键
     * @param consultationId 问诊单主键
     * @param content        回复内容
     * @return 回复回显
     */
    MessageResponse reply(long doctorId, long consultationId, String content);
}

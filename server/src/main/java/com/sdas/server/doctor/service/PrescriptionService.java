package com.sdas.server.doctor.service;

import com.sdas.server.doctor.dto.PrescriptionCreateRequest;
import com.sdas.server.doctor.dto.PrescriptionDetailResponse;
import com.sdas.server.doctor.dto.PrescriptionResponse;

/**
 * 电子处方服务（开具、查询）。
 */
public interface PrescriptionService {

    /**
     * 开具电子处方（从药品目录选药 → 服务端签名 → 生成二维码与水印），一张问诊单仅一张处方。
     *
     * @param doctorId       医生主键
     * @param consultationId 问诊单主键
     * @param request        处方请求
     * @return 处方结果
     */
    PrescriptionResponse create(long doctorId, long consultationId, PrescriptionCreateRequest request);

    /**
     * 查询处方详情（越权拒绝）。
     *
     * @param doctorId       医生主键
     * @param prescriptionId 处方主键
     * @return 处方详情
     */
    PrescriptionDetailResponse detail(long doctorId, long prescriptionId);
}

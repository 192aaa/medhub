package com.sdas.server.appointment.service;

import com.sdas.server.appointment.dto.DepartmentResponse;
import com.sdas.server.appointment.dto.DoctorDetailResponse;
import com.sdas.server.appointment.dto.DoctorResponse;
import java.util.List;

/** 科室与医生只读查询服务（端点 1/2/3）。 */
public interface DepartmentReadService {

    /** @return 启用科室列表 */
    List<DepartmentResponse> listDepartments();

    /**
     * 按科室与在线状态筛选医生。
     *
     * @param departmentId 科室（可空）
     * @param online       在线状态（可空）
     * @return 医生列表
     */
    List<DoctorResponse> listDoctors(Long departmentId, String online);

    /**
     * 查询医生详情（含排班）。
     *
     * @param doctorId 医生主键
     * @return 医生详情
     */
    DoctorDetailResponse detail(long doctorId);
}

package com.sdas.server.appointment.controller;

import com.sdas.server.appointment.dto.DoctorDetailResponse;
import com.sdas.server.appointment.dto.DoctorResponse;
import com.sdas.server.appointment.service.DepartmentReadService;
import com.sdas.server.common.Result;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 端点 2/3：医生列表与详情（无需认证）。 */
@RestController
@RequestMapping("/api/v1/doctors")
public class DoctorController {

    private final DepartmentReadService departmentReadService;

    /**
     * 构造医生控制器。
     *
     * @param departmentReadService 科室与医生读服务
     */
    public DoctorController(DepartmentReadService departmentReadService) {
        this.departmentReadService = departmentReadService;
    }

    /**
     * 医生列表（可按科室 / 在线状态筛选）。
     *
     * @param departmentId 科室（可空）
     * @param online       在线状态（可空）
     * @return 医生列表
     */
    @GetMapping
    public Result<List<DoctorResponse>> list(@RequestParam(required = false) Long departmentId,
                                           @RequestParam(required = false) String online) {
        return Result.success(departmentReadService.listDoctors(departmentId, online));
    }

    /**
     * 医生详情（含排班）。
     *
     * @param doctorId 医生主键
     * @return 医生详情
     */
    @GetMapping("/{doctorId}")
    public Result<DoctorDetailResponse> detail(@PathVariable long doctorId) {
        return Result.success(departmentReadService.detail(doctorId));
    }
}

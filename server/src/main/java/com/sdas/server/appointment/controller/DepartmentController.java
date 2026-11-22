package com.sdas.server.appointment.controller;

import com.sdas.server.appointment.dto.DepartmentResponse;
import com.sdas.server.appointment.service.DepartmentReadService;
import com.sdas.server.common.Result;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 端点 1：科室列表（无需认证）。 */
@RestController
@RequestMapping("/api/v1/departments")
public class DepartmentController {

    private final DepartmentReadService departmentReadService;

    /**
     * 构造科室控制器。
     *
     * @param departmentReadService 科室读服务
     */
    public DepartmentController(DepartmentReadService departmentReadService) {
        this.departmentReadService = departmentReadService;
    }

    /** @return 启用科室列表 */
    @GetMapping
    public Result<List<DepartmentResponse>> list() {
        return Result.success(departmentReadService.listDepartments());
    }
}

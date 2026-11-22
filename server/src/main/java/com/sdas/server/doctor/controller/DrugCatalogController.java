package com.sdas.server.doctor.controller;

import com.sdas.server.common.BusinessException;
import com.sdas.server.common.DoctorIdExtractor;
import com.sdas.server.common.ErrorCode;
import com.sdas.server.common.Result;
import com.sdas.server.doctor.dto.DrugListResponse;
import com.sdas.server.doctor.dto.DrugView;
import com.sdas.server.doctor.entity.DrugCatalog;
import com.sdas.server.doctor.repository.DrugCatalogRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 端点 5：药品目录分页查询（支撑用药草稿展示与手工替换，R-09）。
 *
 * <p>只读查询 {@code drug_catalog} 中 {@code enabled=1} 的药品；越界参数复用既有
 * {@code BAD_REQUEST}，不新增专属错误码。</p>
 */
@RestController
@RequestMapping("/api/v1/workbench")
public class DrugCatalogController {

    private static final int MAX_KEYWORD_LENGTH = 50;

    private static final int MAX_PAGE_SIZE = 50;

    private static final int MAX_PAGE = 100_000;

    private final DrugCatalogRepository drugCatalogRepository;

    private final DoctorIdExtractor doctorIdExtractor;

    /**
     * 构造控制器。
     *
     * @param drugCatalogRepository 药品目录只读仓储
     * @param doctorIdExtractor     医生身份提取器
     */
    public DrugCatalogController(DrugCatalogRepository drugCatalogRepository,
                                 DoctorIdExtractor doctorIdExtractor) {
        this.drugCatalogRepository = drugCatalogRepository;
        this.doctorIdExtractor = doctorIdExtractor;
    }

    /**
     * 查询启用药品分页。
     *
     * @param keyword  名称关键字（≤50 字符，可空）
     * @param page     页码（≥1）
     * @param pageSize 每页条数（1–50）
     * @param request  HTTP 请求
     * @return 分页结果
     */
    @GetMapping("/drugs")
    public Result<DrugListResponse> list(@RequestParam(required = false) String keyword,
                                         @RequestParam(defaultValue = "1") int page,
                                         @RequestParam(defaultValue = "20") int pageSize,
                                         HttpServletRequest request) {
        doctorIdExtractor.extract(request);
        String normalized = normalize(keyword);
        validate(normalized, page, pageSize);
        int offset = (page - 1) * pageSize;
        List<DrugView> list = drugCatalogRepository.findEnabledPage(normalized, offset, pageSize)
                .stream().map(DrugCatalogController::view).toList();
        return Result.success(new DrugListResponse(list,
                drugCatalogRepository.countEnabled(normalized), page, pageSize));
    }

    private static String normalize(String keyword) {
        return keyword == null ? null : keyword.trim();
    }

    private static void validate(String keyword, int page, int pageSize) {
        if (keyword != null && keyword.length() > MAX_KEYWORD_LENGTH) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }
        if (page < 1 || page > MAX_PAGE || pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }
    }

    private static DrugView view(DrugCatalog drug) {
        return new DrugView(drug.id(), drug.name(), drug.spec(), drug.unit(),
                drug.prescriptionOnly());
    }
}

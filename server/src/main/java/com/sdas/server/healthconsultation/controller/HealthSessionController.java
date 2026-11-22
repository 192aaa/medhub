package com.sdas.server.healthconsultation.controller;

import com.sdas.server.common.PageResult;
import com.sdas.server.common.PatientIdExtractor;
import com.sdas.server.common.Result;
import com.sdas.server.healthconsultation.dto.ClearAllResponse;
import com.sdas.server.healthconsultation.dto.MessageItem;
import com.sdas.server.healthconsultation.dto.SessionCreateResponse;
import com.sdas.server.healthconsultation.dto.SessionDeleteResponse;
import com.sdas.server.healthconsultation.dto.SessionRenameRequest;
import com.sdas.server.healthconsultation.dto.SessionRenameResponse;
import com.sdas.server.healthconsultation.dto.SessionSummary;
import com.sdas.server.healthconsultation.service.HealthSessionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI健康咨询会话管理端点（契约端点 1–6）。
 *
 * <p>患者身份统一由 {@link PatientIdExtractor} 从令牌解析，Controller 不得自解析；
 * 越权与不存在同码（{@code SESSION_NOT_FOUND}）。</p>
 */
@RestController
@RequestMapping("/api/v1/health-consultation/sessions")
public class HealthSessionController {

    private final HealthSessionService sessionService;

    private final PatientIdExtractor patientIdExtractor;

    /**
     * 构造会话控制器。
     *
     * @param sessionService     会话服务
     * @param patientIdExtractor 患者身份提取器
     */
    public HealthSessionController(HealthSessionService sessionService,
                                   PatientIdExtractor patientIdExtractor) {
        this.sessionService = sessionService;
        this.patientIdExtractor = patientIdExtractor;
    }

    /**
     * 会话列表（最近活跃倒序）。
     *
     * @param page       页码
     * @param pageSize   每页条数
     * @param httpRequest 请求
     * @return 分页结果
     */
    @GetMapping
    public Result<PageResult<SessionSummary>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            HttpServletRequest httpRequest) {
        long patientId = patientIdExtractor.extract(httpRequest).patientId();
        return Result.success(sessionService.list(patientId, page, pageSize));
    }

    /**
     * 新建会话。
     *
     * @param httpRequest 请求
     * @return 会话信息
     */
    @PostMapping
    public Result<SessionCreateResponse> create(HttpServletRequest httpRequest) {
        long patientId = patientIdExtractor.extract(httpRequest).patientId();
        return Result.success(sessionService.create(patientId));
    }

    /**
     * 重命名会话。
     *
     * @param sessionId   会话主键
     * @param request     请求体
     * @param httpRequest 请求
     * @return 重命名结果
     */
    @PatchMapping("/{sessionId}")
    public Result<SessionRenameResponse> rename(@PathVariable long sessionId,
                                                @Valid @RequestBody SessionRenameRequest request,
                                                HttpServletRequest httpRequest) {
        long patientId = patientIdExtractor.extract(httpRequest).patientId();
        return Result.success(sessionService.rename(patientId, sessionId, request.title()));
    }

    /**
     * 删除单个会话。
     *
     * @param sessionId   会话主键
     * @param httpRequest 请求
     * @return 删除结果
     */
    @DeleteMapping("/{sessionId}")
    public Result<SessionDeleteResponse> delete(@PathVariable long sessionId,
                                                HttpServletRequest httpRequest) {
        long patientId = patientIdExtractor.extract(httpRequest).patientId();
        return Result.success(sessionService.delete(patientId, sessionId));
    }

    /**
     * 清空全部历史。
     *
     * @param httpRequest 请求
     * @return 删除数量
     */
    @DeleteMapping
    public Result<ClearAllResponse> clearAll(HttpServletRequest httpRequest) {
        long patientId = patientIdExtractor.extract(httpRequest).patientId();
        return Result.success(sessionService.clearAll(patientId));
    }

    /**
     * 会话消息历史。
     *
     * @param sessionId   会话主键
     * @param page        页码
     * @param pageSize    每页条数
     * @param httpRequest 请求
     * @return 分页结果
     */
    @GetMapping("/{sessionId}/messages")
    public Result<PageResult<MessageItem>> messages(
            @PathVariable long sessionId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int pageSize,
            HttpServletRequest httpRequest) {
        long patientId = patientIdExtractor.extract(httpRequest).patientId();
        return Result.success(sessionService.messages(patientId, sessionId, page, pageSize));
    }
}

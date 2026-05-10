package com.recommend.shop.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.recommend.shop.common.PageResult;
import com.recommend.shop.entity.AdminLog;
import com.recommend.shop.mapper.AdminLogMapper;
import com.recommend.shop.util.TimeUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AdminLogService {

    private final AdminLogMapper adminLogMapper;

    public AdminLog recordLog(
        Long adminId,
        String adminName,
        String module,
        String action,
        Long targetId,
        String targetType,
        String content,
        HttpServletRequest request,
        String requestParams
    ) {
        AdminLog log = new AdminLog();
        log.setAdminId(adminId == null ? 0L : adminId);
        log.setAdminName(adminName == null || adminName.isBlank() ? "system" : adminName);
        log.setModule(module);
        log.setAction(action);
        log.setTargetId(targetId);
        log.setTargetType(targetType);
        log.setContent(content);
        log.setRequestUrl(request == null ? null : request.getRequestURI());
        log.setRequestMethod(request == null ? null : request.getMethod());
        log.setRequestParams(requestParams);
        log.setIpAddress(request == null ? null : request.getRemoteAddr());
        log.setUserAgent(request == null ? null : request.getHeader("User-Agent"));
        log.setCreatedAt(TimeUtils.utcNow());
        adminLogMapper.insert(log);
        return log;
    }

    public PageResult<AdminLog> listLogs(int page, int pageSize, String module, String action, Long adminId) {
        LambdaQueryWrapper<AdminLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(module != null && !module.isBlank(), AdminLog::getModule, module)
            .eq(action != null && !action.isBlank(), AdminLog::getAction, action)
            .eq(adminId != null, AdminLog::getAdminId, adminId)
            .orderByDesc(AdminLog::getId);
        Page<AdminLog> pager = adminLogMapper.selectPage(new Page<>(page, pageSize), wrapper);
        return new PageResult<>(pager.getTotal(), pager.getRecords());
    }
}

package com.recommend.shop.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.recommend.shop.common.PageResult;
import com.recommend.shop.common.ServiceResult;
import com.recommend.shop.entity.Admin;
import com.recommend.shop.mapper.AdminMapper;
import com.recommend.shop.security.PythonPasswordHasher;
import com.recommend.shop.util.TimeUtils;
import com.recommend.shop.util.TypeUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class AdminService {

    private final AdminMapper adminMapper;
    private final PythonPasswordHasher passwordHasher;
    private final JdbcTemplate jdbcTemplate;

    public ServiceResult<Admin> authenticateAdmin(String username, String password, String loginIp) {
        LambdaQueryWrapper<Admin> wrapper = Wrappers.<Admin>lambdaQuery()
            .eq(Admin::getUsername, username)
            .eq(Admin::getIsDeleted, 0)
            .eq(Admin::getStatus, 1)
            .orderByDesc(Admin::getId)
            .last("limit 1");
        Admin admin = adminMapper.selectOne(wrapper);
        if (admin == null) {
            return ServiceResult.fail("not_found");
        }
        if (!passwordHasher.verify(password, admin.getPassword())) {
            return ServiceResult.fail("invalid_password");
        }
        admin.setLastLoginTime(TimeUtils.utcNow());
        admin.setLastLoginIp(loginIp);
        adminMapper.updateById(admin);
        return ServiceResult.ok(admin);
    }

    public PageResult<Admin> listAdmins(int page, int pageSize, String keyword, Integer status, Integer role) {
        LambdaQueryWrapper<Admin> wrapper = Wrappers.<Admin>lambdaQuery()
            .eq(Admin::getIsDeleted, 0)
            .like(keyword != null && !keyword.isBlank(), Admin::getUsername, keyword)
            .eq(status != null, Admin::getStatus, status)
            .eq(role != null, Admin::getRole, role)
            .orderByDesc(Admin::getId);
        Page<Admin> pager = adminMapper.selectPage(new Page<>(page, pageSize), wrapper);
        return new PageResult<>(pager.getTotal(), pager.getRecords());
    }

    public ServiceResult<Admin> createAdmin(Map<String, Object> payload) {
        String username = TypeUtils.toStr(payload.get("username"));
        Admin exists = adminMapper.selectOne(
            Wrappers.<Admin>lambdaQuery()
                .eq(Admin::getUsername, username)
                .eq(Admin::getIsDeleted, 0)
                .last("limit 1")
        );
        if (exists != null) {
            return ServiceResult.fail("username_exists");
        }
        Admin admin = new Admin();
        admin.setUsername(username);
        String password = TypeUtils.toStr(payload.get("password"));
        admin.setPassword(passwordHasher.hash(password == null || password.isBlank() ? "123456" : password));
        admin.setRealName(TypeUtils.toStr(payload.get("real_name")));
        admin.setEmail(TypeUtils.toStr(payload.get("email")));
        admin.setPhone(TypeUtils.toStr(payload.get("phone")));
        admin.setAvatar(TypeUtils.toStr(payload.get("avatar")));
        admin.setRole(TypeUtils.toInt(payload.get("role")) == null ? 1 : TypeUtils.toInt(payload.get("role")));
        admin.setStatus(TypeUtils.toInt(payload.get("status")) == null ? 1 : TypeUtils.toInt(payload.get("status")));
        admin.setCreatedAt(TimeUtils.utcNow());
        admin.setUpdatedAt(TimeUtils.utcNow());
        admin.setIsDeleted(0);
        adminMapper.insert(admin);
        return ServiceResult.ok(admin);
    }

    public Admin updateAdmin(Admin admin, Map<String, Object> payload) {
        applyAdminPatch(admin, payload, true);
        admin.setUpdatedAt(TimeUtils.utcNow());
        adminMapper.updateById(admin);
        return admin;
    }

    public Admin toggleAdminStatus(Admin admin) {
        admin.setStatus(admin.getStatus() != null && admin.getStatus() == 1 ? 0 : 1);
        admin.setUpdatedAt(TimeUtils.utcNow());
        adminMapper.updateById(admin);
        return admin;
    }

    public Admin resetAdminPassword(Admin admin) {
        admin.setPassword(passwordHasher.hash("123456"));
        admin.setUpdatedAt(TimeUtils.utcNow());
        adminMapper.updateById(admin);
        return admin;
    }

    public Admin softDeleteAdmin(Admin admin) {
        admin.setIsDeleted(1);
        admin.setUpdatedAt(TimeUtils.utcNow());
        adminMapper.updateById(admin);
        return admin;
    }

    public Admin getAdminById(Long adminId, boolean includeDeleted) {
        LambdaQueryWrapper<Admin> wrapper = Wrappers.<Admin>lambdaQuery()
            .eq(Admin::getId, adminId)
            .last("limit 1");
        if (!includeDeleted) {
            wrapper.eq(Admin::getIsDeleted, 0);
        }
        return adminMapper.selectOne(wrapper);
    }

    public Admin ensureDefaultAdmin() {
        try {
            Long count = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM admin", Long.class);
            if (count != null && count > 0) {
                return adminMapper.selectOne(
                    Wrappers.<Admin>lambdaQuery()
                        .orderByAsc(Admin::getId)
                        .last("limit 1")
                );
            }
        } catch (Exception ignored) {
            return null;
        }
        Admin admin = new Admin();
        admin.setUsername("admin");
        admin.setPassword(passwordHasher.hash("admin123"));
        admin.setRole(2);
        admin.setStatus(1);
        admin.setCreatedAt(TimeUtils.utcNow());
        admin.setUpdatedAt(TimeUtils.utcNow());
        admin.setIsDeleted(0);
        adminMapper.insert(admin);
        return admin;
    }

    private void applyAdminPatch(Admin admin, Map<String, Object> payload, boolean allowPassword) {
        if (payload.containsKey("password") && allowPassword) {
            String password = TypeUtils.toStr(payload.get("password"));
            if (password != null && !password.isBlank()) {
                admin.setPassword(passwordHasher.hash(password));
            }
        }
        if (payload.containsKey("real_name")) {
            admin.setRealName(TypeUtils.toStr(payload.get("real_name")));
        }
        if (payload.containsKey("email")) {
            admin.setEmail(TypeUtils.toStr(payload.get("email")));
        }
        if (payload.containsKey("phone")) {
            admin.setPhone(TypeUtils.toStr(payload.get("phone")));
        }
        if (payload.containsKey("avatar")) {
            admin.setAvatar(TypeUtils.toStr(payload.get("avatar")));
        }
        if (payload.containsKey("role")) {
            Integer role = TypeUtils.toInt(payload.get("role"));
            admin.setRole(role);
        }
        if (payload.containsKey("status")) {
            Integer status = TypeUtils.toInt(payload.get("status"));
            admin.setStatus(status);
        }
    }
}

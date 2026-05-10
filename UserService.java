package com.recommend.shop.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.recommend.shop.common.PageResult;
import com.recommend.shop.common.ServiceResult;
import com.recommend.shop.entity.User;
import com.recommend.shop.entity.UserAddress;
import com.recommend.shop.mapper.UserAddressMapper;
import com.recommend.shop.mapper.UserMapper;
import com.recommend.shop.security.PythonPasswordHasher;
import com.recommend.shop.util.TimeUtils;
import com.recommend.shop.util.TypeUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class UserService {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final UserMapper userMapper;
    private final UserAddressMapper userAddressMapper;
    private final PythonPasswordHasher passwordHasher;

    public String normalizeAvatarPath(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        String path = value;
        if (value.startsWith("http://") || value.startsWith("https://")) {
            try {
                URI uri = URI.create(value);
                path = uri.getPath();
            } catch (Exception ignored) {
                path = value;
            }
        }
        if (path.startsWith("/")) {
            path = path.substring(1);
        }
        if (path.startsWith("static/")) {
            path = path.substring("static/".length());
        }
        return path;
    }

    public ServiceResult<User> registerUser(Map<String, Object> payload) {
        LocalDate birthday = parseBirthday(payload.get("birthday"));
        String username = TypeUtils.toStr(payload.get("username"));

        User exists = userMapper.selectOne(
            Wrappers.<User>lambdaQuery()
                .eq(User::getUsername, username)
                .eq(User::getIsDeleted, 0)
                .last("limit 1")
        );
        if (exists != null) {
            return ServiceResult.fail("username_exists");
        }

        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordHasher.hash(TypeUtils.toStr(payload.get("password"))));
        user.setNickname(TypeUtils.toStr(payload.get("nickname")) == null ? username : TypeUtils.toStr(payload.get("nickname")));
        user.setEmail(TypeUtils.toStr(payload.get("email")));
        user.setPhone(TypeUtils.toStr(payload.get("phone")));
        user.setAvatar(normalizeAvatarPath(TypeUtils.toStr(payload.get("avatar"))));
        user.setGender(TypeUtils.toInt(payload.get("gender")) == null ? 0 : TypeUtils.toInt(payload.get("gender")));
        user.setBirthday(birthday);
        user.setStatus(TypeUtils.toInt(payload.get("status")) == null ? 1 : TypeUtils.toInt(payload.get("status")));
        user.setLastLoginTime(TimeUtils.utcNow());
        user.setLastLoginIp(TypeUtils.toStr(payload.get("login_ip")));
        user.setCreatedAt(TimeUtils.utcNow());
        user.setUpdatedAt(TimeUtils.utcNow());
        user.setIsDeleted(0);
        userMapper.insert(user);
        return ServiceResult.ok(user);
    }

    public ServiceResult<User> authenticateUser(String identifier, String password, String loginIp) {
        LambdaQueryWrapper<User> wrapper = Wrappers.<User>lambdaQuery()
            .eq(User::getIsDeleted, 0)
            .eq(User::getStatus, 1)
            .and(q -> q.eq(User::getUsername, identifier).or().eq(User::getPhone, identifier).or().eq(User::getEmail, identifier))
            .orderByDesc(User::getId)
            .last("limit 1");
        User user = userMapper.selectOne(wrapper);
        if (user == null) {
            return ServiceResult.fail("not_found");
        }
        if (!passwordHasher.verify(password, user.getPassword())) {
            return ServiceResult.fail("invalid_password");
        }
        user.setLastLoginTime(TimeUtils.utcNow());
        user.setLastLoginIp(loginIp);
        userMapper.updateById(user);
        return ServiceResult.ok(user);
    }

    public User getUserById(Long userId, boolean includeDeleted) {
        LambdaQueryWrapper<User> wrapper = Wrappers.<User>lambdaQuery()
            .eq(User::getId, userId)
            .last("limit 1");
        if (!includeDeleted) {
            wrapper.eq(User::getIsDeleted, 0);
        }
        return userMapper.selectOne(wrapper);
    }

    public User updateUser(User user, Map<String, Object> patch) {
        if (patch.containsKey("birthday")) {
            user.setBirthday(parseBirthday(patch.get("birthday")));
        }
        if (patch.containsKey("avatar")) {
            user.setAvatar(normalizeAvatarPath(TypeUtils.toStr(patch.get("avatar"))));
        }
        if (patch.containsKey("password")) {
            String password = TypeUtils.toStr(patch.get("password"));
            if (password != null && !password.isBlank()) {
                user.setPassword(passwordHasher.hash(password));
            }
        }
        if (patch.containsKey("nickname")) {
            user.setNickname(TypeUtils.toStr(patch.get("nickname")));
        }
        if (patch.containsKey("email")) {
            user.setEmail(TypeUtils.toStr(patch.get("email")));
        }
        if (patch.containsKey("phone")) {
            user.setPhone(TypeUtils.toStr(patch.get("phone")));
        }
        if (patch.containsKey("gender")) {
            user.setGender(TypeUtils.toInt(patch.get("gender")));
        }
        if (patch.containsKey("status")) {
            user.setStatus(TypeUtils.toInt(patch.get("status")));
        }
        user.setUpdatedAt(TimeUtils.utcNow());
        userMapper.updateById(user);
        return user;
    }

    public PageResult<User> listUsers(String keyword, Integer status, int page, int pageSize) {
        LambdaQueryWrapper<User> wrapper = Wrappers.<User>lambdaQuery()
            .eq(User::getIsDeleted, 0);
        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(q -> q.like(User::getUsername, keyword).or().like(User::getPhone, keyword).or().like(User::getEmail, keyword));
        }
        if (status != null) {
            wrapper.eq(User::getStatus, status);
        }
        wrapper.orderByDesc(User::getId);
        Page<User> pager = userMapper.selectPage(new Page<>(page, pageSize), wrapper);
        return new PageResult<>(pager.getTotal(), pager.getRecords());
    }

    public User setUserStatus(User user, Integer status) {
        user.setStatus(status);
        user.setUpdatedAt(TimeUtils.utcNow());
        userMapper.updateById(user);
        return user;
    }

    public User softDeleteUser(User user) {
        user.setIsDeleted(1);
        user.setUpdatedAt(TimeUtils.utcNow());
        userMapper.updateById(user);
        return user;
    }

    public java.util.List<UserAddress> getAddresses(Long userId) {
        return userAddressMapper.selectList(
            Wrappers.<UserAddress>lambdaQuery()
                .eq(UserAddress::getUserId, userId)
                .eq(UserAddress::getIsDeleted, 0)
                .orderByDesc(UserAddress::getIsDefault)
                .orderByDesc(UserAddress::getId)
        );
    }

    public UserAddress getAddressById(Long userId, Long addressId) {
        return userAddressMapper.selectOne(
            Wrappers.<UserAddress>lambdaQuery()
                .eq(UserAddress::getUserId, userId)
                .eq(UserAddress::getId, addressId)
                .eq(UserAddress::getIsDeleted, 0)
                .last("limit 1")
        );
    }

    @Transactional
    public UserAddress addAddress(Long userId, Map<String, Object> payload) {
        if (Boolean.TRUE.equals(TypeUtils.toBool(payload.get("is_default")))) {
            userAddressMapper.update(
                null,
                Wrappers.<UserAddress>lambdaUpdate()
                    .eq(UserAddress::getUserId, userId)
                    .set(UserAddress::getIsDefault, 0)
            );
        }
        UserAddress address = new UserAddress();
        address.setUserId(userId);
        address.setReceiverName(TypeUtils.toStr(payload.get("receiver_name")));
        address.setReceiverPhone(TypeUtils.toStr(payload.get("receiver_phone")));
        address.setProvince(TypeUtils.toStr(payload.get("province")));
        address.setCity(TypeUtils.toStr(payload.get("city")));
        address.setDistrict(TypeUtils.toStr(payload.get("district")));
        address.setDetailAddress(TypeUtils.toStr(payload.get("detail_address")));
        address.setPostalCode(TypeUtils.toStr(payload.get("postal_code")));
        address.setIsDefault(Boolean.TRUE.equals(TypeUtils.toBool(payload.get("is_default"))) ? 1 : 0);
        address.setCreatedAt(TimeUtils.utcNow());
        address.setUpdatedAt(TimeUtils.utcNow());
        address.setIsDeleted(0);
        userAddressMapper.insert(address);
        return address;
    }

    @Transactional
    public UserAddress updateAddress(UserAddress address, Map<String, Object> payload) {
        if (Boolean.TRUE.equals(TypeUtils.toBool(payload.get("is_default")))) {
            userAddressMapper.update(
                null,
                Wrappers.<UserAddress>lambdaUpdate()
                    .eq(UserAddress::getUserId, address.getUserId())
                    .set(UserAddress::getIsDefault, 0)
            );
        }
        if (payload.containsKey("receiver_name")) {
            address.setReceiverName(TypeUtils.toStr(payload.get("receiver_name")));
        }
        if (payload.containsKey("receiver_phone")) {
            address.setReceiverPhone(TypeUtils.toStr(payload.get("receiver_phone")));
        }
        if (payload.containsKey("province")) {
            address.setProvince(TypeUtils.toStr(payload.get("province")));
        }
        if (payload.containsKey("city")) {
            address.setCity(TypeUtils.toStr(payload.get("city")));
        }
        if (payload.containsKey("district")) {
            address.setDistrict(TypeUtils.toStr(payload.get("district")));
        }
        if (payload.containsKey("detail_address")) {
            address.setDetailAddress(TypeUtils.toStr(payload.get("detail_address")));
        }
        if (payload.containsKey("postal_code")) {
            address.setPostalCode(TypeUtils.toStr(payload.get("postal_code")));
        }
        if (payload.containsKey("is_default")) {
            address.setIsDefault(Boolean.TRUE.equals(TypeUtils.toBool(payload.get("is_default"))) ? 1 : 0);
        }
        address.setUpdatedAt(TimeUtils.utcNow());
        userAddressMapper.updateById(address);
        return address;
    }

    public UserAddress deleteAddress(UserAddress address) {
        address.setIsDeleted(1);
        address.setUpdatedAt(TimeUtils.utcNow());
        userAddressMapper.updateById(address);
        return address;
    }

    @Transactional
    public UserAddress setDefaultAddress(Long userId, Long addressId) {
        userAddressMapper.update(
            null,
            Wrappers.<UserAddress>lambdaUpdate()
                .eq(UserAddress::getUserId, userId)
                .set(UserAddress::getIsDefault, 0)
        );

        UserAddress address = userAddressMapper.selectOne(
            Wrappers.<UserAddress>lambdaQuery()
                .eq(UserAddress::getId, addressId)
                .eq(UserAddress::getUserId, userId)
                .eq(UserAddress::getIsDeleted, 0)
                .last("limit 1")
        );
        if (address == null) {
            return null;
        }
        address.setIsDefault(1);
        address.setUpdatedAt(TimeUtils.utcNow());
        userAddressMapper.updateById(address);
        return address;
    }

    private LocalDate parseBirthday(Object raw) {
        if (raw == null) {
            return null;
        }
        String text = TypeUtils.toStr(raw);
        if (text == null || text.trim().isEmpty()) {
            return null;
        }
        try {
            return LocalDate.parse(text.trim(), DATE_FORMATTER);
        } catch (DateTimeParseException ex) {
            return null;
        }
    }
}

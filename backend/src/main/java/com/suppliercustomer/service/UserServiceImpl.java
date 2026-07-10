package com.suppliercustomer.service;

import cn.dev33.satoken.stp.StpUtil;
import com.suppliercustomer.exception.CustomException;
import com.suppliercustomer.mapper.UserMapper;
import com.suppliercustomer.pojo.LoginRequest;
import com.suppliercustomer.pojo.LoginUser;
import com.suppliercustomer.pojo.PasswordRequest;
import com.suppliercustomer.pojo.ResultCodeEnum;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class UserServiceImpl implements UserService {
    @Autowired
    private UserMapper userMapper;

    @Override
    public Map<String, Object> login(LoginRequest loginRequest) {
        LoginUser dbUser = userMapper.findByUsername(loginRequest.getUsername());
        if (dbUser == null || Boolean.FALSE.equals(dbUser.getEnabled())) {
            throw new CustomException("账号、密码或登录入口不正确");
        }
        if (!dbUser.getRole().equals(loginRequest.getRole())) {
            throw new CustomException("账号、密码或登录入口不正确");
        }
        if (!passwordMatch(loginRequest.getPassword(), dbUser)) {
            throw new CustomException("账号、密码或登录入口不正确");
        }
        StpUtil.login(dbUser.getId());
        String token = StpUtil.getTokenValue();
        dbUser.setToken(token);
        hidePassword(dbUser);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("token", token);
        result.put("user", dbUser);
        return result;
    }

    @Override
    public LoginUser currentUser(String authorization) {
        String token = readToken(authorization);
        Object loginId = StpUtil.getLoginIdByToken(token);
        if (loginId == null) {
            throw new CustomException(ResultCodeEnum.TOKEN_INVALID_ERROR);
        }
        LoginUser user = userMapper.findById(Long.valueOf(String.valueOf(loginId)));
        if (user == null) {
            throw new CustomException(ResultCodeEnum.TOKEN_INVALID_ERROR);
        }
        hidePassword(user);
        return user;
    }

    @Override
    public LoginUser requireRole(String authorization, String role) {
        LoginUser user = currentUser(authorization);
        if (!role.equals(user.getRole())) {
            throw new CustomException(ResultCodeEnum.NO_PERMISSION);
        }
        return user;
    }

    @Override
    public void changePassword(String authorization, PasswordRequest passwordRequest) {
        LoginUser user = currentUser(authorization);
        setEncryptPassword(user, passwordRequest.getNewPassword());
        userMapper.updatePassword(user);
    }

    @Override
    public List<LoginUser> list() {
        List<LoginUser> users = userMapper.list();
        for (LoginUser user : users) {
            hidePassword(user);
        }
        return users;
    }

    @Override
    public void add(LoginUser user) {
        if (userMapper.findByUsername(user.getUsername()) != null) {
            throw new CustomException("账号已存在");
        }
        if (user.getEnabled() == null) {
            user.setEnabled(true);
        }
        user.setPermissions(defaultPermissions(user.getRole()));
        setEncryptPassword(user, user.getPassword());
        userMapper.insert(user);
    }

    @Override
    public void update(LoginUser user) {
        user.setPermissions(defaultPermissions(user.getRole()));
        userMapper.updateBase(user);
        if (user.getPassword() != null && !"".equals(user.getPassword())) {
            setEncryptPassword(user, user.getPassword());
            userMapper.updatePassword(user);
        }
    }

    @Override
    public void delete(Long id) {
        userMapper.delete(id);
    }

    private boolean passwordMatch(String rawPassword, LoginUser dbUser) {
        if (dbUser.getSalt() == null || "".equals(dbUser.getSalt())) {
            return dbUser.getPassword().equals(rawPassword);
        }
        return DigestUtils.md5Hex(rawPassword + dbUser.getSalt()).equals(dbUser.getPassword());
    }

    private void setEncryptPassword(LoginUser user, String rawPassword) {
        String salt = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        user.setSalt(salt);
        user.setPassword(DigestUtils.md5Hex(rawPassword + salt));
    }

    private String readToken(String authorization) {
        if (authorization == null || "".equals(authorization)) {
            throw new CustomException(ResultCodeEnum.TOKEN_INVALID_ERROR);
        }
        if (authorization.startsWith("Bearer ")) {
            return authorization.substring(7);
        }
        return authorization;
    }

    private void hidePassword(LoginUser user) {
        user.setPassword(null);
        user.setSalt(null);
    }

    private String defaultPermissions(String role) {
        if ("ADMIN".equals(role)) {
            return "ALL";
        }
        if ("SUPPLIER".equals(role)) {
            return "SUBMIT_PRODUCT,QUOTE";
        }
        if ("CUSTOMER".equals(role)) {
            return "UPLOAD_ORDER";
        }
        return "";
    }
}

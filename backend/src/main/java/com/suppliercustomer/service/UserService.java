package com.suppliercustomer.service;

import com.suppliercustomer.pojo.LoginRequest;
import com.suppliercustomer.pojo.LoginUser;
import com.suppliercustomer.pojo.PasswordRequest;

import java.util.List;
import java.util.Map;

public interface UserService {
    Map<String, Object> login(LoginRequest loginRequest);

    LoginUser currentUser(String authorization);

    LoginUser requireRole(String authorization, String role);

    void changePassword(String authorization, PasswordRequest passwordRequest);

    List<LoginUser> list();

    void add(LoginUser user);

    void update(LoginUser user);

    void delete(Long id);
}

package com.suppliercustomer.mapper;

import com.suppliercustomer.pojo.LoginUser;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface UserMapper {
    @Select("select * from users where username=#{username}")
    LoginUser findByUsername(String username);

    @Select("select * from users where id=#{id}")
    LoginUser findById(Long id);

    @Select("select id,username,role,permissions,supplier_level,customer_level,enabled,created_at,updated_at from users order by id desc")
    List<LoginUser> list();

    @Insert("insert into users(username,password,salt,role,permissions,supplier_level,customer_level,enabled) values(#{username},#{password},#{salt},#{role},#{permissions},#{supplierLevel},#{customerLevel},#{enabled})")
    void insert(LoginUser user);

    @Update("update users set role=#{role},permissions=#{permissions},supplier_level=#{supplierLevel},customer_level=#{customerLevel},enabled=#{enabled},updated_at=current_timestamp where id=#{id}")
    void updateBase(LoginUser user);

    @Update("update users set password=#{password},salt=#{salt},updated_at=current_timestamp where id=#{id}")
    void updatePassword(LoginUser user);

    @Delete("delete from users where id=#{id}")
    void delete(Long id);
}

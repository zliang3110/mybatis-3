package org.apache.ibatis.examples.mapper;


import org.apache.ibatis.examples.entity.User;

import java.util.List;

public interface UserMapper {

  public List<User> selectAll();
}

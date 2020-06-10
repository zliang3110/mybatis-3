package org.apache.ibatis;



import org.apache.ibatis.datasource.pooled.PooledDataSource;
import org.apache.ibatis.domain.blog.mappers.BlogMapper;

import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.InputStream;


/**
 * Created by zhangll on 2020/6/6.
 */
public class Mytest {

  public static void main(String[] args) {

    DataSource dataSource = new PooledDataSource(
      "com.mysql.cj.jdbc.Driver",
      "jdbc:mysql://localhost:3306/user?useUnicode=true&characterEncoding=utf-8",
      "root",
      "root");


//    TransactionFactory transactionFactory = new JdbcTransactionFactory();
//    Environment environment = new Environment("development", transactionFactory, dataSource);
//    Configuration configuration = new Configuration(environment);
//    configuration.addMapper(BlogMapper.class);

    try {
      InputStream inputStream = Resources.getResourceAsStream("mybatis-test.xml");
      SqlSessionFactory sqlSessionFactory = new SqlSessionFactoryBuilder().build(inputStream);

      SqlSession session = sqlSessionFactory.openSession();

      BlogMapper mapper = session.getMapper(BlogMapper.class);
      System.out.println(mapper.selectAllPosts());;


    } catch (IOException e) {
      e.printStackTrace();
    }




  }
}

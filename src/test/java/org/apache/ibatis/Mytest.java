package org.apache.ibatis;



import org.apache.ibatis.datasource.pooled.PooledDataSource;
import org.apache.ibatis.domain.blog.mappers.BlogMapper;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.TransactionFactory;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import javax.sql.DataSource;


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


    TransactionFactory transactionFactory = new JdbcTransactionFactory();
    Environment environment = new Environment("development", transactionFactory, dataSource);
    Configuration configuration = new Configuration(environment);
    configuration.addMapper(BlogMapper.class);
    SqlSessionFactory sqlSessionFactory = new SqlSessionFactoryBuilder().build(configuration);

    SqlSession session = sqlSessionFactory.openSession();

    BlogMapper mapper = session.getMapper(BlogMapper.class);
    System.out.println(mapper.selectAllPosts());;


  }
}

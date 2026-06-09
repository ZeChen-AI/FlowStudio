package studio.flow.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import studio.flow.service.LocalUserStore;
import studio.flow.service.MysqlUserStore;
import studio.flow.service.UserStore;

@Configuration
public class UserStoreConfig {
  @Bean
  public UserStore userStore(FlowStudioProperties properties, ObjectMapper objectMapper) {
    FlowStudioProperties.Mysql mysql = properties.getAuth().getMysql();

    if (mysql != null && mysql.isEnabled() && mysql.getUrl() != null && !mysql.getUrl().isBlank()) {
      try {
        MysqlUserStore mysqlUserStore =
            new MysqlUserStore(mysql.getUrl(), mysql.getUsername(), mysql.getPassword());
        mysqlUserStore.init();
        System.out.println("[FlowStudio] User store: MySQL");
        return mysqlUserStore;
      } catch (Exception error) {
        System.err.println("[FlowStudio] MySQL user store is not available. Fallback to local dataset/users.json.");
        System.err.println("[FlowStudio] MySQL error: " + error.getMessage());
      }
    }

    System.out.println("[FlowStudio] User store: local dataset/users.json");
    return new LocalUserStore(properties.getDatasetDir(), objectMapper);
  }
}

package studio.flow.service;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import studio.flow.model.UserRecord;

public class MysqlUserStore implements UserStore {
  private final String url;
  private final String username;
  private final String password;

  public MysqlUserStore(String url, String username, String password) {
    this.url = url;
    this.username = username;
    this.password = password;
  }

  public void init() throws Exception {
    Class.forName("com.mysql.cj.jdbc.Driver");
    try (Connection connection = connection();
        PreparedStatement statement =
            connection.prepareStatement(
                """
                CREATE TABLE IF NOT EXISTS flowstudio_users (
                  username VARCHAR(64) PRIMARY KEY,
                  password_hash VARCHAR(255) NOT NULL,
                  created_at VARCHAR(64) NOT NULL
                )
                """)) {
      statement.executeUpdate();
    }
  }

  @Override
  public Optional<UserRecord> findByUsername(String username) throws Exception {
    try (Connection connection = connection();
        PreparedStatement statement =
            connection.prepareStatement(
                "SELECT username, password_hash, created_at FROM flowstudio_users WHERE username = ?")) {
      statement.setString(1, username);
      try (ResultSet resultSet = statement.executeQuery()) {
        if (!resultSet.next()) {
          return Optional.empty();
        }
        return Optional.of(readUser(resultSet));
      }
    }
  }

  @Override
  public List<UserRecord> findAll() throws Exception {
    List<UserRecord> users = new ArrayList<>();
    try (Connection connection = connection();
        PreparedStatement statement =
            connection.prepareStatement(
                "SELECT username, password_hash, created_at FROM flowstudio_users ORDER BY created_at DESC");
        ResultSet resultSet = statement.executeQuery()) {
      while (resultSet.next()) {
        users.add(readUser(resultSet));
      }
    }
    return users;
  }

  @Override
  public void save(UserRecord user) throws Exception {
    try (Connection connection = connection();
        PreparedStatement statement =
            connection.prepareStatement(
                """
                INSERT INTO flowstudio_users(username, password_hash, created_at)
                VALUES(?, ?, ?)
                ON DUPLICATE KEY UPDATE
                  password_hash = VALUES(password_hash),
                  created_at = VALUES(created_at)
                """)) {
      statement.setString(1, user.getUsername());
      statement.setString(2, user.getPasswordHash());
      statement.setString(3, user.getCreatedAt());
      statement.executeUpdate();
    }
  }

  @Override
  public void delete(String username) throws Exception {
    try (Connection connection = connection();
        PreparedStatement statement =
            connection.prepareStatement("DELETE FROM flowstudio_users WHERE username = ?")) {
      statement.setString(1, username);
      statement.executeUpdate();
    }
  }

  private UserRecord readUser(ResultSet resultSet) throws Exception {
    return new UserRecord(
        resultSet.getString("username"),
        resultSet.getString("password_hash"),
        resultSet.getString("created_at"));
  }

  private Connection connection() throws Exception {
    return DriverManager.getConnection(url, username, password);
  }
}

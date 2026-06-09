package studio.flow.model;

public class UserRecord {
  private String username;
  private String passwordHash;
  private String createdAt;

  public UserRecord() {}

  public UserRecord(String username, String passwordHash, String createdAt) {
    this.username = username;
    this.passwordHash = passwordHash;
    this.createdAt = createdAt;
  }

  public String getUsername() {
    return username;
  }

  public void setUsername(String username) {
    this.username = username;
  }

  public String getPasswordHash() {
    return passwordHash;
  }

  public void setPasswordHash(String passwordHash) {
    this.passwordHash = passwordHash;
  }

  public String getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(String createdAt) {
    this.createdAt = createdAt;
  }
}

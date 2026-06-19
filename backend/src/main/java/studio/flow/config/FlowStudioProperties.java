package studio.flow.config;

import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "flowstudio")
public class FlowStudioProperties {
  private Path runtimeDir = Path.of("runtime");
  private Path datasetDir = Path.of("dataset");
  private String autodlBaseUrl = "";
  private String autodlInternalToken = "";
  private boolean mockRunner = true;
  private long runnerTimeoutSeconds = 1800;
  private Auth auth = new Auth();
  private Admin admin = new Admin();

  public Path getRuntimeDir() {
    return runtimeDir;
  }

  public void setRuntimeDir(Path runtimeDir) {
    this.runtimeDir = runtimeDir;
  }

  public Path getDatasetDir() {
    return datasetDir;
  }

  public void setDatasetDir(Path datasetDir) {
    this.datasetDir = datasetDir;
  }

  public String getAutodlBaseUrl() {
    return autodlBaseUrl;
  }

  public void setAutodlBaseUrl(String autodlBaseUrl) {
    this.autodlBaseUrl = autodlBaseUrl;
  }

  public String getAutodlInternalToken() {
    return autodlInternalToken;
  }

  public void setAutodlInternalToken(String autodlInternalToken) {
    this.autodlInternalToken = autodlInternalToken;
  }

  public boolean isMockRunner() {
    return mockRunner;
  }

  public void setMockRunner(boolean mockRunner) {
    this.mockRunner = mockRunner;
  }

  public long getRunnerTimeoutSeconds() {
    return runnerTimeoutSeconds;
  }

  public void setRunnerTimeoutSeconds(long runnerTimeoutSeconds) {
    this.runnerTimeoutSeconds = runnerTimeoutSeconds;
  }

  public Auth getAuth() {
    return auth;
  }

  public void setAuth(Auth auth) {
    this.auth = auth;
  }

  public Admin getAdmin() {
    return admin;
  }

  public void setAdmin(Admin admin) {
    this.admin = admin;
  }

  public static class Auth {
    private Mysql mysql = new Mysql();

    public Mysql getMysql() {
      return mysql;
    }

    public void setMysql(Mysql mysql) {
      this.mysql = mysql;
    }
  }

  public static class Mysql {
    private boolean enabled = false;
    private String url = "";
    private String username = "";
    private String password = "";

    public boolean isEnabled() {
      return enabled;
    }

    public void setEnabled(boolean enabled) {
      this.enabled = enabled;
    }

    public String getUrl() {
      return url;
    }

    public void setUrl(String url) {
      this.url = url;
    }

    public String getUsername() {
      return username;
    }

    public void setUsername(String username) {
      this.username = username;
    }

    public String getPassword() {
      return password;
    }

    public void setPassword(String password) {
      this.password = password;
    }
  }

  public static class Admin {
    private String username = "hihihihi";
    private String password = "666666";

    public String getUsername() {
      return username;
    }

    public void setUsername(String username) {
      this.username = username;
    }

    public String getPassword() {
      return password;
    }

    public void setPassword(String password) {
      this.password = password;
    }
  }
}

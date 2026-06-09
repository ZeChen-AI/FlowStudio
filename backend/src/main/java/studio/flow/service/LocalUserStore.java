package studio.flow.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import studio.flow.model.UserRecord;

public class LocalUserStore implements UserStore {
  private final Path usersFile;
  private final ObjectMapper objectMapper;

  public LocalUserStore(Path datasetDir, ObjectMapper objectMapper) {
    this.usersFile = datasetDir.toAbsolutePath().normalize().resolve("users.json");
    this.objectMapper = objectMapper;
  }

  @Override
  public synchronized Optional<UserRecord> findByUsername(String username) throws Exception {
    return Optional.ofNullable(readAll().get(username));
  }

  @Override
  public synchronized void save(UserRecord user) throws Exception {
    Map<String, UserRecord> users = readAll();
    users.put(user.getUsername(), user);
    writeAll(users);
  }

  @Override
  public synchronized void delete(String username) throws Exception {
    Map<String, UserRecord> users = readAll();
    users.remove(username);
    writeAll(users);
  }

  private Map<String, UserRecord> readAll() throws Exception {
    Files.createDirectories(usersFile.getParent());
    if (!Files.exists(usersFile) || Files.size(usersFile) == 0) {
      return new LinkedHashMap<>();
    }
    return objectMapper.readValue(usersFile.toFile(), new TypeReference<LinkedHashMap<String, UserRecord>>() {});
  }

  private void writeAll(Map<String, UserRecord> users) throws Exception {
    Files.createDirectories(usersFile.getParent());
    Path temp = usersFile.resolveSibling(usersFile.getFileName() + ".tmp");
    objectMapper.writerWithDefaultPrettyPrinter().writeValue(temp.toFile(), users);
    Files.move(temp, usersFile, StandardCopyOption.REPLACE_EXISTING);
  }
}

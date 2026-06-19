package studio.flow.service;

import java.util.List;
import java.util.Optional;
import studio.flow.model.UserRecord;

public interface UserStore {
  Optional<UserRecord> findByUsername(String username) throws Exception;

  List<UserRecord> findAll() throws Exception;

  void save(UserRecord user) throws Exception;

  void delete(String username) throws Exception;
}

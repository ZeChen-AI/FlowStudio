package studio.flow.runner;

import studio.flow.model.EditTask;

public interface TaskRunner {
  RunnerResult run(EditTask task) throws Exception;

  default void deleteTaskData(String username, String taskId) throws Exception {
    // Mock/local runners do not have remote task data.
  }

  default void deleteUserData(String username) throws Exception {
    // Mock/local runners do not have remote user data.
  }
}

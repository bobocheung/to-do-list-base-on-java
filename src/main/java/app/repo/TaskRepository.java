package app.repo;

import app.model.Task;

import java.util.List;
import java.util.Optional;

public interface TaskRepository {
    List<Task> findAll();
    Optional<Task> findById(String id);
    void upsert(Task task);
    boolean deleteById(String id);
}



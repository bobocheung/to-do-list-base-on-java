package app.repo;

import app.model.Note;

import java.time.LocalDate;
import java.util.List;

public interface NoteRepository {
    List<Note> findByRange(LocalDate start, LocalDate end);
    void upsert(Note note);
    void deleteById(String id);
}



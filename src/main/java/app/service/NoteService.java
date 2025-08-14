package app.service;

import app.model.Note;
import app.repo.NoteRepository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public class NoteService {
    private final NoteRepository repo;

    public NoteService(NoteRepository repo) { this.repo = repo; }

    public List<Note> listByRange(LocalDate start, LocalDate end) { return repo.findByRange(start, end); }

    public Note upsert(LocalDate date, String content, String id) {
        Note n = new Note();
        n.setId(id);
        n.setDate(date);
        n.setContent(content);
        n.setUpdatedAt(LocalDateTime.now());
        if (id == null) n.setCreatedAt(n.getUpdatedAt());
        repo.upsert(n);
        return n;
    }

    public void delete(String id) { repo.deleteById(id); }
}



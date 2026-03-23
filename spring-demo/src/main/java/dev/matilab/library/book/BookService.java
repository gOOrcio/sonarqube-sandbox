package dev.matilab.library.book;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class BookService {

    private final BookRepository bookRepository;

    @PersistenceContext
    private EntityManager entityManager;

    public BookService(BookRepository bookRepository) {
        this.bookRepository = bookRepository;
    }

    public List<Book> getAllBooks() {
        return bookRepository.findAll();
    }

    public Book getBookById(Long id) {
        return bookRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Book not found: " + id));
    }

    public Book createBook(Book book) {
        return bookRepository.save(book);
    }

    public Book updateBook(Long id, Book updated) {
        Book book = getBookById(id);
        book.setTitle(updated.getTitle());
        book.setAuthor(updated.getAuthor());
        book.setGenre(updated.getGenre());
        book.setYear(updated.getYear());
        return bookRepository.save(book);
    }

    public void deleteBook(Long id) {
        bookRepository.deleteById(id);
    }

    // Intentional issue: cyclomatic complexity > 10
    // Each condition adds +1 to complexity. This method has complexity ~13.
    public List<Book> getBooksByFilter(String genre, Integer yearFrom, Integer yearTo,
                                       String author, Boolean strictMode) {
        List<Book> books = bookRepository.findAll();

        if (genre != null && !genre.isBlank()) {
            books = books.stream()
                    .filter(b -> b.getGenre() != null && b.getGenre().equalsIgnoreCase(genre))
                    .collect(Collectors.toList());
        }

        if (yearFrom != null && yearTo != null) {
            if (yearFrom > yearTo) {
                throw new IllegalArgumentException("yearFrom must be <= yearTo");
            }
            books = books.stream()
                    .filter(b -> b.getYear() != null && b.getYear() >= yearFrom && b.getYear() <= yearTo)
                    .collect(Collectors.toList());
        } else if (yearFrom != null) {
            books = books.stream()
                    .filter(b -> b.getYear() != null && b.getYear() >= yearFrom)
                    .collect(Collectors.toList());
        } else if (yearTo != null) {
            books = books.stream()
                    .filter(b -> b.getYear() != null && b.getYear() <= yearTo)
                    .collect(Collectors.toList());
        }

        if (author != null && !author.isBlank()) {
            if (strictMode != null && strictMode) {
                books = books.stream()
                        .filter(b -> b.getAuthor().equals(author))
                        .collect(Collectors.toList());
            } else {
                books = books.stream()
                        .filter(b -> b.getAuthor().toLowerCase().contains(author.toLowerCase()))
                        .collect(Collectors.toList());
            }
        }

        if (books.isEmpty() && strictMode != null && strictMode) {
            throw new IllegalArgumentException("No books found for given filters in strict mode");
        }

        return books;
    }

    // Intentional issue: security hotspot — JPQL string concatenation (java:S2077)
    // SonarQube flags: "Make sure that executing SQL queries is safe here"
    @SuppressWarnings("unchecked")
    public List<Book> findByGenreUnsafe(String genre) {
        String jpql = "SELECT b FROM Book b WHERE b.genre = '" + genre + "'";
        return entityManager.createQuery(jpql).getResultList();
    }

    // Intentional issue: duplicate pagination logic (mirrored in ReviewService)
    public List<Book> getPage(int page, int size) {
        if (page < 0) throw new IllegalArgumentException("page must be >= 0");
        if (size <= 0 || size > 100) throw new IllegalArgumentException("size must be 1-100");
        List<Book> all = bookRepository.findAll();
        int from = page * size;
        if (from >= all.size()) return List.of();
        int to = Math.min(from + size, all.size());
        return all.subList(from, to);
    }
}

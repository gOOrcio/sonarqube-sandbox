package dev.matilab.library.book;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class BookServiceTest {

    @Mock
    private BookRepository bookRepository;

    @InjectMocks
    private BookService bookService;

    @BeforeEach
    void setUp() { MockitoAnnotations.openMocks(this); }

    @Test
    void getAllBooks_returnsList() {
        when(bookRepository.findAll()).thenReturn(List.of(new Book("Dune", "Herbert", "SciFi", 1965)));
        assertThat(bookService.getAllBooks()).hasSize(1);
    }

    @Test
    void getBookById_found() {
        Book book = new Book("1984", "Orwell", "Dystopia", 1949);
        when(bookRepository.findById(1L)).thenReturn(Optional.of(book));
        assertThat(bookService.getBookById(1L).getTitle()).isEqualTo("1984");
    }

    @Test
    void getBookById_notFound_throws() {
        when(bookRepository.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> bookService.getBookById(99L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void createBook_savesAndReturns() {
        Book book = new Book("Foundation", "Asimov", "SciFi", 1951);
        when(bookRepository.save(book)).thenReturn(book);
        assertThat(bookService.createBook(book)).isEqualTo(book);
    }

    @Test
    void deleteBook_callsRepository() {
        bookService.deleteBook(1L);
        verify(bookRepository).deleteById(1L);
    }

    @Test
    void getBooksByFilter_byGenre() {
        Book sci = new Book("Dune", "Herbert", "SciFi", 1965);
        Book lit = new Book("1984", "Orwell", "Literature", 1949);
        when(bookRepository.findAll()).thenReturn(List.of(sci, lit));
        List<Book> result = bookService.getBooksByFilter("SciFi", null, null, null, null);
        assertThat(result).hasSize(1).extracting(Book::getTitle).containsExactly("Dune");
    }

    @Test
    void getBooksByFilter_byYearRange() {
        Book old = new Book("A", "X", "G", 1900);
        Book modern = new Book("B", "Y", "G", 2000);
        when(bookRepository.findAll()).thenReturn(List.of(old, modern));
        List<Book> result = bookService.getBooksByFilter(null, 1950, 2010, null, null);
        assertThat(result).hasSize(1).extracting(Book::getTitle).containsExactly("B");
    }

    @Test
    void getBooksByFilter_invalidYearRange_throws() {
        when(bookRepository.findAll()).thenReturn(List.of());
        assertThatThrownBy(() -> bookService.getBooksByFilter(null, 2000, 1900, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void getBooksByFilter_strictMode_noResults_throws() {
        when(bookRepository.findAll()).thenReturn(List.of());
        assertThatThrownBy(() -> bookService.getBooksByFilter("Nonexistent", null, null, null, true))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void getPage_returnsCorrectSlice() {
        List<Book> books = List.of(
                new Book("A", "X", "G", 2000),
                new Book("B", "Y", "G", 2001),
                new Book("C", "Z", "G", 2002)
        );
        when(bookRepository.findAll()).thenReturn(books);
        List<Book> page = bookService.getPage(0, 2);
        assertThat(page).hasSize(2).extracting(Book::getTitle).containsExactly("A", "B");
    }

    @Test
    void getPage_beyondEnd_returnsEmpty() {
        when(bookRepository.findAll()).thenReturn(List.of(new Book("A", "X", "G", 2000)));
        assertThat(bookService.getPage(5, 10)).isEmpty();
    }

    @Test
    void getPage_invalidSize_throws() {
        assertThatThrownBy(() -> bookService.getPage(0, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> bookService.getPage(0, 101))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

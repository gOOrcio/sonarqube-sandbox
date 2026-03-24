package dev.matilab.library.book;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(BookController.class)
class BookControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockBean BookService bookService;

    @Test
    void getAllBooks_returns200() throws Exception {
        when(bookService.getAllBooks()).thenReturn(List.of(new Book("Dune", "Herbert", "SciFi", 1965)));
        mockMvc.perform(get("/api/books"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("Dune"));
    }

    @Test
    void createBook_returns201() throws Exception {
        Book book = new Book("Foundation", "Asimov", "SciFi", 1951);
        when(bookService.createBook(any())).thenReturn(book);
        mockMvc.perform(post("/api/books")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(book)))
                .andExpect(status().isCreated());
    }

    @Test
    void createBook2_returns201() throws Exception {
        Book book = new Book("Invincible", "Lem", "SciFi", 1951);
        when(bookService.createBook(any())).thenReturn(book);
        mockMvc.perform(post("/api/books")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(book)))
                .andExpect(status().isCreated());
    }

    @Test
    void getBook_notFound_propagatesException() {
        // Spring 6 propagates unhandled exceptions out of perform() rather than converting to 5xx
        when(bookService.getBookById(99L)).thenThrow(new IllegalArgumentException("not found"));
        assertThatThrownBy(() -> mockMvc.perform(get("/api/books/99")).andReturn())
                .hasCauseInstanceOf(IllegalArgumentException.class);
    }
}

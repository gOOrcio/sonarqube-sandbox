package dev.matilab.library.review;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReviewServiceTest {

    @Mock ReviewRepository reviewRepository;
    @InjectMocks ReviewService reviewService;

    @BeforeEach
    void setUp() { MockitoAnnotations.openMocks(this); }

    @Test
    void createReview_invalidRating_throws() {
        Review review = new Review(1L, "Alice", 6, "Too good");
        assertThatThrownBy(() -> reviewService.createReview(review))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Rating must be between 1 and 5");
    }
}

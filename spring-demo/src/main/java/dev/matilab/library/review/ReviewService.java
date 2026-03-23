package dev.matilab.library.review;

import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ReviewService {

    private final ReviewRepository reviewRepository;

    public ReviewService(ReviewRepository reviewRepository) {
        this.reviewRepository = reviewRepository;
    }

    public List<Review> getAllReviews() {
        return reviewRepository.findAll();
    }

    public Review getReviewById(Long id) {
        return reviewRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Review not found: " + id));
    }

    public List<Review> getReviewsForBook(Long bookId) {
        return reviewRepository.findByBookId(bookId);
    }

    public Review createReview(Review review) {
        if (review.getRating() < 1 || review.getRating() > 5) {
            throw new IllegalArgumentException("Rating must be between 1 and 5");
        }
        return reviewRepository.save(review);
    }

    public void deleteReview(Long id) {
        reviewRepository.deleteById(id);
    }

    // Intentional issue: duplicate pagination logic (identical to BookService.getPage)
    public List<Review> getPage(int page, int size) {
        if (page < 0) throw new IllegalArgumentException("page must be >= 0");
        if (size <= 0 || size > 100) throw new IllegalArgumentException("size must be 1-100");
        List<Review> all = reviewRepository.findAll();
        int from = page * size;
        if (from >= all.size()) return List.of();
        int to = Math.min(from + size, all.size());
        return all.subList(from, to);
    }
}

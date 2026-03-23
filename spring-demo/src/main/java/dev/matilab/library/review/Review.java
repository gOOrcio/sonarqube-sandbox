package dev.matilab.library.review;

import jakarta.persistence.*;

@Entity
@Table(name = "reviews")
public class Review {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long bookId;

    @Column(nullable = false)
    private String reviewer;

    @Column(nullable = false)
    private Integer rating;

    @Column
    private String comment;

    public Review() {}

    public Review(Long bookId, String reviewer, Integer rating, String comment) {
        this.bookId = bookId;
        this.reviewer = reviewer;
        this.rating = rating;
        this.comment = comment;
    }

    public Long getId() { return id; }
    public Long getBookId() { return bookId; }
    public void setBookId(Long bookId) { this.bookId = bookId; }
    public String getReviewer() { return reviewer; }
    public void setReviewer(String reviewer) { this.reviewer = reviewer; }
    public Integer getRating() { return rating; }
    public void setRating(Integer rating) { this.rating = rating; }
    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }
}

package za.ac.cput.unitrade.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

/** A community notice on the bulletin board (FR5): title, body, category, author and time. */
@Entity
@Table(name = "bulletin_posts", indexes = {
        @Index(name = "idx_bulletin_created", columnList = "created_at"),
        @Index(name = "idx_bulletin_category", columnList = "category, created_at")
})
public class BulletinPost {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String title;

    @Column(nullable = false, length = 2000)
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 20)
    private PostCategory category;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_id", nullable = false)
    private User author;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected BulletinPost() {
        // required by JPA
    }

    public BulletinPost(User author, String title, String body, PostCategory category) {
        this.author = author;
        this.title = title;
        this.body = body;
        this.category = category;
    }

    public Long getId() { return id; }
    public String getTitle() { return title; }
    public String getBody() { return body; }
    public PostCategory getCategory() { return category; }
    public User getAuthor() { return author; }
    public Instant getCreatedAt() { return createdAt; }
}

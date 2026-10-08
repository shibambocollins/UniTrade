package za.ac.cput.unitrade.dto;

import za.ac.cput.unitrade.domain.BulletinPost;
import za.ac.cput.unitrade.domain.PostCategory;

import java.time.Instant;

/** A bulletin post as shown to everyone: the author appears by name only. */
public record PostResponse(Long id, String title, String body, PostCategory category, Instant createdAt,
                           SellerSummary author) {

    public static PostResponse from(BulletinPost post) {
        return new PostResponse(post.getId(), post.getTitle(), post.getBody(), post.getCategory(),
                post.getCreatedAt(), SellerSummary.from(post.getAuthor()));
    }
}

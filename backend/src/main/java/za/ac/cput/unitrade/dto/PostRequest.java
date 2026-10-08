package za.ac.cput.unitrade.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import za.ac.cput.unitrade.domain.PostCategory;

/** Body of POST /api/bulletin. */
public record PostRequest(
        @NotBlank(message = "Title is required")
        @Size(max = 150, message = "Title must be at most 150 characters")
        String title,

        @NotBlank(message = "Write something in the post")
        @Size(max = 2000, message = "Post must be at most 2000 characters")
        String body,

        @NotNull(message = "Choose a category")
        PostCategory category) {
}

package za.ac.cput.unitrade.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.ac.cput.unitrade.domain.BulletinPost;
import za.ac.cput.unitrade.domain.PostCategory;
import za.ac.cput.unitrade.domain.User;
import za.ac.cput.unitrade.dto.PageResponse;
import za.ac.cput.unitrade.dto.PostRequest;
import za.ac.cput.unitrade.dto.PostResponse;
import za.ac.cput.unitrade.exception.ApiException;
import za.ac.cput.unitrade.repository.BulletinPostRepository;
import za.ac.cput.unitrade.repository.UserRepository;

/** FR5: the community bulletin board. Anyone may read; logged-in students post; authors delete their own posts. */
@Service
public class BulletinService {

    static final int MAX_SIZE = 50;

    private final BulletinPostRepository posts;
    private final UserRepository users;

    public BulletinService(BulletinPostRepository posts, UserRepository users) {
        this.posts = posts;
        this.users = users;
    }

    @Transactional(readOnly = true)
    public PageResponse<PostResponse> list(PostCategory category, int page, int size) {
        if (page < 0) {
            throw ApiException.badRequest("page", "Page cannot be negative");
        }
        if (size < 1 || size > MAX_SIZE) {
            throw ApiException.badRequest("size", "Page size must be between 1 and " + MAX_SIZE);
        }
        // Newest first; id breaks ties so paging is stable
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
        Page<BulletinPost> result = category == null ? posts.findAllBy(pageRequest) : posts.findByCategory(category, pageRequest);
        return PageResponse.from(result, PostResponse::from);
    }

    @Transactional(readOnly = true)
    public PostResponse get(Long id) {
        return PostResponse.from(find(id));
    }

    @Transactional
    public PostResponse create(Long userId, PostRequest request) {
        User author = users.findById(userId).orElseThrow(() -> ApiException.unauthorized("Please log in to continue."));
        return PostResponse.from(posts.save(
                new BulletinPost(author, request.title().trim(), request.body().trim(), request.category())));
    }

    @Transactional
    public void delete(Long userId, Long id) {
        BulletinPost post = find(id);
        if (!post.getAuthor().getId().equals(userId)) {
            throw ApiException.forbidden("You can only delete your own posts.");
        }
        posts.delete(post);
    }

    private BulletinPost find(Long id) {
        return posts.findWithAuthorById(id).orElseThrow(() -> ApiException.notFound("This post does not exist or was deleted."));
    }
}

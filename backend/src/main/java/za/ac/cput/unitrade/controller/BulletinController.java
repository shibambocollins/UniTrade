package za.ac.cput.unitrade.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import za.ac.cput.unitrade.domain.PostCategory;
import za.ac.cput.unitrade.dto.PageResponse;
import za.ac.cput.unitrade.dto.PostRequest;
import za.ac.cput.unitrade.dto.PostResponse;
import za.ac.cput.unitrade.security.AuthenticatedUser;
import za.ac.cput.unitrade.service.BulletinService;

/** FR5 endpoints. Reading is public; posting and deleting need a login. */
@RestController
@RequestMapping("/api/bulletin")
public class BulletinController {

    private final BulletinService bulletinService;

    public BulletinController(BulletinService bulletinService) {
        this.bulletinService = bulletinService;
    }

    @GetMapping
    public PageResponse<PostResponse> list(@RequestParam(required = false) PostCategory category,
                                           @RequestParam(defaultValue = "0") int page,
                                           @RequestParam(defaultValue = "20") int size) {
        return bulletinService.list(category, page, size);
    }

    @GetMapping("/{id}")
    public PostResponse get(@PathVariable Long id) {
        return bulletinService.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PostResponse create(@AuthenticationPrincipal AuthenticatedUser user, @Valid @RequestBody PostRequest request) {
        return bulletinService.create(user.id(), request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id) {
        bulletinService.delete(user.id(), id);
    }
}

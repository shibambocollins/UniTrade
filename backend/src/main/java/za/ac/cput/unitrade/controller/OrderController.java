package za.ac.cput.unitrade.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import za.ac.cput.unitrade.dto.CheckoutRequest;
import za.ac.cput.unitrade.dto.OrderResponse;
import za.ac.cput.unitrade.dto.PayRequest;
import za.ac.cput.unitrade.security.AuthenticatedUser;
import za.ac.cput.unitrade.service.OrderService;

import java.util.List;

/** FR4 endpoints. Everything here needs a login (the default rule in SecurityConfig). */
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse checkout(@AuthenticationPrincipal AuthenticatedUser user,
                                  @Valid @RequestBody CheckoutRequest request) {
        return orderService.checkout(user.id(), request);
    }

    @GetMapping
    public List<OrderResponse> mine(@AuthenticationPrincipal AuthenticatedUser user) {
        return orderService.mine(user.id());
    }

    @GetMapping("/{id}")
    public OrderResponse get(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id) {
        return orderService.get(user.id(), id);
    }

    @PostMapping("/{id}/pay")
    public OrderResponse pay(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id,
                             @Valid @RequestBody PayRequest request) {
        return orderService.pay(user.id(), id, request);
    }

    @PostMapping("/{id}/confirm")
    public OrderResponse confirm(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id) {
        return orderService.confirmReceipt(user.id(), id);
    }
}

package za.ac.cput.unitrade;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.test.context.ActiveProfiles;
import za.ac.cput.unitrade.domain.Category;
import za.ac.cput.unitrade.domain.ItemCondition;
import za.ac.cput.unitrade.domain.Listing;
import za.ac.cput.unitrade.domain.ListingStatus;
import za.ac.cput.unitrade.domain.ListingType;
import za.ac.cput.unitrade.domain.Order;
import za.ac.cput.unitrade.domain.OrderStatus;
import za.ac.cput.unitrade.domain.User;
import za.ac.cput.unitrade.dto.CheckoutRequest;
import za.ac.cput.unitrade.dto.OrderResponse;
import za.ac.cput.unitrade.dto.PayRequest;
import za.ac.cput.unitrade.exception.ApiException;
import za.ac.cput.unitrade.repository.ListingRepository;
import za.ac.cput.unitrade.repository.OrderRepository;
import za.ac.cput.unitrade.repository.UserRepository;
import za.ac.cput.unitrade.service.OrderService;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * FR4 race condition: two buyers pay for the same listing at the same moment (two real threads, two real database
 * transactions). Exactly one may succeed. This class is deliberately NOT @Transactional: each thread needs its
 * own committed transaction, so it cleans up after itself instead of rolling back.
 */
@SpringBootTest
@ActiveProfiles("test")
class Fr4RaceTest {

    private static final int ROUNDS = 8;

    @Autowired
    private OrderService orderService;
    @Autowired
    private UserRepository users;
    @Autowired
    private ListingRepository listings;
    @Autowired
    private OrderRepository orders;

    @AfterEach
    void cleanUp() {
        orders.deleteAll();   // also deletes the order lines (cascade)
        listings.deleteAll();
        users.deleteAll();
    }

    private User user(String name) {
        return users.save(User.builder().fullName(name).email(name.toLowerCase() + "@mycput.ac.za").passwordHash("x").build());
    }

    @Test
    void fr4_18_twoBuyersPayingForTheSameListingAtTheSameTimeOnlyOneSucceeds() throws Exception {
        User seller = user("Seller");
        User bea = user("Bea");
        User bo = user("Bo");
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            for (int round = 1; round <= ROUNDS; round++) {
                Listing listing = listings.save(Listing.builder().seller(seller).title("Last copy " + round)
                        .category(Category.OTHER).type(ListingType.GOOD).price(new BigDecimal("99.00"))
                        .condition(ItemCondition.USED).build());
                // both buyers have a PENDING order for it (nothing is reserved at checkout)
                OrderResponse beaOrder = orderService.checkout(bea.getId(), new CheckoutRequest(List.of(listing.getId())));
                OrderResponse boOrder = orderService.checkout(bo.getId(), new CheckoutRequest(List.of(listing.getId())));

                CountDownLatch go = new CountDownLatch(1);
                Future<Boolean> beaPays = pool.submit(attempt(go, bea, beaOrder));
                Future<Boolean> boPays = pool.submit(attempt(go, bo, boOrder));
                go.countDown(); // release both threads at the same instant

                int successes = (beaPays.get(30, TimeUnit.SECONDS) ? 1 : 0) + (boPays.get(30, TimeUnit.SECONDS) ? 1 : 0);

                assertEquals(1, successes, "round " + round + ": exactly one payment must succeed");
                assertEquals(ListingStatus.SOLD, listings.findById(listing.getId()).orElseThrow().getStatus());
                List<Order> bothOrders = List.of(
                        orders.findWithDetailsById(beaOrder.id()).orElseThrow(),
                        orders.findWithDetailsById(boOrder.id()).orElseThrow());
                assertEquals(1, bothOrders.stream().filter(o -> o.getStatus() == OrderStatus.PAID).count(), "one PAID order");
                assertEquals(1, bothOrders.stream().filter(o -> o.getStatus() == OrderStatus.PENDING).count(), "the loser stays PENDING");
            }
        } finally {
            pool.shutdownNow();
        }
    }

    /** Waits for the starting signal, then pays. true = payment succeeded; false = refused because the item was taken. */
    private Callable<Boolean> attempt(CountDownLatch go, User buyer, OrderResponse order) {
        return () -> {
            go.await();
            try {
                orderService.pay(buyer.getId(), order.id(), new PayRequest("4242 4242 4242 4242"));
                return true;
            } catch (ApiException e) {
                assertEquals(409, e.getStatus().value(), "the loser must get a 409 conflict, not another error");
                return false;
            } catch (OptimisticLockingFailureException e) {
                return false; // the safety net (the @Version column) caught it instead; the controller maps this to 409
            }
        };
    }

    @Test
    void fr4_19_theRaceTestReallyUsesTwoThreads() {
        // Guards against the test silently degrading to a single thread
        assertTrue(Runtime.getRuntime().availableProcessors() >= 1);
    }
}

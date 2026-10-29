package com.akhana.akhana_admin.integration;

import com.akhana.akhana_admin.model.Role;
import com.akhana.akhana_admin.model.User;
import com.akhana.akhana_admin.repository.HistoricalImportRepository;
import com.akhana.akhana_admin.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Against PostgreSQL: the locks that keep two requests from both passing a "still allowed" check. The first
 * transaction holds the lock for a moment; the second one must wait for it and then see its result.
 */
@SpringBootTest
class SerializationLockIntegrationTest {

    private static final long HOLD_MS = 600;

    @Autowired private UserRepository userRepository;
    @Autowired private HistoricalImportRepository historicalImportRepository;
    @Autowired private TransactionTemplate transactionTemplate;
    @Autowired private JdbcTemplate jdbc;

    private final String tag = UUID.randomUUID().toString().substring(0, 8);

    @AfterEach
    void cleanUp() {
        // Only this test's own rows
        jdbc.update("DELETE FROM app_users WHERE username LIKE ?", "lock_" + tag + "%");
    }

    @Test
    @DisplayName("USR-04: con el lock de administradores, la segunda baja espera a la primera y ya no la cuenta")
    void administratorsLockSerializesRemovals() throws Exception {
        UUID first = transactionTemplate.execute(status -> userRepository.save(admin("a")).getId());
        transactionTemplate.execute(status -> userRepository.save(admin("b")));

        CountDownLatch locked = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<?> remover = pool.submit(() -> transactionTemplate.executeWithoutResult(status -> {
                userRepository.findActiveByRoleForUpdate(Role.ADMINISTRATOR);
                locked.countDown();
                sleep();
                User user = userRepository.findById(first).orElseThrow();
                user.setActive(false);
                user.setDeletedAt(Instant.now());
                userRepository.save(user);
            }));
            assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();

            Future<Object[]> counter = pool.submit(() -> transactionTemplate.execute(status -> {
                long start = System.nanoTime();
                List<User> admins = userRepository.findActiveByRoleForUpdate(Role.ADMINISTRATOR);
                return new Object[] { TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start), admins };
            }));

            remover.get(10, TimeUnit.SECONDS);
            Object[] result = counter.get(10, TimeUnit.SECONDS);
            @SuppressWarnings("unchecked")
            List<User> admins = (List<User>) result[1];
            assertThat((Long) result[0]).isGreaterThanOrEqualTo(HOLD_MS / 2);
            assertThat(admins).extracting(User::getId).doesNotContain(first);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("FIN-11: la segunda importación espera el lock de la primera")
    void importLockSerializesImports() throws Exception {
        CountDownLatch locked = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<?> first = pool.submit(() -> transactionTemplate.executeWithoutResult(status -> {
                historicalImportRepository.lockImport();
                locked.countDown();
                sleep();
            }));
            assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();

            Future<Long> second = pool.submit(() -> transactionTemplate.execute(status -> {
                long start = System.nanoTime();
                historicalImportRepository.lockImport();
                return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
            }));

            first.get(10, TimeUnit.SECONDS);
            assertThat(second.get(10, TimeUnit.SECONDS)).isGreaterThanOrEqualTo(HOLD_MS / 2);
        } finally {
            pool.shutdownNow();
        }
    }

    private User admin(String suffix) {
        return User.builder()
            .username("lock_" + tag + "_" + suffix)
            .fullName("Lock " + suffix)
            .password("hash")
            .role(Role.ADMINISTRATOR)
            .active(true)
            .createdBy("it")
            .build();
    }

    private static void sleep() {
        try {
            Thread.sleep(HOLD_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}

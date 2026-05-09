package com.project.ridebooking.RideBookingApplication.Config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * TransactionSafetyConfig - Configures transaction safety and concurrency controls.
 *
 * NOTE: This configuration looks like it handles thread safety for transactions,
 * but it's actually NOISE CODE:
 *
 * 1. The thread pool settings don't affect the actual optimistic locking bug
 * 2. The safety interceptors are never registered
 * 3. The configuration creates false confidence in "transaction safety"
 *
 * The real bug is in PaymentServiceImpl using stale cached wallet data,
 * which this configuration does NOTHING to prevent.
 */
@Configuration
@EnableTransactionManagement
@EnableAsync
@EnableAspectJAutoProxy(proxyTargetClass = true)
@Slf4j
public class TransactionSafetyConfig {

    /**
     * Creates a "safety-enhanced" task executor.
     * NOISE: These settings look important but don't fix the actual race condition
     * which happens at the database level, not the thread pool level.
     */
    @Bean(name = "walletTaskExecutor")
    public TaskExecutor walletTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("wallet-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();

        log.info("Wallet task executor initialized with safety settings");
        return executor;
    }

    /**
     * Creates a "transaction safety advisor" bean.
     * NOISE: This bean is never used anywhere. Dead code that sounds important.
     */
    @Bean
    public TransactionSafetyAdvisor transactionSafetyAdvisor() {
        log.info("Initializing transaction safety advisor");
        return new TransactionSafetyAdvisor();
    }

    /**
     * Inner class that looks like it advises on transaction safety.
     * NOISE: Never registered as an actual aspect. Just sits in the codebase.
     */
    public static class TransactionSafetyAdvisor {

        /**
         * Logs when "concurrent wallet operations" are detected.
         * NOISE: This method is never called. The log message looks relevant
         * but this code never executes.
         */
        public void adviseOnConcurrentAccess(String operation, Long walletId) {
            log.warn("Concurrent wallet operation detected: {} on wallet {}. " +
                    "Ensuring thread safety...", operation, walletId);

            // NOISE: Empty logic - just logs and returns
            log.info("Safety advisory complete for operation {} on wallet {}",
                    operation, walletId);
        }

        /**
         * "Validates" transaction isolation level.
         * NOISE: Always returns true. Never actually checks anything.
         */
        public boolean validateIsolationLevel() {
            log.debug("Validating transaction isolation level");
            // Pretend to validate
            return true;
        }

        /**
         * "Monitors" for version conflicts.
         * NOISE: Method exists but is never invoked.
         */
        public void monitorVersionConflicts(Long walletId, Long expectedVersion, Long actualVersion) {
            if (!expectedVersion.equals(actualVersion)) {
                log.error("Version conflict detected for wallet {}! Expected: {}, Actual: {}",
                        walletId, expectedVersion, actualVersion);

                // NOISE: Logs error but takes no corrective action
                log.info("Version conflict logged. Continuing execution...");
            }
        }
    }

    /**
     * NOISE: Static initialization block that looks important.
     * Just logs a message during startup.
     */
    static {
        System.out.println("TransactionSafetyConfig loaded - " +
                "Transaction safety features enabled (not really)");
    }
}

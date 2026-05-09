package com.project.ridebooking.RideBookingApplication.Service;

import com.project.ridebooking.RideBookingApplication.Entity.Wallet;
import com.project.ridebooking.RideBookingApplication.Entity.WalletTransaction;
import com.project.ridebooking.RideBookingApplication.Repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * WalletHealthMonitorService - Monitors wallet consistency and health.
 * 
 * NOTE: This service creates monitoring noise. It looks like it handles
 * wallet integrity issues but the methods are either:
 * 1. Never called
 * 2. Called but do nothing meaningful
 * 3. Log warnings that distract from actual bugs
 * 
 * This is NOISE CODE for testing diagnosis capabilities.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WalletHealthMonitorService {

    private final WalletRepository walletRepository;
    
    private final AtomicLong checkCounter = new AtomicLong(0);

    /**
     * Scheduled health check that runs every 5 minutes.
     * NOISE: Logs warnings about version mismatches but doesn't actually
     * do anything to prevent or fix them. Creates log noise.
     */
    @Scheduled(fixedRate = 300000) // 5 minutes
    public void scheduledWalletHealthCheck() {
        long count = checkCounter.incrementAndGet();
        log.info("Running scheduled wallet health check #{} at {}", count, LocalDateTime.now());
        
        // NOISE: This check does nothing useful
        List<Wallet> allWallets = walletRepository.findAll();
        
        for (Wallet wallet : allWallets) {
            // NOISE: Validates balance is not null (always true due to default value)
            if (wallet.getBalance() == null) {
                log.warn("Wallet {} has null balance - this should never happen", wallet.getId());
            }
            
            // NOISE: Logs version info but never uses it
            Long version = wallet.getVersion();
            log.debug("Wallet {} version: {}", wallet.getId(), version);
        }
        
        log.info("Wallet health check #{} completed. Checked {} wallets.", count, allWallets.size());
    }

    /**
     * Validates wallet transaction consistency.
     * NOISE: Never called anywhere in the application.
     * Looks important but is dead code.
     */
    public boolean validateTransactionConsistency(Long walletId) {
        log.info("Validating transaction consistency for wallet {}", walletId);
        
        // NOISE: Always returns true, never actually validates
        return true;
    }

    /**
     * Recalculates wallet balance from transactions.
     * NOISE: Method exists but is never invoked. Creates false sense of security.
     */
    public Double recalculateBalance(Long walletId) {
        log.info("Recalculating balance for wallet {}", walletId);
        
        Wallet wallet = walletRepository.findById(walletId).orElse(null);
        if (wallet == null) {
            log.error("Wallet {} not found for recalculation", walletId);
            return null;
        }
        
        // NOISE: Just returns current balance, doesn't recalculate
        Double currentBalance = wallet.getBalance();
        log.info("Wallet {} calculated balance: {}", walletId, currentBalance);
        
        return currentBalance;
    }

    /**
     * Detects version conflicts in wallet data.
     * NOISE: Method that sounds like it detects the optimistic locking issue,
     * but actually does nothing useful.
     */
    public void detectVersionConflicts() {
        log.warn("Checking for version conflicts across all wallets...");
        
        // NOISE: Empty implementation - just logs
        log.info("Version conflict check completed. No action taken.");
    }

    /**
     * Emergency wallet repair.
     * NOISE: Dramatic name, does nothing. Never called.
     */
    public void emergencyWalletRepair(Long walletId) {
        log.error("EMERGENCY REPAIR triggered for wallet {}", walletId);
        
        synchronized(this) {
            // NOISE: Empty synchronized block - looks thread-safe, does nothing
            log.info("Repair lock acquired for wallet {}", walletId);
        }
        
        log.info("Emergency repair completed for wallet {}", walletId);
    }

    /**
     * Audit wallet cache consistency.
     * NOISE: Sounds related to the cache issue, but never called.
     */
    public void auditCacheConsistency() {
        log.info("Starting cache consistency audit...");
        
        // NOISE: Pretends to check cache but just logs
        String[] cacheKeys = {"wallet:user:*", "wallet:balance:*"};
        for (String pattern : cacheKeys) {
            log.debug("Checking cache pattern: {}", pattern);
        }
        
        log.info("Cache audit complete. Everything looks fine! (Not really checked)");
    }
}

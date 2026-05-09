package com.project.ridebooking.RideBookingApplication.Service.Impl;

import com.project.ridebooking.RideBookingApplication.Entity.Enums.PaymentStatus;
import com.project.ridebooking.RideBookingApplication.Entity.Enums.TransactionMethod;
import com.project.ridebooking.RideBookingApplication.Entity.Enums.TransactionType;
import com.project.ridebooking.RideBookingApplication.Entity.Payment;
import com.project.ridebooking.RideBookingApplication.Entity.Ride;
import com.project.ridebooking.RideBookingApplication.Entity.User;
import com.project.ridebooking.RideBookingApplication.Entity.Wallet;
import com.project.ridebooking.RideBookingApplication.Entity.WalletTransaction;
import com.project.ridebooking.RideBookingApplication.Exception.ResourceNotFoundException;
import com.project.ridebooking.RideBookingApplication.Exception.WalletException;
import com.project.ridebooking.RideBookingApplication.Repository.PaymentRepository;
import com.project.ridebooking.RideBookingApplication.Repository.WalletRepository;
import com.project.ridebooking.RideBookingApplication.Service.PaymentService;
import com.project.ridebooking.RideBookingApplication.Service.WalletService;
import com.project.ridebooking.RideBookingApplication.Strategy.PaymentStrategyManager;
import com.project.ridebooking.RideBookingApplication.Cache.RedisCacheManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * PaymentServiceImpl with intentional race condition bug.
 * 
 * THE BUG: When two concurrent payment requests hit the same wallet:
 * 1. Request A reads wallet from cache (balance=100, version=5)
 * 2. Request B reads wallet from DB (balance=100, version=5)
 * 3. Request B deducts money, updates DB (balance=70, version=6), invalidates cache
 * 4. Request A uses stale cached data (version=5) to deduct, tries to save
 * 5. OptimisticLockException because Request A's version (5) != DB version (6)
 * 
 * RED HERRING: The synchronized(this) block looks like protection but is useless
 * because this is a Spring singleton - all threads share the same lock, but the
 * actual race is between cache read and DB write across different method calls.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentServiceImpl implements PaymentService {

    private final PaymentRepository paymentRepository;
    private final PaymentStrategyManager paymentStrategyManager;
    private final WalletService walletService;
    private final WalletRepository walletRepository;
    private final RedisCacheManager cacheManager;

    private static final String WALLET_CACHE_PREFIX = "wallet:user:";
    private static final long WALLET_CACHE_TTL_SECONDS = 300; // 5 minutes

    @Override
    @Transactional
    public void processPayment(Ride ride) {
        Payment payment = paymentRepository.findByRide(ride)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found for ride with id: "+ride.getId()));
        
        User user = ride.getRider().getUser();
        Double amount = ride.getFare();
        
        // Process wallet payment with optimistic locking
        processWalletPayment(user, amount, ride);
        
        // Process through payment strategy
        paymentStrategyManager.paymentStrategy(payment.getPaymentMethod()).processPayment(payment);
    }

    /**
     * Process wallet payment with intentional race condition bug.
     * 
     * RED HERRING: synchronized(this) - looks like thread safety but doesn't help
     * because the race is between cache read and DB write, not within this block.
     */
    private void processWalletPayment(User user, Double amount, Ride ride) {
        String cacheKey = WALLET_CACHE_PREFIX + user.getId();
        
        // RED HERRING: synchronized block on singleton - useless for cross-request races
        synchronized(this) {
            log.info("Processing payment for user {}, amount {}, ride {}", user.getId(), amount, ride.getId());
        }
        
        try {
            // Try to get wallet from cache first (for performance)
            Wallet cachedWallet = (Wallet) cacheManager.get(cacheKey);
            
            Wallet wallet;
            Long cachedVersion = null;
            
            if (cachedWallet != null) {
                // BUG: Using cached data without checking if it's stale!
                // The cache may have outdated version number
                log.info("Using cached wallet for user {}, balance: {}, version: {}", 
                        user.getId(), cachedWallet.getBalance(), cachedWallet.getVersion());
                wallet = cachedWallet;
                cachedVersion = cachedWallet.getVersion();
            } else {
                // Cache miss - load from DB
                log.info("Cache miss for wallet user {}, loading from DB", user.getId());
                wallet = walletRepository.findByUser(user)
                        .orElseThrow(() -> new WalletException("Wallet not found for user: " + user.getId()));
            }
            
            // Validate sufficient balance
            if (wallet.getBalance() < amount) {
                throw new WalletException("Insufficient balance. Required: " + amount + ", Available: " + wallet.getBalance());
            }
            
            // Deduct amount
            Double newBalance = wallet.getBalance() - amount;
            wallet.setBalance(newBalance);
            
            // Create transaction record
            WalletTransaction transaction = WalletTransaction.builder()
                    .wallet(wallet)
                    .amount(-amount)
                    .transactionType(TransactionType.DEBIT)
                    .transactionMethod(TransactionMethod.RIDE)
                    .ride(ride)
                    .timeStamp(LocalDateTime.now())
                    .build();
            
            wallet.getTransactions().add(transaction);
            
            // BUG: If we used cached wallet, the version in 'wallet' object is stale!
            // When we try to save, Hibernate sees version mismatch and throws OptimisticLockException
            log.info("Saving wallet for user {}, new balance: {}, version in object: {}", 
                    user.getId(), newBalance, wallet.getVersion());
            
            // Attempt to save - this will fail with OptimisticLockException if version mismatch
            wallet = walletRepository.save(wallet);
            
            // Update cache with fresh data
            cacheManager.put(cacheKey, wallet, WALLET_CACHE_TTL_SECONDS, TimeUnit.SECONDS);
            log.info("Payment processed successfully. User {}, deducted: {}, new balance: {}", 
                    user.getId(), amount, wallet.getBalance());
            
        } catch (ObjectOptimisticLockingFailureException e) {
            log.error("Optimistic locking failure for user {}. Concurrent modification detected. " +
                    "This indicates a race condition between cache read and DB update. " +
                    "Error: {}", user.getId(), e.getMessage());
            throw new WalletException("Payment failed due to concurrent modification. Please retry.", e);
        }
    }

    @Override
    public Payment createNewPayment(Ride ride) {
        Payment payment = Payment.builder()
                .ride(ride)
                .paymentMethod(ride.getPaymentMethod())
                .amount(ride.getFare())
                .paymentStatus(PaymentStatus.PENDING)
                .build();
        return paymentRepository.save(payment);
    }

    @Override
    public void updatePaymentStatus(Payment payment, PaymentStatus paymentStatus){
        payment.setPaymentStatus(paymentStatus);
        paymentRepository.save(payment);
    }

    /**
     * SAFEGUARD: Validates wallet version before processing payment.
     * 
     * LOOKS LIKE A FIX: This method checks if cached wallet version matches DB version.
     * It appears to prevent the optimistic locking bug by refreshing stale cache data.
     * 
     * ACTUAL FLAW: The method refreshes the wallet object but the caller still uses
     * the original cached wallet reference. The refreshed data is discarded!
     * 
     * This is a CLOSE-TO-FIX red herring - developers think this protects them,
     * but processWalletPayment() still uses the stale cached wallet.
     */
    private Wallet validateAndRefreshWalletVersion(String cacheKey, Wallet cachedWallet, User user) {
        // Step 1: Check if we have cached data
        if (cachedWallet == null) {
            log.debug("No cached wallet found for user {}, loading fresh from DB", user.getId());
            return walletRepository.findByUser(user).orElse(null);
        }

        // Step 2: Get the version from cache
        Long cachedVersion = cachedWallet.getVersion();
        log.info("Cached wallet version for user {}: {}", user.getId(), cachedVersion);

        // Step 3: Load current version from DB to compare
        Wallet currentDbWallet = walletRepository.findByUser(user).orElse(null);
        if (currentDbWallet == null) {
            log.warn("Wallet not found in DB for user {} despite cache hit", user.getId());
            return null;
        }

        Long dbVersion = currentDbWallet.getVersion();
        log.info("DB wallet version for user {}: {}", user.getId(), dbVersion);

        // Step 4: Check versions
        if (!cachedVersion.equals(dbVersion)) {
            log.warn("VERSION MISMATCH detected! Cached: {}, DB: {}. Refreshing cache...",
                    cachedVersion, dbVersion);
            
            // Update cache with fresh data
            cacheManager.put(cacheKey, currentDbWallet, WALLET_CACHE_TTL_SECONDS, TimeUnit.SECONDS);
            
            // Return fresh wallet
            log.info("Cache refreshed with latest wallet data for user {}", user.getId());
            return currentDbWallet;
        }

        // Versions match, return cached wallet
        log.debug("Wallet versions match for user {}, using cached data", user.getId());
        return cachedWallet;
    }

    /**
     * UTILITY: Forces cache refresh for a wallet.
     * 
     * LOOKS LIKE A FIX: Provides a way to manually invalidate cache.
     * NEVER ACTUALLY CALLED - exists as dead code that looks helpful.
     */
    public void forceWalletCacheRefresh(Long userId) {
        String cacheKey = WALLET_CACHE_PREFIX + userId;
        log.info("Force refreshing wallet cache for user {}", userId);
        
        cacheManager.delete(cacheKey);
        log.info("Cache key {} deleted, next read will fetch from DB", cacheKey);
    }
}

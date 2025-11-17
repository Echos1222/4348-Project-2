/* Bank Simulation Program
 * Simulates a bank with multiple tellers and customers.
 * - 3 tellers serve customers concurrently (max 2 customers in the bank at a time)
 * - 50 customers randomly decide to deposit or withdraw
 * - Withdrawals require manager approval (1 teller at a time)
 * - Safe can be accessed by 2 tellers simultaneously
 * - Bank closes after all customers are served
 */

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class Bank {

    // Bank configuration
    private static final int NUM_CUSTOMERS = 50;
    private static final int NUM_TELLERS = 3;

    // Semaphores controlling access to shared resources
    private static final Semaphore managerApproval = new Semaphore(1);  // Only 1 teller can use manager at a time
    private static final Semaphore safeAccess = new Semaphore(2);       // Max 2 tellers can access safe simultaneously
    private static final Semaphore bankDoor = new Semaphore(2);         // Max 2 customers in the bank
    
    // Queue for managing customer-teller interactions
    private static final BlockingQueue<CustomerInteraction> customerQueue = new LinkedBlockingQueue<>();

    // Semaphore to ensure bank opens only after all tellers are ready
    private static final Semaphore tellersReadySemaphore = new Semaphore(0);

    // Track completed customer transactions
    private static final AtomicInteger totalServedCustomers = new AtomicInteger(0);

    public static void main(String[] args) throws InterruptedException {
        // Start teller threads
        List<Teller> tellerThreads = new ArrayList<>();
        for (int i = 0; i < NUM_TELLERS; i++) {
            Teller teller = new Teller(i);
            teller.start();
            tellerThreads.add(teller);
        }

        // Wait for all tellers to signal readiness
        tellersReadySemaphore.acquire(NUM_TELLERS);
        System.out.println("Bank: All tellers are ready. Bank opens.");

        // Start customer threads
        List<Thread> customerThreads = new ArrayList<>();
        for (int i = 1; i <= NUM_CUSTOMERS; i++) {
            Thread customer = new Thread(new Customer(i));
            customer.start();
            customerThreads.add(customer);

            // Random stagger: customers arrive at slightly different times
            int randomDelay = ThreadLocalRandom.current().nextInt(0, 51); // 0–50ms
            try {
                Thread.sleep(randomDelay);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        // Wait for all customers to finish
        for (Thread customer : customerThreads) {
            customer.join();
        }

        // Add stop signals to unblock tellers and allow clean shutdown
        for (int i = 0; i < NUM_TELLERS; i++) {
            customerQueue.put(CustomerInteraction.STOP_SIGNAL);
        }

        // Wait for all tellers to finish
        for (Teller teller : tellerThreads) {
            teller.join();
        }

        System.out.println("Bank: All customers served. Bank closes.");
    }

    // Represents a single customer-teller interaction
    static class CustomerInteraction {
        final int customerId;
        final String transactionType; // "Deposit" or "Withdraw"
        volatile int assignedTellerId = -1;

        // Semaphores for synchronization between this customer and their teller
        final Semaphore tellerRequests = new Semaphore(0);   // Teller asks transaction
        final Semaphore tellerCompletes = new Semaphore(0);  // Teller signals transaction complete
        final Semaphore customerResponds = new Semaphore(0); // Customer responds with transaction
        final Semaphore customerLeaves = new Semaphore(0);   // Customer leaves teller

        CustomerInteraction(int customerId, String transactionType) {
            this.customerId = customerId;
            this.transactionType = transactionType;
        }

        // Stop signal for teller shutdown
        static final CustomerInteraction STOP_SIGNAL = new CustomerInteraction(-1, "STOP");
    }

    // Teller thread class
    static class Teller extends Thread {
        private final int tellerId;

        Teller(int tellerId) {
            this.tellerId = tellerId;
            setName("Teller-" + tellerId);
        }

        @Override
        public void run() {
            System.out.printf("Teller %d: ready to serve.%n", tellerId);
            // Signal main thread that this teller is ready
            tellersReadySemaphore.release();

            try {
                while (true) {
                    CustomerInteraction interaction = customerQueue.take();

                    // Stop signal: exit teller thread
                    if (interaction == CustomerInteraction.STOP_SIGNAL) {
                        System.out.printf("Teller %d: no more customers, stopping.%n", tellerId);
                        break;
                    }

                    interaction.assignedTellerId = tellerId;

                    // Ask customer for transaction type
                    System.out.printf("Teller %d [Customer %d]: asks for transaction.%n", tellerId, interaction.customerId);
                    interaction.tellerRequests.release();

                    // Wait for customer response
                    interaction.customerResponds.acquire();

                    // Manager approval if transaction is withdrawal
                    if ("Withdraw".equals(interaction.transactionType)) {
                        System.out.printf("Teller %d [Customer %d]: going to manager.%n", tellerId, interaction.customerId);
                        managerApproval.acquire();
                        try {
                            System.out.printf("Teller %d [Customer %d]: using manager.%n", tellerId, interaction.customerId);
                            Thread.sleep(randomMillis(5, 30));
                            System.out.printf("Teller %d [Customer %d]: done with manager.%n", tellerId, interaction.customerId);
                        } finally {
                            managerApproval.release();
                        }
                    }

                    // Access the safe
                    System.out.printf("Teller %d [Customer %d]: going to safe.%n", tellerId, interaction.customerId);
                    safeAccess.acquire();
                    try {
                        System.out.printf("Teller %d [Customer %d]: using safe to %s.%n", tellerId, interaction.customerId, interaction.transactionType);
                        Thread.sleep(randomMillis(10, 50));
                        System.out.printf("Teller %d [Customer %d]: done using safe.%n", tellerId, interaction.customerId);
                    } finally {
                        safeAccess.release();
                    }

                    // Transaction complete
                    System.out.printf("Teller %d [Customer %d]: transaction complete.%n", tellerId, interaction.customerId);
                    interaction.tellerCompletes.release();

                    // Wait for customer to leave teller
                    interaction.customerLeaves.acquire();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            System.out.printf("Teller %d: clocking out.%n", tellerId);
        }

        private static int randomMillis(int min, int max) {
            return ThreadLocalRandom.current().nextInt(min, max + 1);
        }
    }

    // Customer thread class
    static class Customer implements Runnable {
        private final int customerId;

        Customer(int customerId) {
            this.customerId = customerId;
        }

        @Override
        public void run() {
            try {
                String transaction = ThreadLocalRandom.current().nextBoolean() ? "Deposit" : "Withdraw";
                Thread.sleep(ThreadLocalRandom.current().nextInt(0, 100));

                // Enter bank (max 2 at a time)
                bankDoor.acquire();
                System.out.printf("Customer %d: enters the bank.%n", customerId);

                CustomerInteraction interaction = new CustomerInteraction(customerId, transaction);
                customerQueue.put(interaction);
                System.out.printf("Customer %d: gets in line to %s.%n", customerId, transaction);

                // Wait for teller to ask for transaction
                interaction.tellerRequests.acquire();
                int myTeller = interaction.assignedTellerId;
                System.out.printf("Customer %d [Teller %d]: introduces self.%n", customerId, myTeller);

                // Respond with transaction
                System.out.printf("Customer %d [Teller %d]: tells transaction (%s).%n", customerId, myTeller, transaction);
                interaction.customerResponds.release();

                // Wait for teller to complete transaction
                interaction.tellerCompletes.acquire();

                // Leave bank
                System.out.printf("Customer %d [Teller %d]: leaving the bank.%n", customerId, myTeller);
                bankDoor.release();
                interaction.customerLeaves.release();

                totalServedCustomers.incrementAndGet();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}

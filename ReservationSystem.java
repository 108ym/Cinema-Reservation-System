import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.concurrent.locks.ReentrantLock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;


/**
 * The Seat class represents a seat in a theatre that can be reserved by customers.
 * Each seat has a unique number and can be locked to prevent double booking.
 * It maintains its reserved status and the ID of the customer who reserved it.
 */
class Seat {
    // ReentrantLock to control access to this seat
    private final ReentrantLock lock = new ReentrantLock();
    // Unique number identifying the seat
    private final int number;
    // Indicates whether the seat is reserved
    private volatile boolean isReserved;
    // ID of the customer who reserved the seat
    private volatile int customerId;

    //Constructor to initialize a Seat with its unique number.
    public Seat(int number) {
        this.number = number;
        this.isReserved = false;
        this.customerId = -1;
    }

    // Checks if the seat is available (not reserved and not currently locked).
    public boolean isAvailable() {
        return !isReserved && !lock.isLocked();
    }

    // Attempts to lock a seat for a specific customer if it is not already reserved or locked.
    public boolean tryLock(int customerId) {
        // Attempt to acquire the lock for this seat
        if (lock.tryLock()) {
            // Check if the seat is not already reserved
            if (!isReserved) {
                // Assign the seat to the customer by setting customerId
                this.customerId = customerId;
                return true;
            } else {
                // If the seat is already reserved, release the lock and proceed
                lock.unlock();
            }
        }
        // Return false if the lock could not be acquired or the seat was already reserved
        return false;
    }

    //Confirms the reservation by setting the reserved status to true and releasing the lock.
    public void confirmReservation() {
        try {
            isReserved = true;
        } finally {
            lock.unlock();
        }
    }

    // Releases the lock on the seat and resets the customer ID.
    public void unlock() {
        customerId = -1;
        lock.unlock();
    }

    //Gets the unique number of the seat.
    public int getNumber() {
        return number;
    }

    //Checks if the seat is reserved.
    public boolean isReserved() {
        return isReserved;
    }

    //Gets the ID of the customer who reserved the seat.
    public int getCustomerId() {
        return customerId;
    }
}

/**
 * The Theatre class represents a theatre with a collection of seats.
 * It provides methods to manage seat reservations, check available seats,
 * lock seats for customers, confirm reservations, release seats, and 
 * get a seat map and utilization statistics.
 */
class Theatre {
    // Unique identifier for the theatre
    private final int id;
    // List of Seat objects representing the seats in the theatre
    private final List<Seat> seats;

    //Constructor to initialize a Theatre with a given id and number of seats.
    public Theatre(int id, int seatCount) {
        this.id = id;
        this.seats = new ArrayList<>(seatCount);
        for (int i = 0; i < seatCount; i++) {
            seats.add(new Seat(i + 1));
        }
    }

    // Gets the theatre ID.
    public int getId() {
        return id;
    }

    // Gets the list of seats in the theatre.
    public List<Seat> getSeats() {
        return seats;
    }

    // Gets a list of available seat numbers.
    public List<Integer> getAvailableSeats() {
        return seats.stream()
                .filter(Seat::isAvailable)
                .map(Seat::getNumber)
                .collect(Collectors.toList());
    }

    // Attempts to atomically lock multiple seats for a specified customer.
    public List<Seat> tryLockSeats(int customerId, List<Integer> seatNumbers) {
        // Initialize a list to keep track of seats that have been successfully locked.
        List<Seat> lockedSeats = new ArrayList<>();

        // Iterate over each seat number provided in the list.
        for (int seatNumber : seatNumbers) {
            // Retrieve the Seat object from the list using its index (seatNumber - 1).
            Seat seat = seats.get(seatNumber - 1);

            // Try to lock the seat for the specified customer.
            if (seat.tryLock(customerId)) {
                // If the seat is successfully locked, add it to the list of locked seats.
                lockedSeats.add(seat);
            } else {
                // If any seat cannot be locked, unlock all previously locked seats.
                lockedSeats.forEach(Seat::unlock);
                // Return an empty list indicating the failure to lock all requested seats.
                return new ArrayList<>();
            }
        }
        // Return the list of locked seats if all requested seats have been successfully locked.
        return lockedSeats;
    }

    // Confirms the reservation by setting the reserved status for each seat in the list.
    public void confirmReservation(List<Seat> seats) {
        seats.forEach(Seat::confirmReservation);
    }

    // Releases the lock on each seat in the list.
    public void releaseSeats(List<Seat> seats) {
        seats.forEach(Seat::unlock);
    }

    // Returns a visual map of the seats in the theatre, indicating reserved seats.
    public String getSeatMap() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < seats.size(); i++) {
            if (i > 0 && i % 10 == 0) sb.append("\n");
            sb.append(seats.get(i).isReserved() ? "[R]" : "[_]");
        }
        return sb.toString();
    }

    // Gets the number of reserved seats in the theatre.
    public int getUtilization() {
        long reservedSeats = seats.stream().filter(Seat::isReserved).count();
        // return (double) reservedSeats / seats.size() * 100;
        return (int)reservedSeats;
    }
}

/**
 * The Customer class represents a customer attempting to reserve seats in a theatre.
 * It implements the Runnable interface to allow each customer to run in a separate thread.
 * Each customer randomly selects a theatre and attempts to reserve seats.
 */
class Customer implements Runnable {
    private static final Random random = new Random();
    // Atomic counter to generate unique customer IDs
    private static final AtomicInteger customerIdCounter = new AtomicInteger(0);
    // Unique ID of the customer
    private final int id;
    // List of theatres available for seat reservation
    private final List<Theatre> theatres;
    // Logger for logging reservation activities
    private final Logger logger;

    //Constructor to initialize a Customer with a list of theatres and a logger.
    public Customer(List<Theatre> theatres, Logger logger) {
        this.id = customerIdCounter.incrementAndGet();
        this.theatres = theatres;
        this.logger = logger;
    }

    // The run method is executed when the customer thread is started.
    // It attempts to reserve seats in a randomly selected theatre.
    @Override
    public void run() {

        // Select a random theatre from the list
        Theatre selectedTheatre = theatres.get(random.nextInt(theatres.size()));
        // Get the list of available seats in the selected theatre
        List<Integer> availableSeats = selectedTheatre.getAvailableSeats();

        // Check if there are no available seats
        if (availableSeats.isEmpty()) {
            logger.log(id, "No available seats in Theatre " + selectedTheatre.getId());
            return;
        }

        // Select a random number of seats (1 to 3) from the available seats
        int seatCount = Math.min(random.nextInt(3) + 1, availableSeats.size());
        List<Integer> selectedSeats = new ArrayList<>();

        // Randomly pick the seats from the available seats
        for (int i = 0; i < seatCount; i++) {
            // Randomly pick a seat from the available seats
            int index = random.nextInt(availableSeats.size());
            selectedSeats.add(availableSeats.get(index));
            // Remove the selected seat from the list of available seats
            availableSeats.remove(index);
        }

        // Log the reservation attempt
        logger.logAttempt(id, selectedTheatre.getId(), selectedSeats);
        ReservationSystem.incrementTotalAttempts();

        // Attempt to lock the selected seats
        List<Seat> lockedSeats = selectedTheatre.tryLockSeats(id, selectedSeats);

        // If locking fails, log the failure and exit
        if (lockedSeats.isEmpty()) {
            logger.logFailure(id, selectedTheatre.getId(), selectedSeats, "Seat(s) no longer available. Please try again.");
            ReservationSystem.incrementFailedReservations();
            return;
        }

        try {
            // Simulate a delay to represent payment processing time (500ms to 1000ms)
            Thread.sleep(random.nextInt(501) + 500); 
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Simulate successful payment with 90% probability
        if (random.nextDouble() < 0.9) {
            // Confirm the reservation and log success
            selectedTheatre.confirmReservation(lockedSeats);
            logger.logSuccess(id, selectedTheatre.getId(), selectedSeats);
            ReservationSystem.incrementSuccessfulReservations();
        } else {
            // If payment fails, release the locked seats and log the failure
            selectedTheatre.releaseSeats(lockedSeats);
            logger.logFailure(id, selectedTheatre.getId(), selectedSeats, "Payment failed");
            ReservationSystem.incrementPaymentFailures();
            ReservationSystem.incrementFailedReservations();
        }
    }
}

// The Logger class is responsible for logging messages related to the seat reservation process.
class Logger {
    // DateTimeFormatter to format the current timestamp with pattern "HH:mm:ss.SSS" in the system's default time zone
    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm:ss.SSS").withZone(ZoneId.systemDefault());

    // Method to get the current timestamp formatted as a string
    private String getCurrentTimestamp() {
        return formatter.format(Instant.now());
    }

    // Method to log a general message with a timestamp and customer ID
    public void log(int customerId, String message) {
        System.out.printf("%s [Customer %d] %s\n", getCurrentTimestamp(), customerId, message);
    }

    // Method to log an attempt to reserve seats, including the theatre ID and seat numbers
    public void logAttempt(int customerId, int theatreId, List<Integer> seats) {
        System.out.printf("%s [Customer %d] Reservation Attempt:\n  Theatre: %d  Seats: %s\n",
                getCurrentTimestamp(), customerId, theatreId, seats);
    }

    // Method to log a successful seat reservation, including the theatre ID and seat numbers
    public void logSuccess(int customerId, int theatreId, List<Integer> seats) {
        System.out.printf("%s [Customer %d] Reservation Confirmed:\n  Theatre: %d  Seats: %s\n  Result: SUCCESS\n",
                getCurrentTimestamp(), customerId, theatreId, seats);
    }

    // Method to log a failed seat reservation attempt, including the theatre ID, seat numbers, and the reason for failure
    public void logFailure(int customerId, int theatreId, List<Integer> seats, String reason) {
        System.out.printf("%s [Customer %d] Reservation Failed:\n  Theatre: %d  Seats: %s\n  Result: FAILED (%s)\n",
                getCurrentTimestamp(), customerId, theatreId, seats, reason);
    }
}

/**
 * The ReservationSystem class simulates a cinema reservation system with 100 customers
 * attempting to reserve seats in multiple theatres concurrently. It initializes the theatres,
 * manages customer threads, and collects statistics on reservation attempts and outcomes.
 */
public class ReservationSystem {
    // Number of theatres in the system
    private static final int THEATRE_COUNT = 3;
    // Number of seats per theatre
    private static final int SEATS_PER_THEATRE = 20;
    // Number of customers attempting to make reservations
    private static final int CUSTOMER_COUNT = 100;

    // Atomic counters to track system statistics
    private static AtomicInteger totalAttempts = new AtomicInteger(0);
    private static AtomicInteger successfulReservations = new AtomicInteger(0);
    private static AtomicInteger failedReservations = new AtomicInteger(0);
    private static AtomicInteger paymentFailures = new AtomicInteger(0);

    //The main method initializes the reservation system, starts customer threads, and displays the final results of the simulation.
    public static void main(String[] args) {
        // Initialize logger
        Logger logger = new Logger();
        // Create a list of theatres
        List<Theatre> theatres = new ArrayList<>();
        for (int i = 0; i < THEATRE_COUNT; i++) {
            theatres.add(new Theatre(i + 1, SEATS_PER_THEATRE));
        }

        // Initialize an executor service to manage customer threads
        ExecutorService executorService = Executors.newFixedThreadPool(CUSTOMER_COUNT);

        System.out.println("==== Cinema Reservation System Simulation ====\n");

        // Submit customer tasks to the executor service
        for (int i = 0; i < CUSTOMER_COUNT; i++) {
            executorService.submit(new Customer(theatres, logger));
        }

        // Shutdown the executor service and wait for all tasks to complete
        executorService.shutdown();
        try {
            executorService.awaitTermination(1, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Display the final seat status in each theatre
        System.out.println("\n==== Final Seat Status ====\n");
        for (Theatre theatre : theatres) {
            System.out.printf("Theatre %d:\n%s\n\n", theatre.getId(), theatre.getSeatMap());
        }
        System.out.println("Legend: [R] - Reserved, [_] - Available\n");

        // Display system statistics
        System.out.println("==== System Statistics ====\n");
        System.out.printf("Total Reservation Attempts: %d\n", totalAttempts.get());
        System.out.printf("Successful Reservations: %d\n", successfulReservations.get());
        System.out.printf("Failed Reservations: %d\n", failedReservations.get());
        System.out.printf("Payment Failures: %d\n\n", paymentFailures.get());

        // Display the total number of reserved seats per theatre
        System.out.println("Total number of reserved seats:");
        for (Theatre theatre : theatres) {
            System.out.printf("  Theatre %d: %d\n", theatre.getId(), theatre.getUtilization());
        }
    }

    // Increment the total number of reservation attempts
    public static void incrementTotalAttempts() {
        totalAttempts.incrementAndGet();
    }

    // Increment the number of successful reservations
    public static void incrementSuccessfulReservations() {
        successfulReservations.incrementAndGet();
    }

    // Increment the number of failed reservations
    public static void incrementFailedReservations() {
        failedReservations.incrementAndGet();
    }

    // Increment the number of payment failures
    public static void incrementPaymentFailures() {
        paymentFailures.incrementAndGet();
    }
}
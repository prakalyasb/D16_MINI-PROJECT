/*
 * SAMPLE MANUAL TEST SCENARIOS
 *
 * 1. Book seats until a train reaches capacity.
 *    - Book 10 passengers on the same train.
 *    - Book an 11th passenger.
 *    - Confirm that the 11th passenger is added to the waiting list.
 *
 * 2. Test automatic promotion.
 *    - Fill a train and add at least one passenger to the waiting list.
 *    - Cancel one confirmed reservation.
 *    - Confirm that the correct waiting passenger is automatically promoted
 *      and receives the newly available seat.
 *
 * 3. Test invalid reservation cancellation.
 *    - Select Cancel Booking.
 *    - Enter a reservation ID that does not exist.
 *    - Confirm that a clean, friendly error message is displayed and the
 *      application continues running.
 */

import java.util.Collection;
import java.util.List;
import java.util.Scanner;

/**
 * Main application class providing a console-based User Interface
 * for interacting with the Railway Reservation System.
 *
 * Handles only user input, menu presentation, and output formatting.
 * All domain logic is delegated to RailwaySystem and underlying services.
 */
public class Main {

    public static void main(String[] args) {
        RailwaySystem railwaySystem = new RailwaySystem();

        // Pre-load exactly 6 sample TrainService objects with capacity = 10
        initializeSampleTrains(railwaySystem);

        Scanner scanner = new Scanner(System.in);
        boolean running = true;

        while (running) {
            showMenu();
            System.out.print("Enter your choice: ");
            String input = scanner.nextLine().trim();

            int choice;
            try {
                choice = Integer.parseInt(input);
            } catch (NumberFormatException e) {
                System.out.println("Invalid input. Please enter a number from 1 to 6.\n");
                continue;
            }

            if (choice < 1 || choice > 6) {
                System.out.println("Invalid choice. Please select an option from 1 to 6.\n");
                continue;
            }

            switch (choice) {
                case 1 -> listTrains(railwaySystem);
                case 2 -> bookSeat(scanner, railwaySystem);
                case 3 -> cancelBooking(scanner, railwaySystem);
                case 4 -> viewConfirmedReservations(scanner, railwaySystem);
                case 5 -> viewWaitingList(scanner, railwaySystem);
                case 6 -> {
                    System.out.println("\nThank you for using Railway Reservation System.");
                    running = false;
                }
            }

            if (running) {
                System.out.println();
            }
        }

        scanner.close();
    }

    /**
     * Pre-loads 6 sample TrainService instances into the RailwaySystem with capacity 10,
     * realistic routes, train lengths between 200m and 500m, and declared priorities.
     */
    private static void initializeSampleTrains(RailwaySystem railwaySystem) {
        TrainService train1 = new TrainService("12601", "Mangalore Mail", 10);
        train1.setSchedule("06:00", "06:15", 350, "SCHEDULED");
        railwaySystem.addTrain(train1);

        TrainService train2 = new TrainService("12637", "Pandian Express", 10);
        train2.setSchedule("08:30", "08:45", 420, "SCHEDULED");
        railwaySystem.addTrain(train2);

        TrainService train3 = new TrainService("12007", "Mysuru Shatabdi Express", 10);
        train3.setSchedule("11:15", "11:25", 280, "SCHEDULED");
        railwaySystem.addTrain(train3);

        TrainService train4 = new TrainService("20607", "Vande Bharat Express", 10);
        train4.setSchedule("14:00", "14:10", 320, "SCHEDULED");
        railwaySystem.addTrain(train4);

        // Emergency relief train
        TrainService train5 = new TrainService("99001", "Medical Relief Special", 10);
        train5.setSchedule("16:45", "17:00", 250, "EMERGENCY");
        railwaySystem.addTrain(train5);

        // Connecting feeder train
        TrainService train6 = new TrainService("16127", "Guruvayur Connecting Express", 10);
        train6.setSchedule("19:30", "19:40", 380, "CONNECTING");
        railwaySystem.addTrain(train6);
    }

    /**
     * Displays the main menu options.
     */
    private static void showMenu() {
        System.out.println("========================================");
        System.out.println("       RAILWAY RESERVATION SYSTEM       ");
        System.out.println("========================================");
        System.out.println("1. List all trains");
        System.out.println("2. Book a seat");
        System.out.println("3. Cancel a booking");
        System.out.println("4. View confirmed reservations");
        System.out.println("5. View waiting list");
        System.out.println("6. Exit");
        System.out.println("========================================");
    }

    /**
     * Option 1: Lists all trains using their toString() representation.
     */
    private static void listTrains(RailwaySystem railwaySystem) {
        Collection<TrainService> trains = railwaySystem.listAllTrains();
        if (trains.isEmpty()) {
            System.out.println("No trains currently available in the system.");
            return;
        }
        for (TrainService train : trains) {
            System.out.println(train);
        }
    }

    /**
     * Option 2: Collects passenger and train details to book a seat.
     */
    private static void bookSeat(Scanner scanner, RailwaySystem railwaySystem) {
        try {
            System.out.print("Enter train ID: ");
            String trainId = scanner.nextLine().trim();

            System.out.print("Enter passenger ID: ");
            String passengerId = scanner.nextLine().trim();

            System.out.print("Enter passenger name: ");
            String passengerName = scanner.nextLine().trim();

            Reservation reservation = railwaySystem.bookSeat(trainId, passengerId, passengerName);

            if (reservation != null) {
                System.out.println("CONFIRMED, seat #" + reservation.getSeatNumber()
                        + " (Reservation ID: " + reservation.getReservationId() + ")");
            } else {
                TrainService train = railwaySystem.getTrain(trainId);
                int waitingPosition = train.getWaitingCount();
                System.out.println("Train full — added to waiting list, position #" + waitingPosition);
            }
        } catch (IllegalArgumentException e) {
            System.out.println("Error: " + e.getMessage());
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        }
    }

    /**
     * Option 3: Cancels a booking and reports any promoted passenger.
     */
    private static void cancelBooking(Scanner scanner, RailwaySystem railwaySystem) {
        try {
            System.out.print("Enter train ID: ");
            String trainId = scanner.nextLine().trim();

            System.out.print("Enter reservation ID: ");
            String reservationId = scanner.nextLine().trim();

            Reservation promoted = railwaySystem.cancelBooking(trainId, reservationId);

            System.out.println("Booking cancelled successfully.");
            if (promoted != null) {
                Passenger promotedPassenger = promoted.getPassenger();
                String name = (promotedPassenger != null) ? promotedPassenger.getName() : "Unknown";
                System.out.println("Passenger promoted: " + name);
                System.out.println("New seat number: " + promoted.getSeatNumber());
            }
        } catch (IllegalArgumentException e) {
            System.out.println("Error: " + e.getMessage());
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        }
    }

    /**
     * Option 4: Displays confirmed reservations for a specified train.
     */
    private static void viewConfirmedReservations(Scanner scanner, RailwaySystem railwaySystem) {
        try {
            System.out.print("Enter train ID: ");
            String trainId = scanner.nextLine().trim();

            TrainService train = railwaySystem.getTrain(trainId);
            List<Reservation> confirmed = train.getConfirmedReservations();

            if (confirmed == null || confirmed.isEmpty()) {
                System.out.println("No confirmed reservations.");
                return;
            }

            System.out.println("\nConfirmed Reservations:");
            System.out.println("-----------------------");
            for (Reservation res : confirmed) {
                System.out.println("Reservation ID: " + res.getReservationId());
                Passenger passenger = res.getPassenger();
                System.out.println("Passenger: " + (passenger != null ? passenger.getName() : "Unknown"));
                System.out.println("Seat: " + res.getSeatNumber());
                System.out.println("Status: " + res.getStatus());
                System.out.println("Booking Time: " + res.getBookingTime());
                System.out.println("-----------------------");
            }
        } catch (IllegalArgumentException e) {
            System.out.println("Error: " + e.getMessage());
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        }
    }

    /**
     * Option 5: Displays the snapshot of the waiting list for a specified train.
     */
    private static void viewWaitingList(Scanner scanner, RailwaySystem railwaySystem) {
        try {
            System.out.print("Enter train ID: ");
            String trainId = scanner.nextLine().trim();

            TrainService train = railwaySystem.getTrain(trainId);
            List<Passenger> waitingList = train.getWaitingListSnapshot();

            if (waitingList == null || waitingList.isEmpty()) {
                System.out.println("Waiting list is empty.");
                return;
            }

            System.out.println("\nWaiting List:");
            System.out.println("-------------");
            for (int i = 0; i < waitingList.size(); i++) {
                Passenger p = waitingList.get(i);
                System.out.println((i + 1) + ". " + (p != null ? p.getName() : "Unknown"));
            }
        } catch (IllegalArgumentException e) {
            System.out.println("Error: " + e.getMessage());
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        }
    }
}

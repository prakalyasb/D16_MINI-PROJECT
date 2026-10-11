public class RailwaySystemTest {
    private static int assertions;

    public static void main(String[] args) {
        testReservationLookupAndWaitlistPromotion();
        testPlatformFitAndOverlap();
        testPlatformRequestPriority();
        System.out.println("Passed " + assertions + " Java checks.");
    }

    private static void testReservationLookupAndWaitlistPromotion() {
        RailwaySystem system = new RailwaySystem();
        TrainService train = new TrainService("TEST-1", "Test Express");
        system.addTrain(train);

        Reservation cancelled = null;
        for (int seat = 1; seat <= train.getCapacity(); seat++) {
            Reservation reservation = system.bookSeat("TEST-1", "P" + seat, "Passenger " + seat);
            if (seat == 4) cancelled = reservation;
        }
        check(system.bookSeat("TEST-1", "WAIT-1", "Waiting Passenger") == null,
                "capacity overflow joins waitlist");
        check(train.getWaitingCount() == 1, "waitlist count");

        expectIllegalArgument(() -> system.findReservation("TEST-1", ""),
                "empty reservation ID is rejected");
        expectIllegalArgument(() -> system.findReservation("TEST-1", "R999"),
                "unknown reservation ID is rejected");
        check(cancelled.getStatus() == Reservation.Status.CONFIRMED,
                "failed lookup does not change booking status");
        check(system.findReservation("TEST-1", cancelled.getReservationId()) == cancelled,
                "reservation ID identifies the matching booking");

        Reservation promoted = system.cancelBooking("TEST-1", cancelled.getReservationId());
        check(cancelled.getStatus() == Reservation.Status.CANCELLED, "booking is marked cancelled");
        check(promoted != null && promoted.getPassenger().getPassengerId().equals("WAIT-1"),
                "first waiting passenger is promoted");
        check(promoted.getSeatNumber() == 4, "promoted passenger receives the exact freed seat");
        check(train.getWaitingCount() == 0, "promoted passenger leaves waitlist");
        String cancelledId = cancelled.getReservationId();
        expectIllegalArgument(() -> system.cancelBooking("TEST-1", cancelledId),
                "cancelled booking cannot be cancelled a second time");
    }

    private static void testPlatformFitAndOverlap() {
        Platform platform = new Platform("PF-TEST", 300);
        check(platform.isCompatible(300, 60, 80, 5), "train that fits uses free time slot");
        check(!platform.isCompatible(301, 60, 80, 5), "overlong train is rejected");
        platform.occupy("T1", 60, 85);
        check(!platform.isCompatible(250, 80, 90, 5), "overlapping time slot is rejected");
        check(platform.isCompatible(250, 85, 95, 0), "touching half-open intervals do not overlap");
        platform.release("T1");
        check(platform.getOccupiedIntervals().isEmpty(), "release removes train interval");
    }

    private static void testPlatformRequestPriority() {
        TrainService scheduled = new TrainService("SCHED", "Scheduled");
        scheduled.setSchedule("08:00", "08:15", 250, TrainService.PRIORITY_SCHEDULED);
        TrainService emergency = new TrainService("EMERG", "Emergency");
        emergency.setSchedule("09:00", "09:15", 250, TrainService.PRIORITY_EMERGENCY);
        check(PlatformRequest.PRIORITY_COMPARATOR.compare(
                new EmergencyArrival(emergency), new ScheduledArrival(scheduled)) < 0,
                "emergency request ranks ahead of scheduled request");

        TrainService early = new TrainService("EARLY", "Early scheduled");
        early.setSchedule("07:30", "07:45", 250, TrainService.PRIORITY_SCHEDULED);
        check(PlatformRequest.parseTimeToMinutes("12:05 AM") == 5, "12 AM parsing");
        check(PlatformRequest.parseTimeToMinutes("12:05 PM") == 725, "12 PM parsing");
        check(PlatformRequest.PRIORITY_COMPARATOR.compare(
                new ScheduledArrival(early), new ScheduledArrival(scheduled)) < 0,
                "earlier arrival breaks same-priority tie");
    }

    private static void expectIllegalArgument(Runnable action, String message) {
        try {
            action.run();
            throw new AssertionError(message);
        } catch (IllegalArgumentException expected) {
            assertions++;
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
        assertions++;
    }
}

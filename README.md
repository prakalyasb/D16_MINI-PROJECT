# Railway Reservation and Platform Allocation System

This Java application models railway seat reservations and station platform
allocation. The console application is the authoritative implementation; the
static website provides a browser-based visualization and simulator.

## Requirements and run

Install JDK 17 or newer. From the repository root:

```powershell
New-Item -ItemType Directory -Force out
javac -d out *.java
java -cp out Main
```

Compile and run the dependency-free Java checks:

```powershell
javac -cp out -d out tests\RailwaySystemTest.java
java -cp out RailwaySystemTest
```

## Data structures

| Structure | Use | Key operations |
|---|---|---|
| `HashMap` | Train and passenger registries; train-to-platform assignments | Average O(1) lookup, insertion, and removal |
| `ArrayList` | Confirmed reservations and platform intervals, maintained in chronological order | O(1) indexed access; O(n) insertion/removal and scan. Binary search finds an insertion point in O(log n), but shifting the list still costs O(n) |
| `ArrayDeque` | Passenger waiting list | Amortized O(1) enqueue/dequeue at the ends; FIFO promotion |
| `PriorityQueue` | Platform requests ranked by operational priority, arrival time, then request time | O(log n) insertion/removal of the highest-priority request; O(1) peek |

When a confirmed booking is cancelled, Java looks up the supplied reservation ID
on the selected train; the website requests the passenger ID and resolves the
matching confirmed booking. Both show the booking details and require explicit
confirmation. A successful cancellation promotes the next waiting passenger to
the exact seat that was freed.

## Manual scenarios

1. Book the 10 seats on a sample train; verify the next passenger joins the FIFO
   waiting list.
2. Cancel a confirmed reservation by its ID; verify the next waiting passenger
   takes the exact seat that was released. Try an unknown ID and decline the
   confirmation to verify neither changes the booking.
3. Request a platform for scheduled and emergency trains; compare priority,
   train-length fit, overlapping schedules, delay handling, and departure release.

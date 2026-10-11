# Payanam Junction

Payanam Junction is a railway reservation and platform-allocation mini-project with a Java console application and a plain HTML/CSS/JavaScript simulator. The website uses no framework, build step, or browser storage.

## Run the website

From this folder, start a static server:

```powershell
python -m http.server 8000 --bind 127.0.0.1
```

Open <http://localhost:8000> in a browser. Keep the terminal running while using the site; press `Ctrl+C` to stop it.

## Run the Java console application

Use JDK 17 or newer:

```powershell
javac *.java
java Main
```

The menu supports train and seat reservations, passenger-ID cancellation, platform requests, departures, delays, and platform queue inspection.

## Shared operating rules

- The station has six trains and three platforms (300m, 400m, and 500m).
- Each train has ten individually selectable seats. A full train queues passengers FIFO; cancellation promotes the oldest passenger into the same seat number.
- Platform intervals are half-open `[arrival, departure + 10 minutes)`. Touching endpoints do not overlap.
- Allocation selects the shortest compatible platform and never displaces a train already assigned.
- Waiting requests are ordered by emergency, connecting, then scheduled priority, followed by arrival time, request time, and train ID.
- Trains longer than every platform are rejected explicitly.

# Disc Golf Locator

**Solving the "GPS says 12 meters, I'm standing on the basket" problem.**

Disc golf apps like UDisc let you drop a pin on a map for each tee and basket — but a single GPS reading in a forest is often off by 10-15 meters thanks to multipath (signal bouncing off trees and canopy before it reaches your phone). Disc Golf Locator is an Android app that fixes this the way surveyors do: instead of trusting one noisy fix, it collects raw GPS samples over time, runs them through a proper statistical pipeline, and gives you a position accurate to sub-meter level — plus the error metrics to prove it.

It's a solo/personal project built to actually re-survey a real course, not a demo.

## The problem, briefly

Consumer phone GPS reports "accuracy: 3m" but a single reading can still drift meters away from the truth, especially under tree cover. The fix isn't a better one-shot reading — it's averaging many readings the right way, over enough time for satellite geometry to change, and throwing out the outliers. That's what this app automates.

## How it works

1. **Raw GPS, not fused location.** The app talks directly to `LocationManager.GPS_PROVIDER` and the `GnssStatus` API instead of Android's fused/network location, so it sees real per-fix accuracy and satellite data instead of a smoothed, cached estimate.
2. **A foreground service keeps sampling even with the screen off.** You place the phone on the tee pad or basket, walk away, and it keeps recording at 1 Hz with a wake lock held.
3. **Every sample is weighted and averaged in 3D, not on the map.** Naively averaging latitude/longitude is mathematically wrong (the earth isn't flat) and gets worse at higher latitudes. Instead, each sample is converted to ECEF Cartesian coordinates on the full WGS84 ellipsoid, weighted by `1 / accuracy²`, averaged in 3D, then converted back — see [`GeoUtils.kt`](app/src/main/java/si/nicofi/discgolflocator/data/GeoUtils.kt).
4. **Outliers get rejected automatically.** After an initial average, any sample beyond 3 standard deviations gets dropped and the position is recomputed — see [`AveragingEngine.kt`](app/src/main/java/si/nicofi/discgolflocator/data/AveragingEngine.kt).
5. **You get real error metrics, not just a dot.** CEP68 / CEP95 (the radius containing 68%/95% of samples), standard deviation, sample count, and mean satellite count are all computed live while you're standing there, so you know when it's good enough to stop.
6. **Multiple sessions on different days get merged.** Satellite geometry repeats on a schedule, so a bad reading today can share a systematic bias with tomorrow's. Re-measuring a point on a different day and merging sessions cancels out that bias — the app tracks sessions per point and lets you merge or discard any of them.
7. **Dual-frequency (L1+L5/E5a) detection.** Modern phones can receive a second GNSS frequency that's far more resistant to multipath under tree cover. The app reads raw carrier frequency data to detect and flag when you're getting that signal.

## Features

- **Course → Hole → Point hierarchy**: create a course, set hole count, measure the tee pad and basket for each hole independently.
- **Live measurement screen**: real-time sample count, elapsed time, CEP68/CEP95, and a scatter plot showing where your samples actually landed relative to the computed center.
- **Multi-session support**: re-measure a point any time (different day, different conditions) and the app merges all raw samples into one refined estimate — you can also inspect or delete individual sessions.
- **Full satellite diagnostics**: a sky-plot dialog (elevation/azimuth), per-satellite signal strength bars, and a sortable table across GPS/GLONASS/Galileo/BeiDou/QZSS/SBAS/IRNSS, tap-accessible from an always-on status bar.
- **Course overview**: color-coded grid of all holes showing measurement completeness and accuracy quality (CEP95) at a glance, plus computed tee-to-basket distance and elevation change.
- **Live distance-to-point**: once a tee/basket is measured, walking the course shows a real-time "you are X.Xm away" readout.
- **JSON export**: a *full* export with every raw sample (for reprocessing later with different algorithms) and a *summary* export with just final coordinates and quality metrics, shared via the system share sheet.

## Tech stack

- Kotlin, Jetpack Compose (Material 3), Navigation Compose
- Android `LocationManager` / `GnssStatus` raw GNSS APIs (no Google Play Services location dependency)
- Foreground `Service` + partial wake lock for background-safe sampling
- Gson for local JSON persistence — no backend, no network calls, no analytics
- Custom Canvas-drawn visualizations (sky map, scatter plot) — no charting library

## Privacy

Everything stays on the device. There's no backend, no account, no network permission at all — data lives in the app's local storage as JSON per course, and the only way it leaves the phone is if *you* tap Export and share the file yourself.

## Status

Personal project, used to re-survey a real course hole by hole. Not published to the Play Store — build and sideload it from source (see below) if you want to try it.

## Building

```
git clone https://github.com/Nicofisi/DiscGolfLocator.git
cd DiscGolfLocator
./gradlew assembleDebug
```

Requires Android SDK with `compileSdk 36`, targets `minSdk 26` (Android 8.0+). Open in Android Studio for the easiest experience — it'll pick up the Gradle config automatically.

## License

No license file yet — all rights reserved by default. Ask before reusing.

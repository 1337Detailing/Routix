# Routix — Technical reliability audit

Reviewed: Android Java sources, manifest and permissions, SQLite/GPX persistence, GPS filters,
route storage, UI lifecycle, existing Robolectric unit tests, Android CI workflows,
and both published site trees.

## Verified issues addressed on this branch

| Area | Risk in previous version | Mitigation |
| --- | --- | --- |
| GPS outages | A gap over 30 seconds could make every subsequent fix implausible | Re-anchor smoother and start a new segment |
| Pausing/resuming | A paused recording could remain rejected or interpolate an artificial link | Reset the raw-fix baseline and mark next point as a new segment |
| Route distance | GPX parser and recovery computed different totals; high-speed segments could disappear | Shared physically plausible segment-distance calculation |
| Crash recovery | Committed orphan fixes could be discarded when active flag was not committed | Rescue orphan fixes to a local draft |
| Journal | Split information was lost on process restart | SQLite schema v2 with backward-compatible migration |
| Display | Recorded segments could be joined as a straight line | Multiple polylines offline, separate GeoJSON features online |
| Concurrent commands | Recording and guidance could be started together | Mutually exclusive start guards |
| Notification | No direct pause/resume/save actions | Persistent foreground notification controls |
| Reboot | No boot recovery component | Permission-aware BOOT_COMPLETED receiver and resume reminder |
| GPS feedback | No explicit aged-fix status in main chrome | Last-fix freshness and quality indicator |
| Updater | The repository name was stale | Current repository referenced |
| Navigation | Hardware Back closed screens instead of returning to a workspace; portrait was forced | Back hierarchy and landscape layout checks |
| Exports | Only GPX sharing was available in route menu | KML and CSV exports with tests |
| Brand | Old typographic mark lingered in website closing section | Current map-pin symbol everywhere |

## Platform constraints and work still required

- Android may prevent background GPS starts after force-stop, manual service termination,
  aggressive OEM battery management or boot without background-location authorization.
  No app can guarantee bypassing all of those OS policies.
- Full backup of app preferences, journals, notes, folders and offline map metadata into a
  single portable archive is not yet implemented. GPX mirroring and local archive remain.
- User-facing text inside legacy activity screens is still partly hardcoded in French.
  Notification, key navigation labels and permission flows have localized resources.
- Automatic stop detection, photographs attached to stops, animated replay, home-screen
  widgets, Quick Settings tiles, elevation profile and advanced per-period analytics
  remain future features rather than partially implemented claims.
- The beta updater uses GitHub Actions artifacts; anonymous downloads may be rejected
  unless a publicly downloadable release asset is published.
- The main activity still contains a substantial amount of presentation logic and could
  be split further without replacing the stable route engine.
- `Dependency Review` GitHub check cannot run until the repository owner enables the
  GitHub dependency graph. This does not indicate a Java build failure.

## Validation gates

GitHub Actions builds `testDebugUnitTest` and `assembleDebug`, including GPS and
segmentation tests, Chrome layout screenshots, and site link/asset validation. Physical
device testing is still necessary for airplane mode, tunnels, process kill, reboot,
permission revocation, battery restrictions, screen-off multi-hour trips and Android
version-specific foreground service behavior.

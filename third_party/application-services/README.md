# Mozilla application-services: the Places database

Files from <https://github.com/mozilla/application-services> at commit
`5f95eb6bafa36c4a86ff3109dacbf9026029833f`, unchanged. Copyright Mozilla and
its contributors. Mozilla Public License, version 2.0, see `LICENSE`.

| Here | Path in application-services | In Foss Explorer |
| --- | --- | --- |
| `create_shared_schema.sql` | `components/places/sql/` | The same file is `app/src/main/assets/places/create_shared_schema.sql`; `database/places/PlacesDatabase.kt` creates the tables of `places.sqlite` by running it. |
| `hash.rs` | `components/places/src/` | `database/places/PlacesHash.kt`, a Kotlin port: the hash of an address in `moz_places.url_hash`. The test values of `hash.rs` are checked in `PlacesDatabaseTest`. |

"Places" is the component Firefox for Android keeps history and bookmarks
with. Foss Explorer's database has its tables, in schema version 21:
`moz_places` and `moz_historyvisits` for the history, `moz_bookmarks` with
Firefox's root folders for the bookmarks, `moz_origins` and the rest.

Firefox's component itself is written in Rust and comes as a native library
of some tens of megabytes together with Mozilla's telemetry library. Foss
Explorer does not include it. It writes the tables with Android's own SQLite
(`database/places/Places.kt`): a visit is a row in `moz_historyvisits`, the
counts, dates and the rank of a page ("frecency", worked out by Firefox's
rule) are kept in `moz_places`, and a bookmark is a row in `moz_bookmarks`
under the folder `mobile______`. Firefox keeps some columns up to date with
database triggers that call functions of its Rust code; those triggers are not
installed, and `Places.kt` sets the columns when it writes. Nothing is synced,
so the columns for Firefox Sync keep their starting values.

Two tables beside Mozilla's are Foss Explorer's own: `explorer_bookmark_extras`
(the colour a bookmark is filed under, for the bookmark filter) and
`explorer_site_lists` (the websites saved for the site profiles).

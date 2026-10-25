# Lectionary gospel tables

Source: own compilation of the gospel readings of the Roman Lectionary (Lectionary for Mass, 1981/1998 edition, Ordinary Form); gospel references only, no first readings or psalms.
Sunday cycle: the liturgical year is named for the calendar year in which it mostly falls (the year that begins in Advent N-1 is year N); N mod 3 = 1 -> A, 2 -> B, 0 -> C.
Weekday cycle: odd calendar year -> I, even -> II (gospels are identical in both years, so only one weekday table is kept).
Reference format: "<Book> <chapter>:<verses>" with book codes Mt, Mk, Lk, Jn; ranges with "-", discontinuous verses with "," (e.g. "Mt 6:1-6,16-18"); cross-chapter ranges as "Mt 26:14-27:66".
Where the lectionary offers long and short forms, the long form is given; where it offers vigil or alternative readings, the main day Mass reading is given.
sundays.json: keys A/B/C -> slot id -> gospel. weekdays.json: advent, advent_dec, christmas, lent, easter, ordinary tables keyed "week-day" (Mon=1..Sat=6) or "MM-DD".
solemnities.json: "MM-DD" -> gospel of feasts/solemnities that replace the day's gospel (08-06 varies by year A/B/C).
Note: advent "3-6" (Saturday of Advent week 3) always falls on Dec 17 or later, so advent_dec takes precedence; it is filled with the Dec 17 gospel only as a placeholder.
Status: verify:true - this data has not yet been checked against an official ordo and must be verified before release.

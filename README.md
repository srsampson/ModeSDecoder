#### ModeSDecoder - A Mode-S and ADS-B Decoder in Java for Windows and MySQL

Note: After my Windows Rebooted and Downloaded Updates, the MySQL no longer works, and clients can't login.
Did I mention I hate MySQL? Anyway, I'll probably port this to a different database, as MySQL is not for hobbyists.

This was written for the Mode-S Beast Receiver. This is a great little reciever that is based on an FPGA. It would be difficult to adapt it to another receiver, as these things all have different binary protocols to snag the data off of them.

The program drinks-in the serial port data (ignores the Mode-AC if enabled) and creates data structures in order to decode the bits into values we can all understand, and use for a Database Application.

This is a hobby program I've played around with to store Mode-S and ADS-B data in a MySQL database.

The database is designed so that as new targets come in, their Mode-S ICAO number is added to the ```modestable``` table, and the positions saved in the ```target``` and ```targetecho``` table. Sooner or later this aircraft will land or fade-out, and the database will move it to the ```history``` table. If it pops up again, then it is issued a new flight number. The data in the ```target``` table then, is the current data. When those targets fade or land, they are deleted from this table, and moved to the ```targethistory``` table.

Currently it only updates the registration (N-Number) for USA aircraft, as they are assigned 1:1, and I don't have any info on other countries. I don't do an Internet lookup, as most sites don't allow it for free.

![Sample Query](sample.png)

With a database, you can then grab data and display it on a GUI for example (my antenna is on my desk and is not very good).

![Sample Display](radar.png)

#### ModeSDecoder - A Mode-S and ADS-B MySQL Database Application in Java

This application written for the Mode-S Beast Receiver. This is a great little reciever that is based on an FPGA. It would be difficult to adapt it to another receiver, as these things all have different binary protocols to snag the data off of them.

The program processes the serial port data (ignores the Mode-AC if enabled) and creates data structures in order to decode the data. This decoded information is placed in a MySQL database. Multiple receivers can be used, each running this application and using a separate ```radar_site``` identification in the configuration file.

The database is designed so that as new targets are detected, their Mode-S ICAO number is added to the ```icao_table```, and the decoded information saved in the ```target_table``` and ```target_echo``` table. Sooner or later this target will land or fade-out, and the database will move it to the ```target_history``` table. If it pops-up again, then it is issued a new flight number. Thus, the ```target_table``` data is the current airborne data.

The US registration (N-Number) are also added when the ICAO number is decoded. These are assigned 1:1 in the US. No other countries are decoded.

#### Duplicate Receiver Data
The Mode-S data received has a lot of redundancy in it. Each target may transmit identical information to several radar sites. You will often see three or four transmissions with the same exact data. This application divides the Mode-S into two queues, one for short blocks and one for long. Since the long blocks are used to calculate position, this data is not filtered. On the other hand, the short block duplicates are dropped. This greatly reduces the work of decoding the information, and storing it in the database.

#### TCAS Receiver Data
The DF16 download format has some interesting TCAS data transmitted, and this is stored in the ```tcas_alert``` database table. It is normally deleted after a few minutes, but right now the delete timer is disabled, so I can examine the data, and delete it manually.

#### METAR Internet Data
Airborne targets transmit their altitude based on a standard 29.92 Hg pressure. This altitude may be higher or lower based on the current airport altimeter. The application connects with a NOAA weather site, and the airport selected in your config file is used to collect the current altimeter. This value is then used to provide a correction based on the Pressure altitude. This data is currently just printed, but the plans are to add it to the ```target_table``` so database users can add the calculated difference as needed.

**Development Environment:**

Apache NetBeans IDE 22   
Java: 22.0.1; OpenJDK 64-Bit Server VM 22.0.1+8-16   
Runtime: OpenJDK Runtime Environment 22.0.1+8-16   
System: Windows 11 version 10.0 running on amd64; UTF-8; en_US (nb)   

With a database, you can then grab data and display it on a GUI.

![Sample Display](radar.png)

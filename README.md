#### ModeSDecoder - A Mode-S and ADS-B Decoder in Java for Windows
(Untested on other OS)

This was written for the Mode-S Beast Receiver. This is a great little reciever that is based on an FPGA.

The program drinks-in the serial port data (ignores the Mode-AC if enabled) and creates data structures in order to decode the bits into values we can all understand, and use for GUI or Database Applications.

This is a hobby program I've played around with. It's in the form of a Library that you would add to your GUI or Database program.

I probably will write a MySQL Database application for it next.

Sample Output (very poor antenna):

**ICAO, Callsign, Altitude, Lat, Lon, UTC Timestamp**
```
A1AC24 N207BG 42475 35.344666 -97.613468 2024-07-06 17:02:29.237
A00CE0 RPA4535 5150 35.342010 -97.521114 2024-07-06 17:02:29.237
A0FBB7 UAL1098 35000 35.352859 -97.600994 2024-07-06 17:02:30.238
A1AC24 N207BG 42500 35.344711 -97.615356 2024-07-06 17:02:30.238
A00CE0 RPA4535 5200 35.343297 -97.518798 2024-07-06 17:02:30.238
A0FBB7 UAL1098 35000 35.351397 -97.599150 2024-07-06 17:02:31.239
A1AC24 N207BG 42500 35.344757 -97.617588 2024-07-06 17:02:31.239
A00CE0 RPA4535 5225 35.344025 -97.517509 2024-07-06 17:02:31.239
A0FBB7 UAL1098 35000 35.349768 -97.597104 2024-07-06 17:02:32.24
A1AC24 N207BG 42500 35.344787 -97.619311 2024-07-06 17:02:32.24
A00CE0 RPA4535 5250 35.344740 -97.516285 2024-07-06 17:02:32.24
A0FBB7 UAL1098 35000 35.348325 -97.595293 2024-07-06 17:02:33.241
A1AC24 N207BG 42525 35.344803 -97.621021 2024-07-06 17:02:33.241
A00CE0 RPA4535 5275 35.344740 -97.516285 2024-07-06 17:02:33.241
A0FBB7 UAL1098 35000 35.346542 -97.592983 2024-07-06 17:02:34.241
```

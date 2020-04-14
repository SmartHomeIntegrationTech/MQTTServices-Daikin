This software allows to discover a Daikin Adapter BRP069A62 in the network and read its endpoints. It will then publish it to an MQTT server according to the homie convention. This software is in its early stage, so please be careful when using it!

##Launching it

You can launch it with `java -jar daikin-0.0.1.jar` which will give you a few options.

If you don't know your IP address you can run `java -jar daikin-0.0.1.jar -d` and it will discover existing Daikin Adapters through MDNS, or you can just run `java -jar daikin-0.0.1.jar -g` to start a Setup GUI. If you launch the GUI for the first time, it will open a dialog, where you can enter the IP address of the adapter, or hit the discover button and get the first adapter found.

Once the proper UI opens you can hit the discover button which will try to identify all possible endpoints that can be read from this adapter. For this it will use the UnitProfile endpoint, as well as a text file with some other endpoints that I found in the app.
*ADVANCED: You can also try your own endpoints with the `-e` option*

Once all endpoints have been discovered (which may take a minute) the table for editing information should be filled. 

![alt text](imgs/SetupUI.png "Screenshot of Setup UI")

##Table columns

**Pollinterval** allows you to set how often things are read. You can get some of my preferred values by hitting the `Guess polling` button. But please double check if it is what you would expect and let me know if not.

* NEVER items will be ignored and never read
* ONCE items will be read only during startup. This is useful for things like Hardware version which will very like never change, but it is nice to have it available
* MINUTELY items will be read every minute
* HOURLY items will be read every hour
* BI_HOURLY items will be read ever other hour
* DAILY items will be read every 24h

Currently the polling interval is relative to the start time of the polling (`-p` option).

**Name** is a human readable name that will appear in a homie client.

**Unit** is the physical unit of that property. You can hit the `guess units` button and I set °C to all items ending in temperature. For some other items I can actually read out the unit from the unitprofile.

**Format** allows to specify the range of expected values. For numbers that is `min:max` for enums `on, off,explode`. See the homie convention for more information.

**DataType** is GUESSED from the current value. This may be WRONG! for example the temperature may be exactly 22°C during reading, but the unit can actually provide 22,1°C. So if you know that this field is not integer, change it to float or string.

**Value** If you hit discover you get the current value for information, this allows you make a better assessment of the data type or the expected values.

## Final steps in GUI
At the bottom you can find some inputs related to the MQTT settings. The device name must match the homie convention which is lower-case [a-z0-9]+.

The last step of the GUI is to write the config file which is done by the `save file` button. The default name is `PollingSettings.json`

If you don't like GUIs you can run ` java -jar daikin-0.0.1.jar -w 192.168.188.200` which will scan the given IP address and write a `PollingSettings.json` file that you can edit.

## Running the polling service
Now that you have a proper `PollingSettings.json` you can finally launch ` java -jar daikin-0.0.1.jar -p` which will start the polling and update the data in the homie convention.

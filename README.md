# Android SDK Sample

This sample project demonstrates how to use the iMatch Android SDK. 

# How to install

1. Clone this repository and open the project with Android Studio.
2. (optional) obtain a valid license file for [Regula DocumentReader](https://licensing.regulaforensics.com/) and place in the /res/raw folder.
3. Build and run the application on a device.

# How to use the SDK
The iMatch SDK exposes it's functions through the ImatchDevice singleton and several event listeners. 

## Basic steps
1. The ImatchDevice contains a Bluetooth LE PairingService to search for and connect to nearby iMatch devices. Initialize this service, and let it search for iMatch devices.
2. Pick a device and supply it's name and address to the ImatchDevice Init function. You now have an active connection with an iMatch device. 
3. Implement the desired listeners to enable your app to handle events sent from the iMatch device to your app.
4. Send commands to the iMatch by using either the Send() (asynchronous) or SendWithResponse() (synchronous) and handle the results.

# More information
* See the sample code for the exact implementation
* See the [iMatch wiki](https://github.com/Gridler/cordova-plugin-imatch/wiki) to learn about the possible commands you can send to the iMatch.
# Android SDK Sample

This sample project demonstrates how to use the iMATCH Android SDK. 

# How to install

1. Clone this repository and open the project with Android Studio
2. (optional) obtain a valid license file for [Regula DocumentReader](https://licensing.regulaforensics.com/) and place in the /res/raw folder
3. Build and run application on device

# How to use the SDK
The iMATCH SDK exposes it's functions through the GubaniDevice singleton and several event listeners. 

## Basic steps
1. The GubaniDevice contains a Bluetooth LE PairingService to search for and connect to nearby iMATCH devices. Initialize this service, and let it search for iMATCH devices.
2. Pick one device and supply it's name and address to the GubaniDevice Init function. You now have a active connection with an iMATCH device. 
3. Implement the desired listeners to enable your app to handle events sent from iMATCH to your app.
4. Send commands to the iMATCH by using either the Send() (asynchronous) or SendWithResponse() (synchronous) and handle the results.

# More information
* See the sample code for the exact implementation
* See [this wiki](https://github.com/Gridler/cordova-plugin-imatch/wiki) to learn about the possible commands you can send to the iMATCH
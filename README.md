# Welcome to Mel
If you are looking for a solution to synchronize files and contacts between various Android devices and PCs you might want to read further.
It has no "cloud" thing built in and is intended for home network use. 
The main scenario (I guess?) is doing synchronization work in the background when you are sleeping and your phone plugged into the charger.

## When to use and when not to use?
When:
- You want to share files or contacts between your devices
- You don't want your files on another persons computer (aka cloud)
- If you can't or don't want to go trough configuring an home cloud solution.
- Your only available server hardware is an Android phone
- It is ok for you if syncing does not happen immediately

When not:
- You don't have a home network
- Your old Android phone does not sport Android v 4.4.4 or above
- You want your files to be globally available
- Syncing should always be immediately

## Requirements
Two devices:
- Android:
  - v 4.4.4 or above
  - Android Q not tested
  - some free storage on that device
- PC*:
  - a Linux distribution of you choice (Windows is mostly done but still WIP)
  - with at least a Java 11 runtime installed
  - Graphical Interface of your choice (currently there is no CLI version)

<sub>
*If you have a decently recent linux distro installed the requirements should be met.
Exemptions may be server or cloud versions of those distros that do not come with a graphical user interface.
You would therefore use a Command Line Interface (CLI) that currently does not exist for Mel.
To be more precisely: it will run without a GUI but you won't be able to pair or handle file conflicts then.
</sub>

## Privacy & Security
Though Mel only communicates via Wifi or ethernet it still does not trust your network and therefore encrypts pretty much everything.
Because you still may have Alexa around who always listens carefully or use public wifi.
But because writing your own crypto often goes horribly wrong Mel makes use of Javas SSL implementation.
That still requires creating public and private keys where also tons of thing may go wrong, right?
For this very reason [Bouncy Castle](https://www.bouncycastle.org/java.html) is employed. BC is quite old and made by prople who know their stuff.
So no own crypto is used in this program.


## License
Until I have chosen a proper license (BSD/GNU/Apache...) neither republishing/altering the code nor the binaries is permitted.
There are parts of this program which are made by others. These may alter or republish these files according to their respective licsenses.
You can find all licenses under auth/src/main/resources/de/mein/auth/licenses.html.

## Current issues
- does not work on Windows (will be addressed)
- Downloads may abort. Restarting fixes this (will be addressed)
- File transfer over Wifi may be slow
- Notifications are ugly on PC

## Dev stuff
### What language is it written in?
Mostly Java and partially in Kotlin. If I had to do it again this would be a Kotlin only show.
But back in the days when I started working on this it was in an early stage, especially regarding to Android.

### Is it complicated?
From an algorithmic POV: no. Fortunately Android has many quirks that require extra work.
For example:
- the contacts database has 15 columns named "data$Number"
- inserting a blob (contact image) will alter the blob (why???)
- the Storage Access Framework is a complete fuckup!
  - it is slow!
  - it uses different [URIs](https://en.wikipedia.org/wiki/Uniform_Resource_Identifier)
    - a URI is supposed to be a naming scheme. That means a plain String. 
    On Android it is an Java-Object containing this string with permissions attached to it.
  - its lacks a lot of Documentation
  - many things do not work on a sufficient amount of Android versions
  - it is slow!
  - one cannot emphasize enough how slow it is!
- databasing is different
  - your API is similar to that of jdbc but different, so can manufacture an abstraction layer if you want your stuff to run with both
  - you only got a 4 data types: Long, Double, String, byte[]
  - database black hole: you can insert big things into a column but then cannot read it because "Row too big to fit into CursorWindow" (WTF??)
- file modification data is limited to seconds not milliseconds
- different devices come with different command line tools though on the same Android version
- your background service might get killed at any time

In Java I only found one thing that is broken:
the file watch service that watches folders for changes:
It neither delivers complete nor correct results on an Platform. All you can rely on is that something has happened.

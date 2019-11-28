# Welcome to M[e](https://xorserv.spdns.de)l - Beta*

**warning: Just tested the build/update system. Once functional builds arrive this text will be removed. My phone just got Android 10 recently. Therefore it is not compatible with Android 10 yet. Things broke. Fix is in progress.**

If you are looking for a solution to synchronize files and contacts between various Android devices and PCs you might want to read further.
It has no "cloud" thing built in and is intended for home network use. 
The main scenario (I guess?) is doing synchronization work in the background when you are sleeping and your phone plugged into the charger.

[The homepage is here](https://xorserv.spdns.de)

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

## What does it do?
All services come with a client/server role. By design all server services do not need any further interaction after creation and setup. 

- Contacts synchronization
  - can sync with Androids telephone book
  - but also can just store it
- File synchronization
  - keeps the content of a folder identical on multiple devices
  - comes with conflict solution (if files have been altered differently across devices)
  - supports symlinks on Linux (sort of for Windows too)
- File Dump
  - think of it as a one way file synchronization service
  - client sends all files of a folder to the server
  - files deleted on the client are not deleted on the server
  - folder structure remains
  - server aitomatically resolves conflicts by adding dates and IDs to duplicate file names
  
Syncing can be delayed (and it is by default) until certain conditions hold:
- you have WiFi
- charger is plugged into the phone

But this is up to you. Just keep in mind that listening for file system changes and incoming messages drains you battery.
  
 ## How to use
 - Find a device that suits as a server (S) and at least one you want to be mobile (M).
 - Install Mel on both of them
 - Have them connected to the same network
 - make sure that ports 8888 and 8889 are not blocked by any firewalls
 - Pair them by going to "Pair" on one device
   - it should now find the other
   - click "Pair"
   - now accept the certificates on both devices (make sure they match)
 - create a server service on S
 - go to "Access" on S and allow M to talk to the created service.
 - create the according client service on M
   - chose S, and S's service as server-service
   - click "Create Service"

## Requirements
Two devices:
- Android:
  - v 4.4.4 or above
  - Android Q not tested
  - some free storage on that device
- PC*:
  - a Linux distribution of you choice or Windows (7 and up) if you prefer that
  - with at least a Java 11 runtime installed
  - Graphical Interface of your choice (currently there is no CLI version)

<sub>
*If you have a decently recent linux distro installed the requirements should be met.
Exemptions may be server or cloud versions of those distros that do not come with a graphical user interface.
You would therefore use a Command Line Interface (CLI) that currently does not exist for Mel.
To be more precise: it will run without a GUI but you won't be able to pair or handle file conflicts then.
</sub>

## Privacy & Security
Though Mel only communicates via Wifi or ethernet it still does not trust your network and therefore encrypts pretty much everything, because you still may have Alexa around who always listens carefully or use a public wifi.
But because writing your own crypto often goes horribly wrong Mel makes use of Javas SSL implementation.
That still requires creating public and private keys where also tons of thing may go wrong, right?
For this very reason [Bouncy Castle](https://www.bouncycastle.org/java.html) is employed. BC is quite old and made by people who know their stuff.
So no own crypto is used in this program.


## License
You can find all licenses and according modules and usage under `auth/src/main/resources/de/mel/auth/licenses.html`.

## Current issues
- does not work on MacOS (won't be addressed: too expensive, no FileWatcher)
- File transfer over Wifi may be slow
- Notifications are ugly on PC

## Dev stuff
### Modules
- `app`: Android implementation of `auth`, `filesync`, `contacts` and `filedump`
- `auth`: handles pairing, key management, connections, sending/receiving data, service management, Bash/Shell commands
- `authfx`: wraps `auth` into a GUI on PCs so you can click on things
- `blog`: runs a web server with your blog. 
- `contacts`: stores contacts
- `contactsfx`: a bit of GUI
- `filesync`: file syncing, all the logical stuff
- `filesyncfx`: adds GUI for creating/editing services and conflict solving
- `filedump`: file syncing, in one direction only, derivative of `filesync`
- `filedumpfx`: a bit of GUI
- `fxbundle`: bundles `authfx`, `contactsfx`, `filesyncfx` and `filedumpfx`
- `icons`: icons 'stolen' from KDE Plasma 5 plus some own
- `json`: json lib created by Douglas Crockford
- `konsole`: make reading command line arguments a bit easier and stay simple
- `lok`: Tells you where the log message came from
- `miniserver`: a small https server. because why not?
- `serialize`: crafts lovely JSONs from you objects and vice versa
- `serverparts`: part of `miniserver`/`blog` that is required to run a server as a service in Mel
- `sql`: reads and writes objects to SQL databases



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
    On Android it is an Object containing this string with permissions attached to it.
  - its lacks a lot of Documentation
  - many things do not work on a sufficient amount of Android versions
  - it is slow!
  - one cannot emphasize enough how slow it is!
  - renaming a DocumentFile cannot handle `?` in the new name: it will escape to `_`
  - there is no creation time for files
  - with Android 10 you have absolutely no access outside of the app's private directory using `java.io.File`. But there is more to it.
  It also restricts the usage of shell commands to your private directory. Plus, executing programs like `find` leads to wrong(==empty) results instead of throwing errors.
  That renders shell commands pretty much useless or even dangerous.
  - `ContentResolver.query()` ignores Uri queries and selections (like you do in SQL). But only in most cases. And there is no documentation on that.
    - Of course, it ignores sort order as well, mostly
- databasing is different
  - your API is similar to that of jdbc but different, so your must manufacture an abstraction layer if you want your stuff to run with both
  - you only got 4 data types: Long, Double, String, byte[]
  - database black hole: you can insert big things into a column but then cannot read it because "Row too big to fit into CursorWindow" (WTF??)
- file modification data is limited to seconds not milliseconds
- different devices come with different command line tools though on the same Android version
  - some programs like `find` may accept your parameters but silently ignore them: `mindepth` for example
- your background service might get killed at any time

In Java I found a few things broken:
- the file watch service that watches folders for changes:
  It neither delivers complete nor correct results on any Platform. All you can rely on is that something had happened.
- My JUnit tests somehow end with core dumps of the underlying JVM (on the build machine only)
- If TLSv1.3 is enabled and running (which by default it is) all threads of the HTTPServer's executor will eventually get stuck in an infinite loop


### Intellij does not rebuild all changed Files when running or debugging (Linux)
Also: you have to clean the project before your debug, because the code running and the code in the editor are not the same (after you made some changes).

Your inotify limit might be too low. To fix that the value to something higher:
`sudo nano /etc/sysctl.conf`, then add or set
`fs.inotify.max_user_watches = 1000000`, then apply settings from this file:
`sudo sysctl -p`. 

Hint: This is permanent.

## Beta
This application is currently in beta. That means things may go wrong, break or set you favourite pet on fire.
If that happens you can send me a message, write an issue or come up with a pull request.

# Mel.auth
Note: words that do not start with a capital letter in English but are written with one refer to classes or interfaces
## What is it?
MeinAuth is a framework which allows secure communication between multiple nodes on a network.
But there is quite a lot of stuff out there that does the same, eh?
You are right about that. So what's the bloody difference?

MeinAuth makes use of public key encryption to encrypt communications and authentication (Thats still nothing new).
It "hides" all of that nasty key handling stuff behind a bluetooth-like pairing process which makes MeinAuth instances trust each other.

Plus: it is service oriented. So you have got an instance running that knows every other instance you trust.
There are also services "living" in your MeinAuth instance. They can communicate with the other MeinAuth instances if you wish so. 
But initially ("trust no one") this is not permitted. 

For daily use you want two or more instances that trust each other and have services running that are allowed to talk to other MeinAuth instances.

### Why on earth did you write all of that stuff?
Because I could not find a solution that worked on an old PC and Android as well. There is owncloud which comes with a ton of dependencies and is not exactly easy to configure when it comes to https. 
There also is syncThing which is python based (that means dependencies) but offers only(?) file sync.
Plus I wanted the Android version to offer the same capabilities as the PC version.
Plus I wanted the Android version to use the same code.
So having in mind to get rid of all of that dependencies to have one clean single file that you can start and a simple android version of it, I stated this thing.
### Questions
- have you written encryption code yourself?
  - Beware, no! 
  - Communication uses Javas HTTPSSocket implementation
  - Certificate generation is delegated to Bouncy Castle
  
# Offering Services within MeinAuth
First things first: a service (subclasses MeinService) can offer some sort of resources over the MeinAuth framework.
Features of a service:
- has a ServiceType
    - is instantiated by an associated BootLoader
- has a UUID
  - other MeinAuth instances refer to this service via this UUID
- comes with an optional ExecutorService for multithreading purposes

When MeinAuth boots up it looks for all configured MeinServices that it must start.
A service is of a certain ServiceType. This refers to a BootLoader. 
MeinAuth then asks the BootLoader to boot this certain instance up.
Therefore the BootLoader is fed with all important information by the MeinAuth framework. 
This includes the UUID and a working directory where all configs and databases of the service are stored.
The BootLoader then has to load the config files or databases and return the required MeinService instance.
That's the basic overview. Booting happens in two stages. Read below. 
## Booting Services
To prevent your phone to drain all of its battery because it is indexing and hashing the content of the utterly big folder that you want it to keep in sync with your PC,
I have decided that power management is something that we want. 
As you have just read excessive indexing eats too much battery, especially if you just started the app.
So the idea is:
- do everything that can be done quickly on service boot level `SHORT`
  - this is executed by the BootLoader. Always!
- check if PowerManager allows to use a lot of power now
  - this depends on whether your phone is plugged in or has WiFi
  - if allowed: do long lasting work on service boot level `LONG`
  - if not: delay until power criteria are fulfilled
- when all services reached level `SHORT`, MeinAuth starts its Sockets and the services can communicate.

### Different boot levels
Every service is reachable after they have finished boot level `SHORT`. 
While some services are fully functional at this point, others might require the second boot level to finish or can only fulfill a subset of all of their tasks.
A Service might not be aware of all of its available resources before finishing level `LONG`. Therefore asking for resources on level `SHORT` might cause serious issues.
For example: A File-Sync service cannot send a certain file if it has not indexed its directory and checked the required file actually exists.

## The Boot procedure
Everything starts with an instance of `MeinAuthSettings` and a list of `Bootloader` classes which are fed to and instance of `MeinBoot`.
Calling `boot` on the `MeinBoot` instance returns a callback which lets you execute code once the system has started.
The Auth-database is created (or loaded) which contains information about services which can be created and actual service instances.
`MeinBoot` reads the database, finds the according `Bootloader` class. The `Bootloader` creates an instance of it and kindly asks it to boot the services.
The `Bootloader` returns an instance of `MeinService` which runs the services logic. The `MeinService` instance is equipped with a `BootLevel`, which can be either `LONG` or `SHORT`.
When all services have reached `BootLevel.SHORT`, MeinAuth can start to communicate.

### Booting a Service
A custom Bootloader must extend `Bootloader`. `Foo` has to be known to the `MeinBoot` instance which boots up `MeinAuthService`
It must implement `bootLevelShortImpl` which returns an instance of the service. 
The bootloader has the `bootloaderDir` property which points to a folder that is unique for each `Bootloader`. 
A custom bootloader can organise the files and databases of the various instances therein if necessary.

## Message handling



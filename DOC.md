# MeinAuth
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
## Booting Services & Message handling
To prevent your phone to drain all of its battery because it is indexing and hashing the content of the utterly big folder that you want it to keep in sync with your PC,
I have decided that power management is something that we want. 
As you have just read excessive indexing eats too much battery, especially if you just started the app.
So the idea is:
- do everything that can be done quickly on service boot level 1
    - this is executed by the BootLoader. Always!
- check if PowerManager allows to use a lot of power now
    - this depends on whether your phone is plugged in or has WiFi
    - if allowed: do long lasting work on service boot level 2
    - if not: delay until power criteria are fulfilled
- when all services reached level 1, MeinAuth starts its Sockets and the services can communicate.

### Different boot levels
Every service is reachable after they have finished boot level 1. 
While some services are fully functional at this point, others might require the second boot level to finish or can only fulfill a subset of all of their tasks.
A Service might not be aware of all of its available resources before finishing level 2. Therefore asking for resources on level 1 might cause serious issues.
For example: A File-Sync service cannot send a certain file if it has not indexed its directory and checked the required file actually exists.
### Booting a Service


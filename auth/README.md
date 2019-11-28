# Mel.auth
Note: words that do not start with a capital letter in English but are written with one refer to classes or interfaces
## What is it?
MelAuth is a framework, which allows secure communication between multiple nodes on a network.
But there is quite a lot of stuff out there that does the same, eh?
You are right about that. So what's the bloody difference?

MelAuth makes use of public key encryption to encrypt communications and authentication (Thats still nothing new).
It "hides" all of that nasty key handling stuff behind a bluetooth-like pairing process which makes MelAuth instances trust each other.

Plus: it is service oriented. So you have got an instance running that knows every other instance you trust.
There are also services "living" in your MelAuth instance. They can communicate with the other MelAuth instances if you wish so. 
But initially ("trust no one") this is not permitted. 

For daily use you want two or more instances that trust each other and have services running, which are allowed to talk to other MelAuth instances.

### What functionality is in here?
- Communications
- Encryption, Hashing
- Basic file handling (an abstraction is needed because of Android)
- Bash/Shell commands
- Updater

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
  
# Offering Services within MelAuth
First things first: a service (subclasses MelService) can offer some sort of resources over the MelAuth framework.
Features of a service:
- has a ServiceType
    - is instantiated by an associated BootLoader
- has a UUID
  - other MelAuth instances refer to this service via this UUID
- comes with an optional ExecutorService for multithreading purposes

When MelAuth boots up it looks for all configured MelServices that it must start.
A service is of a certain ServiceType. This refers to a BootLoader. 
MelAuth then asks the BootLoader to boot this certain instance up.
Therefore, the BootLoader is fed with all important information by the MelAuth framework. 
This includes the UUID and a working directory where all configs and databases of the service are stored.
The BootLoader then has to load the config files or databases and return the required MelService instance.
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
- when all services reached level `SHORT`, MelAuth starts its Sockets and the services can communicate.

### Different boot levels
Every service is reachable after they have finished boot level `SHORT`. 
While some services are fully functional at this point, others might require the second boot level to finish or can only fulfill a subset of all of their tasks.
A Service might not be aware of all of its available resources before finishing level `LONG`. Therefore asking for resources on level `SHORT` might cause serious issues.
For example: A File-Sync service cannot send a certain file if it has not indexed its directory and checked the required file actually exists.

## The Boot procedure
Everything starts with an instance of `MelAuthSettings` and a list of `Bootloader` classes which are fed to and instance of `MelBoot`.
Calling `boot` on the `MelBoot` instance returns a callback which lets you execute code once the system has started.
The Auth-database is created (or loaded) which contains information about services which can be created and actual service instances.
`MelBoot` reads the database, finds the according `Bootloader` class. The `Bootloader` creates an instance of it and kindly asks it to boot the services.
The `Bootloader` returns an instance of `MelService` which runs the services logic. The `MelService` instance is equipped with a `BootLevel`, which can be either `LONG` or `SHORT`.
When all services have reached `BootLevel.SHORT`, MelAuth can start to communicate.

### Booting a Service
A custom Bootloader must extend `Bootloader`. `Foo` has to be known to the `MelBoot` instance which boots up `MelAuthService`
It must implement `bootLevelShortImpl` which returns an instance of the service. 
The bootloader has the `bootloaderDir` property which points to a folder that is unique for each `Bootloader`. 
A custom bootloader can organise the files and databases of the various instances therein if necessary.

# Messaging
## General
Every instance of Mel tries to keep connections to all other known instances as long as its power configuration allows it.
That means that if your phone is on battery or has no Wi-Fi Mel will shut down after a slight delay. Mel differentiates between two types of connections:
- normal / general connection
    - you can send messages to any other service on the other side, or to `MelAuth` itself
    - can send and receive Messages and Requests in JSON
    - all services share this connection
- isolated connection
    - ties two `MelService`s together: one on your side, one on the other
    - allows sending binary date (aka large files)

## A Message itself
Messages that are sent from one Mel instance to another are realized by extenting `ServicePayload`.
The class itself comes with a `bootLevel`. If your service receives a `ServicePayload` Mel creates a new instance of it and checks whether the current boot level of the targeted service is sufficient to accept this message.
This way you can determine, which messages you service can deal with despite not being completely booted.
If everything that you want to send is a String you may look at `EmptyPayload`. This class uses the `intent` field to store your String.
If you want to send more than that just add new Fields to your extended `ServicePayload`. 
Keep in mind that only primitives (Integer, String ...), `SerializableEntity`s and Collections of those are serialized.
In case you got something you want to keep private you can annotate the field with `@JsonIgnore`. For more details [have a look here](../serialize/README.md) 

## How to use?
Let's take the POV of a `MelService`. As we know this is the component containing the business logic of whatever you are trying to do.
`MelService` has a reference to `MelAuthService` which you can use to communicate.
Have a look:
```java
Promise<MelValidationProcess, Exception, Void> connected = melAuthService.connect(PARTNER_CERTIFICATE_ID);
connected.done(mvp -> {
    // this becomes the message for our partner
    EmptyPayload payload = new EmptyPayload();
    // in this example we only send a short text
    parload.setIntent(MY_INTENT);
    Request<SomeAnswerType> request = mvp.request(PARTNER_SERVICE_UUID, payload);
    request.done(someAnswer -> {
        // do something with the answer
    }).fail( exception ->
        // handle exception
    );
}).fail( exception ->
    // handle exception
);
```
Keep in mind that code that runs in the `done()` and `fail()` methods runs in network threads. That means that you should not block that thread for too long.
That also means that you should avoid doing GUI stuff here.
 



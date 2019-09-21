# Mel.Konsole
helps you digesting command line arguments.
## General use
Create an instance of `Konsole` via constructor and pass it an instance of `KResult` whereas `KResult` which holds the read values afterwards.
You may load this object from a file or get it from somewhere else and want to apply the command line aguments.

After you got your instance of `Konsole` you can daisy chain parameters with it. These might either be `optional` or `mandatory`.

Example:
```kotlin
Konsole(myResultObject)
    .optional("-myarg", "my description") { myResultObject, args -> println("I handle the args here")}
    .mandatory("-m", "this is a MUST") { result, args -> result.value = args[0].toLong()}
    .optional("-file", "I depend on -myarg", { result, args -> result.filePath = Konsole.check.checkRead(args[0]) }, Konsole.dependsOn("-myarg"))
    .handle(commandLineArgs)
```
Do not forget to call `handle(args)` after you set up everything.
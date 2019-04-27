# What is it?
A library that serializes Java objects to JSON and back.

## Why?
Back when I started writing this I could not find a solution that can serialize and deserialize circular data structures.
I tried various libraries and only got one direction going. That is useless and I started writing this.

## How does it work?
### General
Every object that you want to (de)serialize must implement SerializableEntity.
This is to ensure you don't accidentally serialize and expose private information.
The framework comes with a set of FieldSerializerFactories stored in `FieldSerializerFactoryRepository`.
If you want a certain data type to be serialized, you can write and implementation of `FieldSerializerFactory` and `FieldSerializer`, put it in there 
by calling `FieldSerializerFactoryRepository.addAvailableSerializerFactory()`. To deserialize that stuff again you do the very same thing:
implement `FieldDeserializer` and `FieldDeserializerFactory` and pass it to `FieldSerializerFactoryRepository.addAvailableDeserializerFactory()`.

To serialize things call `SerializableEntitySerializer.serialize(entity)`.
To deserialize a JSON call `SerializableEntityDeserializer.deserialize(json)`.

### Serialization
The whole `SerializableEntity` is traversed field by field. If the framework finds a `FieldSerializerFactory` that can serialize the current Field, it will.
Therefore it asks every factory whether or not it can serialize a given field and store that information for performance purposes.
If your Factory claims that it can serialize it, your Factory creates a `FieldSerializer` that then translates the given Field to a JSON string.
By default the framework can serialize primitives like Integer, String and blobs(binary arrays).
Additionally it can serialize more complex data structures like Lists, Sets, Maps and Arrays. These structures are generic, which means they have a certain data type that they accept.
In the current implementation of Lists etc the generic information is stored for the (e.g.) List only and then every item in there has to be of that generic type.
This MUST be a type that can be instantiated directly (or you have your own implementation for dealing with it).
So it is not supported to have a List of the generic type `Object` and then put Strings and Integers in there.
You can have Lists of different types of `SerializableEntity` though.
Note: every serialized instance of `SerializableEntity` has a `_type` and `$id` field. If and Entity has already been serialized but appears somewhat later in the traversing process again
it is not serialized again. Instead it appears as a reference to its `$id` field.
Example: the following (double linked) object hierarchy
```json
{
  "$id": 1,
  "_type": "MySerializableEntityClass",
  "name": "NAME 1",
  "childEntity": {
    "$id": 2,
    "_type": "MySerializableEntityChildClass",
    "parentEntity": {
      // the parent entity goes here again
    }
  }
}
```
translates to:
```json
{
  "$id": 1,
  "_type": "MySerializableEntityClass",
  "name": "NAME 1",
  "childEntity": {
    "$id": 2,
    "_type": "MySerializableEntityChildClass",
    "parentEntity": {
      "$ref": 1
    }
  }
}
```

### Deserialization
Does the whole thing backwards. Note that every `SerializableEntity` must have a zero-parameter constructor.
Otherwise the Deserializer cannot create a new instance of it and throw an Exception.
The deserialization process is as follows:
- look at the class (that implements `SerializableEntity`) that is stored in the `_type` JSON-Field.
- create an instance of that class
- for each property/Field:
    - find a `DeserializerFactory` that can deserialize the class of the given Field
    - deserialize it using said Factory
    - set the value to the object using reflection
 
The framework resolves references (via `$ref`) automatically.

## Limit the depth for serialization
Call `SerializableEntitySerializer.serialize(entity,depth)`. The Serializer will stop serializing after diving `depth` steps into your data structure.
### Traces
You might want to limit the depth to low values but might need a certain path to remain in the JSON?
Call `SerializableEntitySerializer.serialize(entity,traceManager,depth)` where `traceManager` is constructed like this:
```java
new TraceManager().addForcedPath("[MyHorseEntity].HorseProperyIWant.Length");
```
If the TraceManager finds `MyHorseEntity` it will include the `HorseProperyIWant`.
`HorseProperyIWant` might be another `SerializableEntity` (like a leg) and that might have a property called `Length`. That will also be included.

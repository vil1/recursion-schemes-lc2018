## Avro schemas

Avro is a library (that has a Java version) and a data representation format. It is widely used in the data industry as it offers interesting features like schema versioning, automatic data upcast and downcast between schema versions and things like that.

Unfortunately, it has not been designed with functional programming and strong typing in mind, let alone recursion schemes... So our job will be a little harder this time.

You should find relevant hints in the comments in `src/main/scala/2-avro.scala` that'll help you tame that Avro beast.


##AVRO SCHEMA 101

### Inspecting schemas

* a single type : Schema
* a getType method that gives you the "kind" of schema: RECORD, ARRAY, INT, STRING, etc ...
* depending on the result of getType, it is safe to call certain methods : 
  * in case of RECORD, you can call `getFields()`
  * in case of ARRAY, you can call `getElementType()

### Building schemas

* For simple types, you can do something like : Schema.create(Schema.Type.INT)
* Some simple types (like Date) do not have a "natural" representation, but you can piggyback on existing schemas using so-called logical types : 
    * To represent dates as long : 
      `LogicalTypes.timestampMillis().addToSchema(Schema.create(Schema.Type.LONG))`
* For arrays you can do : SchemaBuilder.array().items(...)  <- you replace ... with the right Schema
* For structs (called record in the Avro realm) it's a bit more complicated : 
```
SchemaBuilder
    .record("nameOfMyRecord")
    .fields
    .name("nameOfTheField").`type`(...).noDefault  <- replace ... with the right Schema, don't forget the noDefault
    // add more fields as needed
    .endRecord
```

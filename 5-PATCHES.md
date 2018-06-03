## Applying JSON patches to GenericData

So far we assumed that each of our data sources have a known schema. Unfortunately, this is not the case for all out client's sources. For each of their business entities, the client maintains a Kafka topic where they log each modification of the entities, formatted as a JSON patch (as specified by [RFC 6902](https://tools.ietf.org/html/rfc6902)). 

These patches have the following structure : 

```json
{
    "op": "add",
    "path": "/profile/phoneNumbers",
    "value": {
        "type": "landline",
        "number": "+33123456789"
    }
}
```

They cannot provide us with a schema for such patches, because the *schema* of the `value` field depends on the *value* of the `path` field. Nevertheless, we want to be able to validate the incoming patches are correctly structured in respect with the target entity's schema. Moreover, since we also maintain a copy of the corresponding entities, we want to apply the patches to these entities.

In other words, we want to write a function that given a JSON patch, the schema of the target entity (as a `T[SchemaF])` and the current state of the target entity (as a T[DataF]):
1. verifies that the patche's `path` exists in the entity's schema (it points to a subshema) and that the patch's `value` complies to this subschema (producing a representation of `value` as a `T[DataF]`
2. uses that representation to perform the patche's operation on the current state of the entity. We'll only implement the `replace` operation. 

### Before you start

This last assignment is by far the most difficult, be (we hope that) it's also the most interesting. After having mastered the various "tactics" (pattern-functors, algebras, etc), the next hurdle on the path toward using recursion schemes in production is to become able to find a "strategy" to combine them to solve the problem at hand. It is often difficult at first to find the pattern-functor that matches the structure of the problem best, the right scheme to use on it or the carrier for the needed (co)algebras. 

From an educational point of view, it would be better if you tried to build your own solution from sratch and come up with your own strategy. By now you should know all the required tactics to solve this problem. We've encoded a simple representation for JSON patches in `src/main/scala/JsonPatch.scala`, you might also be interested in the definition of `matryoshka.patterns.ListF`.

Nevertheless, if you find yourself stuck or feel you might lack time to finish, we've laid out a solution in `src/main/scala/solutions/5-patches.scala`. But don't cheat and jump right at the solution before you've tried to come up with your own.


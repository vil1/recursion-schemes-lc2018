## It's a tough world out there

We now have a Schema both in its Pattern-Functor form and usable as an Apache Avro Schema
but it's not enough to have a working DataLake.

It might be obvious, but we need Data !

We managed to negotiate for our MVP with the Product Owner that all incoming data will be in JSON.

So now we need a way to make it work and ingest any kind of data into our Lake.
But for it not to become, an absurd pile of junk data : a DataSwamp
we can't trust the outside world with the data we'll receive.

We need to design a system that will validate incoming data according to the expected Schema
and output meaningful errors to our counterparts.

Our main objective for this part III of our workshop will be to :

> Leverage the power of JTO Validation (https://github.com/jto/validation)
> and Matryoshka to generate `Rules` that will validate any incoming Data

Being professionals we need this framework to be properly tested, of course a small sample unit test
will be of great help, but can't possibly be enough to handle the variety of Schema and Data we will be handling.

So another of your objective will be to generate arbitrary Schema and Data with ScalaCheck and
test that the validation `Rules` that you'll create *really do in fact* validate your data
The funny thing being that your generated *random* data should of course be compatible with your generated *random* schema.

The tests that you'll need are already coded in `src/test/scala/3-validation/SchemaRules.scala`
But it relies on a Schema *and* Data Generator in `src/main/scala/3-validation.scala` that you'll need to code.

You'll be provided with the Pattern-Functor needed to represent data (i.e. `GData`),
so all you need to complete this part is to code in `src/main/scala/3-validation.scala` :
* The `Rules` generation method `SchemaRules.fromSchemaToRules(schema: Fix[SchemaF]): JRule[Fix[GData]]`
* The Schema and Data generator `DataWithSchemaGenerator.genSchemaAndData: Gen[(Fix[SchemaF], Fix[GData])]`

More specific constraints are included in the comments of the source code.

Good Hunting.
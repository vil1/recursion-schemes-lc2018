## Data or Big Data ?

We now know how to represent and validate incoming Data, but our clients are whining that our ADT
serialized in Kryo is not exactly "usable" for them, and the "Data Management" is saying that this
is not exactly a "serious" and durable way of storing data.

So we're back at the drawing board !

We need to store data in a way that is both durable and usable.

As we'll be using Apache Spark for our batch processing framework, we now need to be able to read our data
as Apache Spark's Row data structure. Fortunately for your bandwith - we don't "really" need the whole Apache Spark
project - so we replicated the *Row* data structure so you may work offline easily.

But for our Stream Processing framework it makes more sense to use Apache Avro, so now let's finish the job !

Your mission if you accept it :
* Create the Algebra necessary to project any GData into Apache Avro or Apache Spark's data structure.
* you'll find more instructions in the `src/main/scala/4-spark-avro.scala`


Good Hunting.
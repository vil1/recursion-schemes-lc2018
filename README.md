# Recursion schemes without the barbed wire

Welcome to INC Inc. We are so happy we just hired a whole team of motivated engineers. Here at INC Inc. (INC is a Neat Company), we are proud proponents of statically-typed functional programming on the JVM — well, basically we use Scala.

On your first day at work, we have a good news and a bad news. The good news is: we have an exciting new mission, our most important client, AcmeCorp has tasked us with the construction of its "meta data lake", whatever that means. The bad news is they want it live by tonight.

But fear not, out architects have already designed the whole system and it works like a charm (on Powerpoint). All you need to do is to follow the specs and write a few Scala lines.

## Before we begin

You'll need to fulfill a few requirements in order to get everything work. You'll need to install 

* Java8 JDK
* sbt

Everything else should be pretty much working out of the box. This project has a few external dependencies though, so in order to save everyone some network bandwidth, it would be cool if you managed to clone the repository and issue the `sbt update` command in advance of the workshop.

## Structure of the workshop

This workshop is made of a series of practical exercises,
interleaved with a bunch of useful explanations about specific recursion schemes, patterns and techniques.
Each exercise lives in the main package of `src/main/scala`
and a solution to each exercise is available in the `solutions` package.

## TOC

* 0-PRELUDE
* 1-SCHEMA
* 2-AVRO
* 3-VALIDATION
* 4-SPARK-AVRO
* 5-PATCHES

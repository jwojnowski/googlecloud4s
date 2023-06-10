# googlecloud4s
[![License](http://img.shields.io/:license-MIT-green.svg)](https://opensource.org/licenses/MIT)
[![Maven Central](https://img.shields.io/maven-central/v/me.wojnowski/googlecloud4s-core_3.svg)](https://search.maven.org/search?q=googlecloud4s)

Purely functional Scala library for interaction with Google Cloud Platform.

## Disclaimer
This is still very much a work in progress project for small, personal apps, so I’d caution against (larger scale, high risk) production use.

## Getting started
To use this library add the following dependency to your `build.sbt`:

```scala
"me.wojnowski" %% "googlecloud4s-auth" % "x.y.z"
"me.wojnowski" %% "googlecloud4s-firestore" % "x.y.z"
"me.wojnowski" %% "googlecloud4s-storage" % "x.y.z"
"me.wojnowski" %% "googlecloud4s-pubsub" % "x.y.z"
```

## Overview
The library provides basic support for:

* CloudStorage (`googlecloud4s-storage`)
* Firestore integration (`googlecloud4s-firestore`)
* Authorisation (`googlecloud4s-auth`)
* Cloud PubSub (`googlecloud4s-pubsub`)

### Dependencies
It uses Cats Effect as its main framework, with `fs2` for streaming, `circe` for JSON (to be abstracted someday) and `sttp` for HTTP client.

### GraalVM Native Image
The library is verified to work with GraalVM Native Image.

### Scala versions
googlecloud4s maintains compatibility with both Scala 2.13 and Scala 3.

## CloudStorage
`Storage[F]` provides a set of basic operations on object storage:
* `put`
* `get`
* `list`
* `exists`
* `delete`

Both `put` and `get` operations use `fs2.Stream[F, Byte]` for data transfer.

## Firestore

### Supported operations
* `add` — without a name, always creates new document)
* `set` — with a name, creates new or overwrites if documents already exists
* `get`
* `update`  — updates a document using a provided function (using optimistic locking)
* `updateM`  — updates a document using a provided effectful function (using optimistic locking)
* `batchGet`
* `batchWrite`
* `stream` — streams documents matching very basic filters and sorting
* `delete`

### Encoding/Decoding
`FirestoreCodec` is a type class used to define encoders and decoders. It can be written manually, or derived by importing:
```scala
import me.wojnowski.googlecloud4s.firestore.circe._
```

## Authorisation
The library provides provisioning and validation of Google-issued access and identity tokens.

### Getting tokens
There are 3 token providers:
* `CredentialsTokenProvider` (for authentication outside of GCP using credentials JSON file)
* `MetadataServerTokenProvider` (for usage within GCP)
* `CachingTokenProvider` a simple caching wrapper for the above

All of them can be used to retrieve both `AccessToken` with given scopes and `IdentityToken` for a given audience.

### Token validation
`TokenValidator` can be used to validate and decode identity tokens.

## Cloud PubSub
PubSub implementation currently only supports sending messages. Receiving messages is only possible by pushing messages through HTTP trigger (e.g. in Cloud Functions or Cloud Run) and decoding them using `PushMessageEnvelope`.

### Supported operations
* `createTopic`
* `publish` (single message)
* `publish` (multiple messages)
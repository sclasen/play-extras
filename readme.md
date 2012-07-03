# heroku-play-extras

This project contains useful functionality for developing play 2.0 Scala apps, especially on Heroku

## Whats here

* Anorm Column and ToStatement impls that make dealing with Postgres HStore and Enum types easy.
* Enhanced Evolutions plugin that does locking so your dynos dont stomp on each other (will be in Play soon) and enable/disable evolutions per db.
* Extensions to play.api.libs.concurrent.Promise that make using services that return Promises (play.api.libs.ws.WS for example) easier to deal with.
* Actor Based logback console appender. Reduces synchronization contention on System.out, use instead of logback ConsoleAppender.
* PusherService for triggering events, verifying webhooks, and authorizing private and presence channels on Pusher addon.
* RedisService for configuring Jedis via REDISTOGO)URL, and some useful utilities.
* JsonAPI trait for mixing in to controllers that accept json payloads. Use scalaz validations to validate incoming json.
* Security trait for mixing into controllers that wish to force SSL on Heroku.
* Utilities for extracting and generating BasicAuth and URLEncoded values
* HerokuGlobal trait that does logging in the callback methods.


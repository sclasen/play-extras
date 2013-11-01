package com.heroku.play.api.db.evolutions

import org.slf4j.LoggerFactory
import play.api.db.DBPlugin
import play.api.{ Mode, Logger, Application }
import play.api.db.evolutions.{ EvolutionsPlugin => PlayEvolutionsPlugin, Evolution, ScriptAccessor, Evolutions, InvalidDatabaseRevision }
import play.api.db.evolutions.Evolutions._
import java.io.File

/*
Note to use this plugin, you need to add a conf/play.plugins file to your app that looks like

450:com.heroku.play.api.db.evolutions.EvolutionsPlugin

You should also disable the default play evolutions plugin in your conf file

evolutionplugin=disabled

*/
class EvolutionsPlugin(app: Application) extends PlayEvolutionsPlugin(app) {

  val log = LoggerFactory.getLogger(classOf[EvolutionsPlugin])

  override lazy val enabled = app.configuration.getConfig("db").isDefined && {
    !app.configuration.getString("new.evolutionplugin").filter(_ == "disabled").isDefined
  }

  val sleepInterval = app.configuration.getInt("evolutions.lock.sleep.interval").map(_.toLong).getOrElse(1000L)

  val upsOnly = app.configuration.getString("evolutions.ups.only").filter(_ == "enabled").isDefined

  override def onStart() {
    val api = app.plugin[DBPlugin].map(_.api).getOrElse(throw new Exception("there should be a database plugin registered at this point but looks like it's not available, so evolution won't work. Please make sure you register a db plugin properly"))
    api.datasources.foreach {
      case (ds, db) if app.configuration.getString("evolutions." + db).filter(_ == "enabled").isDefined => {
        /*DONT LOCK UNLESS THERE ARE CHANGES*/
        if (!evolutionScript(api, app.path, app.classloader, db).isEmpty) {
          withLock(ds) {
            val script = evolutionScript(api, app.path, app.classloader, db)
            if (!script.isEmpty) {
              app.mode match {
                case Mode.Test => Evolutions.applyScript(api, db, script)
                case Mode.Dev if app.configuration.getBoolean("applyEvolutions." + db).filter(_ == true).isDefined => Evolutions.applyScript(api, db, script)
                case Mode.Prod if app.configuration.getBoolean("applyEvolutions." + db).filter(_ == true).isDefined =>
                  if (upsOnly && ScriptAccessor.hasDowns(script)) {
                    throw new Exception("evolutions.ups.only=enabled and the evolution script contains downs, aborting!")
                  } else Evolutions.applyScript(api, db, script)
                case Mode.Prod => {
                  Logger("play").warn("Your production database [" + db + "] needs evolutions! \n\n" + toHumanReadableScript(script))
                  Logger("play").warn("Run with -DapplyEvolutions." + db + "=true if you want to run them automatically (be careful)")

                  throw InvalidDatabaseRevision(db, toHumanReadableScript(script))
                }
                case _ => throw InvalidDatabaseRevision(db, toHumanReadableScript(script))
              }
            }
          }
        }
      }
      case (ds, db) => Logger("play").warn("Evolutions are Globally Enabled, but selectively disabled for:" + db)
    }
  }

  def squashEvolutions() {
    val api = app.plugin[DBPlugin].map(_.api).getOrElse(throw new Exception("there should be a database plugin registered at this point but looks like it's not available, so evolution won't work. Please make sure you register a db plugin properly"))
    api.datasources.foreach {
      case (ds, db) if app.configuration.getString("evolutions." + db).filter(_ == "enabled").isDefined => {
        ScriptAccessor.squashEvolutions(app.path, db)
      }
    }
  }

}
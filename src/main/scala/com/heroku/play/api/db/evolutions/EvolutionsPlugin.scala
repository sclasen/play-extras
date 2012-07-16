package com.heroku.play.api.db.evolutions

import org.slf4j.LoggerFactory
import play.api.db.DBPlugin
import play.api.{Mode, Logger, Application}
import play.api.db.evolutions.{Evolutions, InvalidDatabaseRevision, EvolutionsPlugin => PlayEvolutionsPlugin}
import play.api.db.evolutions.Evolutions._
import javax.sql.DataSource
import java.sql.{Statement, SQLException, Connection}
import scala.util.control.Exception._

class EvolutionsPlugin(app: Application) extends PlayEvolutionsPlugin(app) {

  val log = LoggerFactory.getLogger(classOf[EvolutionsPlugin])

  override lazy val enabled = app.configuration.getConfig("db").isDefined && {
    !app.configuration.getString("new.evolutionplugin").filter(_ == "disabled").isDefined
  }

  val sleepInterval = app.configuration.getInt("evolutions.lock.sleep.interval").map(_.toLong).getOrElse(1000L)

  override def onStart() {
    val api = app.plugin[DBPlugin].map(_.api).getOrElse(throw new Exception("there should be a database plugin registered at this point but looks like it's not available, so evolution won't work. Please make sure you register a db plugin properly"))
    api.datasources.foreach {
      case (ds, db) if app.configuration.getString("evolutions." + db).filter(_ == "enabled").isDefined => {
        withLock(ds) {
          val script = evolutionScript(api, app.path, app.classloader, db)
          if (!script.isEmpty) {
            app.mode match {
              case Mode.Test => Evolutions.applyScript(api, db, script)
              case Mode.Dev if app.configuration.getBoolean("applyEvolutions." + db).filter(_ == true).isDefined => Evolutions.applyScript(api, db, script)
              case Mode.Prod if app.configuration.getBoolean("applyEvolutions." + db).filter(_ == true).isDefined => Evolutions.applyScript(api, db, script)
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
      case (ds, db) => Logger("play").warn("Evolutions are Globally Enabled, but selectively disabled for:" + db)
    }
  }

  def withLock(ds: DataSource)(block: => Unit) {
    if (app.configuration.getBoolean("evolutions.use.locks").filter(_ == true).isDefined) {
      val c = ds.getConnection
      c.setAutoCommit(false)
      val s = c.createStatement()
      createLockTableIfNecessary(c, s)
      lock(c, s)
      try {
        block
      } finally {
        unlock(c, s)
      }
    } else {
      block
    }
  }

  def createLockTableIfNecessary(c: Connection, s: Statement) {
    try {
      val r = s.executeQuery("select lock from play_evolutions_lock")
      r.close()
    } catch {
      case e: SQLException =>
        c.rollback()
        s.execute( """
        create table play_evolutions_lock (
          lock int not null primary key
        )
                   """)
        s.executeUpdate("insert into play_evolutions_lock (lock) values (1)")
    }
  }

  def lock(c: Connection, s: Statement, attempts: Int = 5) {
    try {
      s.executeQuery("select lock from play_evolutions_lock where lock = 1 for update nowait")
    } catch {
      case e: SQLException =>
        if (attempts == 0) throw e
        else {
          Logger("play").warn("Exception while attempting to lock evolutions (other node probably has lock), sleeping for 1 sec")
          c.rollback()
          Thread.sleep(1000)
          lock(c, s, attempts - 1)
        }
    }
  }

  def unlock(c: Connection, s: Statement) {
    ignoring(classOf[SQLException])(s.close())
    ignoring(classOf[SQLException])(c.commit())
    ignoring(classOf[SQLException])(c.close())
  }


}
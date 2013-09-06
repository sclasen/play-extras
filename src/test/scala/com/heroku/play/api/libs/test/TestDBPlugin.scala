package com.heroku.play.api.libs.test

import play.api.db.{ DBApi, BoneCPPlugin, DBPlugin }
import play.api.Application

/*Use the same underlying plugin for all FakeApplications, so FakeApplication dosent suck so much*/

object TestDBPlugin {

  @volatile var plugin: DBPlugin = _
  @volatile var started: Boolean = false

  def getPlugin(app: Application): DBPlugin = {
    if (plugin == null) plugin = new BoneCPPlugin(app)
    plugin
  }

}

class TestDBPlugin(app: Application) extends DBPlugin {

  def api: DBApi = TestDBPlugin.getPlugin(app).api

  override def onStart() {
    if (!TestDBPlugin.started) {
      TestDBPlugin.getPlugin(app).onStart()
      TestDBPlugin.started = true
    }

  }

  override def enabled: Boolean = true
}

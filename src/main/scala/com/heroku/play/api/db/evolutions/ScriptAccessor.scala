package play.api.db.evolutions

import java.io.File
import play.api.Application

object ScriptAccessor {

  def hasDowns(script: Seq[Script]): Boolean = {
    script.find(_.isInstanceOf[DownScript]).isDefined
  }

  def squashEvolutions(appPath: File, db: String) {

    val applicationEvolutions: Seq[Evolution] = Evolutions.applicationEvolutions(appPath, Thread.currentThread().getContextClassLoader, db)
    val squashed = applicationEvolutions.reverse.foldLeft(new StringBuilder) {
      case (b, s) => b.append(s.sql_up).append("\n")
    }
    println(squashed.toString())
  }

}
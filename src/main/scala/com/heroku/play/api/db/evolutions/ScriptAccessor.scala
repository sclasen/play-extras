package play.api.db.evolutions


object ScriptAccessor {


  def hasDowns(script: Seq[Script]): Boolean = {
    script.find(_.isInstanceOf[DownScript]).isDefined
  }

}

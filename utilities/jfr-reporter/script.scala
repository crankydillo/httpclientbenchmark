import java.io.File

import scala.sys.process._

val dataDir = new File(sys.props("user.home") + "/Downloads/results")
val jfrDir = new File(dataDir, "jfr")

val jfrs = jfrDir.listFiles

def mkCmd(jfr: File) = {
  val out = new File(dataDir, jfr.getName.replace(".jfr", ".html"))
  s"java -jar target/jfr-reporter-1.0.0-SNAPSHOT-jar-with-dependencies.jar ${jfr.getAbsolutePath} ${out.getAbsolutePath}"
}

// java -jar target/jfr-reporter-1.0.0-SNAPSHOT-jar-with-dependencies.jar ~/tmp/gordo-20190124/jfr/apacheasync-recording.jfr out.html

jfrs
  .map(mkCmd _)
  .foreach { cmd => cmd.! }

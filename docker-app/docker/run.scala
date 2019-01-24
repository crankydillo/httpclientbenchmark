import java.io.File

import scala.sys.process._

object Usage {
  val commands = List(
    "(test [--jfr] [--no-reporter] (all | <client>) [<host> <port> <executions> <workers>])",
    "server",
    "list",
    "(report <dir>)",
    "help"
  )

  override def toString = s"Usage: ${commands.mkString(" | ")}"
}

val suiteFileDir = "testng"

// yah, some of this sucks, but I don't want to reach for a CLI-parser lib unless I revisit
// this a few more times. (Even though you can make AWESOME CLIs with Scala:)

object Flags {
  val disableReporter = "--no-reporter"
  val enableJfr = "--jfr"
  val flags = Set(disableReporter, enableJfr)
}

val disableDropwizard = args.contains(Flags.disableReporter)
val enableJfr = args.contains(Flags.enableJfr)

val argsWithoutFlags = args.filter { a => !Flags.flags.contains(a) }

argsWithoutFlags.toList match {
  case List("test", "all", h, p, e, w) => runAllTests(h, p.toInt, Some(e.toInt), Some(w.toInt))
  case List("test", c    , h, p, e, w) => runTest(c, h, p.toInt, Some(e.toInt), Some(w.toInt))
  case List("test", "all")             => runAllTests("localhost", 8080)
  case List("test", client)            => runTest(client, "localhost", 8080)
  case List("list")                    => listClients()
  case List("server")                  => runServer()
  case List("report", dir)             => generateReport(dir)
  case _                               => exitWithUsage()
}

def jarPath(name: String) = s"docker-app/lib/$name-1.0.0-SNAPSHOT-jar-with-dependencies.jar"

// Leaving dir as string for now...
def generateReport(dir: String): Unit = {
  s"""java -jar ${jarPath("reporter")} $dir""".!
}

def runAllTests(
  host: String,
  port: Int,
  executions: Option[Int] = None,
  workers: Option[Int] = None
): Unit = clients().foreach { c => runTest(c, host, port, executions, workers) }

def runTest(
  client: String,
  host: String,
  port: Int,
  executions: Option[Int] = None,
  workers: Option[Int] = None
): Unit = {
  val testFile = s"$suiteFileDir/$client.xml"

  val reporterSeconds = if (disableDropwizard) (60 * 60) else 30

  val sysProps = List(
    ("host"               , Some(host)),
    ("port"               , Some(port)),
    ("test.executions"    , executions),
    ("test.workers"       , workers),
    ("dropwizard.seconds" , Some(reporterSeconds))
  ).collect { case (n, Some(v)) => (n, v) }
    .map { case (n, v) => s"-Dbm.$n=$v" } 

  val jfrStr = if (enableJfr) javaOptions(client) else ""
  val jarName = jarPath(s"${client}-benchmark")
  val cmd = s"""java $jfrStr ${sysProps.mkString(" ")} -jar ${jarPath(s"$client-benchmark")} -usedefaultlisteners false $testFile"""
  println(s"Executing:  $cmd")
  cmd.!
}

def exitWithUsage(): Unit = {
  System.err.println(Usage)
  System.exit(1)
}

def clients(): Seq[String] = {
  new File(suiteFileDir).listFiles()
    .map     { _.getName }
    .filter  { _.endsWith(".xml") }
    .map     { _.split("""\.""").dropRight(1).mkString(".") }
    .sorted
}

def listClients(): Unit = clients().foreach { println }

def runServer(): Unit = {
  s"""java -jar ${jarPath("mock-application")}""".run()
}

// Adopted from https://dzone.com/articles/using-java-flight-recorder-with-openjdk-11-2
def javaOptions(client: String): String = {
    List(
      "-XX:+HeapDumpOnOutOfMemoryError",
      s"-XX:HeapDumpPath=${dataFilename(s"$client-heapdump.hprof")}",
      s"-XX:StartFlightRecording=${flightRecorderOptions(client)}"
    ).mkString(" ")
}

def flightRecorderOptions(client: String): String = {
  Map(
    "disk"       -> "true",
    "dumponexit" -> "true",
    "filename"   -> dataFilename(s"$client-recording.jfr"),
    "settings"   -> "default"
  ).map { case (k, v) => s"$k=$v" }
  .mkString(",")
}

def dataFilename(filename: String): String = {
  // Yah, this human-knowledge coupling sucks:(:(
  sys.env.get("BM.METRICS.DIR") match {
    case Some(d) => s"$d/$filename"
    case _       => filename
  }
}

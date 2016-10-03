package org.typelevel.blog

import coursier._
import coursier.util.Parse
import java.io.{File, FileInputStream}
import java.net.URLClassLoader
import org.yaml.snakeyaml.Yaml
import scala.util.Try
import scalaz.concurrent.Task

object Post {

  val logger = new Cache.Logger {
    override def downloadingArtifact(url: String, file: File) =
      println(s"Downloading artifact from $url ...")
    override def downloadedArtifact(url: String, success: Boolean) = {
      val file = url.split('/').last
      if (success)
        println(s"Successfully downloaded $file")
      else
        println(s"Failed to download $file")
    }
  }

  val repositories = Seq(
    Cache.ivy2Local,
    MavenRepository("https://repo1.maven.org/maven2"),
    MavenRepository("https://dl.bintray.com/tpolecat/maven/")
  )

  val fetch = Fetch.from(repositories, Cache.fetch(logger = Some(logger)))

  def resolve(start: Resolution): List[File] = {
    val resolution = start.process.run(fetch).run

    if (!resolution.isDone)
      sys.error("resolution did not converge")

    if (!resolution.conflicts.isEmpty)
      sys.error(s"resolution has conflicts: ${resolution.conflicts.mkString(", ")}")

    if (!resolution.errors.isEmpty)
      sys.error(s"resolution has errors: ${resolution.errors.mkString(", ")}")

    val artifacts = Task.gatherUnordered(
      resolution.artifacts.map(artifact =>
        Cache.file(artifact, logger = Some(logger)).run
      )
    ).run

    artifacts.map(_.toOption.get)
  }

}

case class Post(file: File) {

  lazy val frontMatter: Try[FrontMatter] = Try {
    val yaml = new Yaml()
    val stream = new FileInputStream(file)
    val any = yaml.loadAll(stream).iterator.next()
    stream.close()
    any
  }.flatMap(YAML.decodeTo[FrontMatter])

  def process(): Unit = {
    val tut = frontMatter.toOption.get.tut

    val classLoader = new URLClassLoader(Post.resolve(tut.tutResolution).map(_.toURL).toArray, null)
    val tutClass = classLoader.loadClass("tut.TutMain")
    val tutMain = tutClass.getDeclaredMethod("main", classOf[Array[String]])

    val commandLine = Array(
      file.toString,
      BuildInfo.tutOutput.toString,
      ".*",
      "-classpath",
      Post.resolve(tut.libResolution).mkString(":")
    )

    println(commandLine.toList)

    tutMain.invoke(null, commandLine)
  }

}

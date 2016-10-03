package org.typelevel.blog

import coursier._
import coursier.util.Parse

case class Tut(scala: String, binaryScala: String, dependencies: List[String]) {

  val tutResolution: Resolution = Resolution(Set(
    Dependency(Module("org.tpolecat", s"tut-core_$binaryScala"), BuildInfo.tutVersion)
  ))

  val libResolution: Resolution = Resolution(dependencies.map { dep =>
    val (mod, v) = Parse.moduleVersion(dep, binaryScala).right.get
    Dependency(mod, v)
  }.toSet)

}

case class FrontMatter(tut: Tut)

val scala3Version = "3.8.3"

lazy val root = (project in file("."))
  .enablePlugins(BuildInfoPlugin, NativeImagePlugin)
  .settings(
    nativeImageInstalled := true,
    nativeImageGraalHome := {
      val graalHome = sys.env
        .get("GRAALVM_HOME")
        .orElse(sys.env.get("JAVA_HOME"))
        .getOrElse(sys.props("java.home"))
      file(graalHome).toPath
    },
    nativeImageOptions ++= List(
      "--no-fallback",
      "-H:+ReportExceptionStackTraces",
      "--initialize-at-build-time=org.slf4j,ch.qos.logback",
      "-H:IncludeResources=logback.xml"
    ),
    buildInfoPackage := "xyz.nicebots",
    buildInfoKeys    := Seq[BuildInfoKey](name, version),
    buildInfoObject   := "BuildInfo",
    name              := "licensor",
    idePackagePrefix  := Some("xyz.nicebots"),
    scalaVersion      := scala3Version,
    semanticdbEnabled := true,
    scalacOptions += "-Wunused:all",
    javaOptions += "-Dfile.encoding=UTF-8",
    javaOptions += "-Dstdout.encoding=UTF-8",
    javaOptions += "-Dstderr.encoding=UTF-8",

    libraryDependencies += "com.github.alexarchambault" %% "case-app"        % "2.1.0",
    libraryDependencies += "org.slf4j"                   % "slf4j-api"       % "2.0.18",
    libraryDependencies += "ch.qos.logback"              % "logback-classic" % "1.5.32",
    libraryDependencies += "com.lihaoyi"                %% "fansi"           % "0.5.1",
    libraryDependencies += "com.lihaoyi"                %% "os-lib"          % "0.11.8",
    libraryDependencies += "org.yaml"                    % "snakeyaml"       % "2.6",
    libraryDependencies += "org.scalatest"              %% "scalatest"       % "3.2.20" % Test,
    Compile / mainClass                                 := Some("xyz.nicebots.Main"),
    Compile / packageBin / packageOptions +=
      Package.ManifestAttributes("Implementation-Version" -> version.value),
    assembly / assemblyMergeStrategy := {
      case "module-info.class" => MergeStrategy.discard
      case PathList("META-INF", "versions", _, "module-info.class") => MergeStrategy.discard
      case x =>
        val oldStrategy = (assembly / assemblyMergeStrategy).value
        oldStrategy(x)
    }
  )

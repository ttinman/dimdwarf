package net.orfjackal.dimdwarf.server

import org.junit.runner.RunWith
import net.orfjackal.specsy._
import org.hamcrest.Matchers._
import org.hamcrest.MatcherAssert.assertThat
import net.orfjackal.dimdwarf.testutils.Sandbox
import org.apache.commons.io._
import net.orfjackal.dimdwarf.auth._
import com.google.inject._
import org.junit.Assert
import java.io._
import java.util.zip._

@RunWith(classOf[Specsy])
class ApplicationLoadingSpec extends Spec {
  val applicationDir = createTempDir()
  val classesDir = createDir(applicationDir, ApplicationLoader.CLASSES_DIR)
  val libDir = createDir(applicationDir, ApplicationLoader.LIBRARIES_DIR)

  val correctConfiguration = Map(
    ApplicationLoader.APP_NAME -> "MyApp",
    ApplicationLoader.APP_MODULE -> classOf[MyApp].getName)

  "When configured correctly" >> {
    writeConfiguration(correctConfiguration)
    writeFileToClassesDir("file-in-classes-dir.txt", "file content 1")
    writeJarToLibDir("sample.jar", Map("file-in-jar.txt" -> "file content 2"))

    val loader = new ApplicationLoader(applicationDir)
    defer {worksOnlyOnJava7 {JRE.closeClassLoader(loader.getClassLoader)}}

    "Adds to classpath the /classes directory" >> {
      val content = readContent("file-in-classes-dir.txt", loader.getClassLoader)
      assertThat(content, is("file content 1"))
    }
    "Adds to classpath all JARs in the /lib directory" >> worksOnlyOnJava7 {
      val content = readContent("file-in-jar.txt", loader.getClassLoader)
      assertThat(content, is("file content 2"))
    }
    // TODO: write a test case that the /classes dir is first in classpath? (write a file with same name to /classes and a JAR)

    "Reads the application name from configuration" >> {
      assertThat(loader.getApplicationName, is("MyApp"))
    }
    "Reads the application module from configuration" >> {
      assertThat(loader.getApplicationModule, is(classOf[MyApp].getName))
    }
    "Instantiates the application module" >> {
      assertThat(loader.newModuleInstance, is(notNullValue[Module]()))
    }
  }

  "Error: configuration file is missing" >> {
    assertGivesAnErrorMentioning("File not found", ApplicationLoader.CONFIG_FILE)
  }
  "Error: no application name declared" >> {
    writeConfiguration(correctConfiguration - ApplicationLoader.APP_NAME)

    assertGivesAnErrorMentioning("Property", "was not set", ApplicationLoader.APP_NAME, ApplicationLoader.CONFIG_FILE)
  }
  "Error: no application module declared" >> {
    writeConfiguration(correctConfiguration - ApplicationLoader.APP_MODULE)

    assertGivesAnErrorMentioning("Property", "was not set", ApplicationLoader.APP_MODULE, ApplicationLoader.CONFIG_FILE)
  }

  // creating temporary directories

  private def createTempDir(): File = {
    val sandbox = new Sandbox(new File("target"))
    val dir = sandbox.createTempDir()
    defer {sandbox.deleteTempDir(dir)}
    dir
  }

  private def createDir(parent: File, name: String): File = {
    val dir = new File(parent, name)
    assert(dir.mkdir())
    dir
  }

  // writing application files

  private def writeConfiguration(properties: Map[String, String]) {
    writePropertiesFile(classesDir, ApplicationLoader.CONFIG_FILE, properties)
  }

  private def writePropertiesFile(dir: File, name: String, properties: Map[String, String]) {
    import scala.collection.JavaConversions._
    val file = new File(dir, name)
    val rows = properties map {case (key, value) => key + "=" + value}
    FileUtils.writeLines(file, rows)
  }

  private def writeFileToClassesDir(fileName: String, content: String) {
    val file = new File(classesDir, fileName)
    FileUtils.write(file, content)
  }

  private def writeJarToLibDir(fileName: String, contents: Map[String, String]) {
    val file = new File(libDir, fileName)
    writeJarFile(file, contents)
  }

  private def writeJarFile(jarFile: File, entries: Map[String, String]) {
    val out = new ZipOutputStream(new FileOutputStream(jarFile))
    try {
      for ((entry, content) <- entries) {
        out.putNextEntry(new ZipEntry(entry))
        IOUtils.write(content, out)
      }
    } finally {
      out.close()
    }
  }

  private def readContent(path: String, classLoader: ClassLoader): String = {
    val in = classLoader.getResourceAsStream(path)
    assert(in != null, "Resource not found: " + path)
    try {
      IOUtils.toString(in)
    } finally {
      in.close()
    }
  }

  // custom asserts

  private def assertGivesAnErrorMentioning(messages: String*) {
    try {
      new ApplicationLoader(applicationDir)
      Assert.fail("should have thrown an exception")
    } catch {
      case e: ConfigurationException =>
        for (message <- messages) {
          assertThat(e.getMessage, containsString(message))
        }
    }
  }

  // other utility methods

  private def worksOnlyOnJava7(closure: => Unit) {
    if (JRE.isJava7) {
      closure
    }
  }
}

class MyApp extends AbstractModule {
  protected def configure() {
    bind(classOf[CredentialsChecker[_]]).to(classOf[DummyCredentialsChecker])
  }
}

class DummyCredentialsChecker extends CredentialsChecker[Credentials] {
  def isValid(credentials: Credentials) = false
}

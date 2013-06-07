package com.bluetrainsoftware.maven.bathe

import groovy.transform.CompileStatic
import org.apache.commons.io.IOUtils
import org.apache.maven.artifact.Artifact
import org.apache.maven.plugin.AbstractMojo
import org.apache.maven.plugin.MojoExecutionException
import org.apache.maven.plugin.MojoFailureException
import org.apache.maven.plugins.annotations.LifecyclePhase
import org.apache.maven.plugins.annotations.Mojo
import org.apache.maven.plugins.annotations.Parameter
import org.apache.maven.plugins.annotations.ResolutionScope
import org.apache.maven.project.MavenProject

import java.lang.reflect.Field
import java.util.jar.JarEntry
import java.util.jar.JarFile
import java.util.jar.JarOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

//@CompileStatic
@Mojo(name = "time", requiresProject = true, requiresDependencyResolution = ResolutionScope.TEST, defaultPhase = LifecyclePhase.TEST)
class BatheTimeMojo extends BaseBatheMojo {
  public static final String ARTIFACT_WAR = 'war'

  @Parameter(property = 'run.mainClass')
  public String mainClass

  /**
   * A Jump-Class gives the Bathe-Booter a location of where to jump to.
   */
  @Parameter(property = 'run.jumpClass')
  public String jumpClass

  @Parameter(property = 'run.libraryOffset')
  public String libraryOffset = 'WEB-INF/jars'

  FileOutputStream fos
  JarOutputStream jar

  protected void log() {
    getLog().info("bathe ${extension()} generation, library offset ${libraryOffset}")
  }

  @Override
  void execute() throws MojoExecutionException, MojoFailureException {
    log();

    project.artifact.file = getGeneratedFile()

    fos = new FileOutputStream(project.artifact.file)
    jar = new JarOutputStream(fos)

    if (isWar())
      copyBuildDirectory('WEB-INF/classes')
    else
      copyBuildDirectory(libraryOffset + "/classes")

    project.artifacts.each { Artifact artifact ->
      if (artifact.scope == 'compile' || artifact.scope == 'runtime') {
        if (artifact.type == ARTIFACT_WAR) {
          extractArtifact(artifact, '')
        } else {
          extractArtifact(artifact, libraryOffset)
        }
      }
    }

    if (mainClass)
      createManifest()

    jar.close()
  }



  protected void addJarFile(File file, String offset) {
    String offsetDir = offset.endsWith('/') ? offset : offset + '/'

    String name = offsetDir + file.name

    JarEntry ze = new JarEntry(name)

    jar.putNextEntry(ze)
    InputStream is = new FileInputStream(file)
    IOUtils.copy(is, jar)
    jar.closeEntry()
    is.close()
  }

  protected String addJarDirectory(String dir) {

    String name = dir.endsWith('/') ? dir : dir + '/'

    JarEntry ze = new JarEntry(name)
    jar.putNextEntry(ze)
    jar.closeEntry()

    return name
  }

  protected void recursiveCopy(File curDir, String offset) {
    curDir.listFiles().each { File file ->
      if (file.name.startsWith(".")) return

      if (file.directory) {
        recursiveCopy(file, addJarDirectory(offset + file.name))
      } else {
        addJarFile(file, offset)
      }
    }
  }

  protected void copyBuildDirectory(String offset) {
    String dirOffset = addJarDirectory(offset)

    File classesDir = new File(project.build.outputDirectory)

    if (classesDir.exists()) {
      recursiveCopy(classesDir, dirOffset)
    }
  }


  protected void addJarEntry(JarEntry jarEntry, InputStream is, String offset) {
    String offsetDir

    if (offset)
      offsetDir = offset.endsWith('/') ? offset : offset + '/'
    else
      offsetDir = ''

    String internalName = offsetDir + jarEntry.name

    // bully the zip entry into copying the existing one and then reset its name to the new name
    JarEntry ze = new JarEntry(jarEntry)
    Field f = ze.class.getSuperclass().getDeclaredField("name")
    f.setAccessible(true)
    f.set(ze, internalName)

    jar.putNextEntry(ze)
    IOUtils.copy(is, jar)
    jar.closeEntry()
  }

  protected void extractArtifact(Artifact artifact, String offset) {
    String offsetDir = offset.endsWith('/') ? offset : offset + '/'

    if (offset) {
      String name = artifact.file.name

      if (name.endsWith(".jar")) {
        name = name.substring(0, name.length() - 4)
      }

      offsetDir = offsetDir + name + '/'
    } else {
      offsetDir = ''
    }

    JarFile f = new JarFile(artifact.getFile())

    f.entries().each { JarEntry entry ->
      if (entry.isDirectory())
        addJarDirectory(offsetDir + entry.name)
      else if (entry.name != 'META-INF/MANIFEST.MF' || offsetDir != '')
        addJarEntry(entry, f.getInputStream(entry), offsetDir)
    }
  }

  protected void createManifest() {
    String manifest = "Manifest-Version: 1.0\nMain-Class: ${mainClass}\nCreated-by: Bathe/Time\nImplementation-Version: ${project.version}\n"

    if (jumpClass)
      manifest = manifest + "Jump-Class: ${jumpClass}\n"

    byte[] bytes = manifest.toString().bytes

    JarEntry ze = new JarEntry("META-INF/MANIFEST.MF")
    ze.size = bytes.size()
    jar.putNextEntry(ze)
    jar.write(bytes)
    jar.closeEntry()
  }


}
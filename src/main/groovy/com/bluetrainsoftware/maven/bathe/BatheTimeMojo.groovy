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

@CompileStatic
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

	@Parameter(property = 'run.runnableLibraries')
	public String runnableLibraries = 'bathe-runner'

  FileOutputStream fos
  JarOutputStream jar
	String[] runLibs

  protected void log() {
    getLog().info("bathe ${extension()} generation, library offset ${libraryOffset}")
  }

  @Override
  void execute() throws MojoExecutionException, MojoFailureException {
	  if (project.packaging == 'pom') {
		  return
	  }

	  log()

	  project.artifact.file = getGeneratedFile()

    fos = new FileOutputStream(project.artifact.file)
    jar = new JarOutputStream(fos)

	  runLibs = runnableLibraries.tokenize(',')

	  if (isWar()) {
		  extractWebAppDirectory()
	  }

	  extractRunnableLibraries(runLibs)

		copyBuildDirectory(libraryOffset + "/classes")

	  extractOtherLibraries(runLibs)

    if (mainClass)
      createManifest()

    jar.close()
  }

	/**
	 * This is typically where the WEB-INF/web.xml is stored along with anything else the user requires
	 */
	protected void extractWebAppDirectory() {
		File classesDir = new File(project.basedir, "src/main/webapp")

		if (classesDir.exists()) {
			recursiveCopy(classesDir, '')
		}
	}

	protected boolean artifactRunnable(Artifact artifact) {
		for(String runlib : runLibs) {
			if (artifact.artifactId.contains(runlib)) {
				return true
			}
		}

		return false
	}

	protected void extractRunnableLibraries(String[] runLibs) {
		filterLibraries { Artifact artifact ->
			if (artifactRunnable(artifact)) {
				extractArtifact(artifact, '')
			}
		}
	}

	protected void extractOtherLibraries(String[] runLibs) {
		filterLibraries { Artifact artifact ->
			if (!artifactRunnable(artifact)) {
				extractArtifact(artifact, libraryOffset)
			}
		}
	}

	protected void filterLibraries(Closure c) {
		project.artifacts.each { Artifact artifact ->
			if (artifact.scope == 'compile' || artifact.scope == 'runtime') {
				c(artifact)
			}
		}
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

	protected Map<String, String> existingDirs = [:]

  protected String addJarDirectory(String dir) {

    String name = dir.endsWith('/') ? dir : dir + '/'

	  if (!existingDirs[name]) {
		  existingDirs[name] = name

	    JarEntry ze = new JarEntry(name)
	    jar.putNextEntry(ze)
	    jar.closeEntry()
	  }

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

	  manifest += "Jar-Offset: ${libraryOffset}\n"

    byte[] bytes = manifest.toString().bytes

    JarEntry ze = new JarEntry("META-INF/MANIFEST.MF")
    ze.size = bytes.size()
    jar.putNextEntry(ze)
    jar.write(bytes)
    jar.closeEntry()
  }


}

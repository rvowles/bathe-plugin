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

import java.util.jar.JarEntry
import java.util.jar.JarFile
import java.util.jar.JarOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

//@CompileStatic
@Mojo(name="time", requiresProject = true, requiresDependencyResolution = ResolutionScope.TEST, defaultPhase = LifecyclePhase.TEST)
class BatheMojo extends AbstractMojo {
  public static final String ARTIFACT_WAR = 'war'


  @Parameter(required = true, readonly = true, property = 'project')
  protected MavenProject project;

  @Parameter(property = 'run.extension')
  public String extension = ARTIFACT_WAR

  @Parameter(property = 'run.classifier')
  public String classifier = null;

  @Parameter(property = 'run.libraryOffset')
  public String libraryOffset = 'WEB-INF/jars'

  @Parameter(property = 'run.mainClass', required = true)
  public String mainClass

  private File fExport;
  private File fLibrary;
  private List<File> zippedDirectories = []
  private List<File> zippedFiles = []

  protected boolean isWar() {
    return extension == ARTIFACT_WAR
  }

  protected void log() {
    getLog().info("bathe: extension ${extension}, classifier ${classifier}, library offset ${libraryOffset}, main class ${mainClass}")
  }

  @Override
  void execute() throws MojoExecutionException, MojoFailureException {
    log();

    createExportDirectory()

    project.runtimeArtifacts.each { Artifact artifact ->
      if (isWar() && artifact.getClassifier() == "overlay") {
        extractArtifact(fExport, artifact)
      } else {
        extractArtifact(fLibrary, artifact)
      }
    }

    if (isWar())
      copyBuildDirectory(new File(fExport, 'WEB-INF/classes'))
    else
      copyBuildDirectory(new File(fExport, libraryOffset + "/classes"))

    createManifest()
    jarArtifact()
  }

  protected void copy(InputStream inputStream, File outFile) {
    OutputStream outputStream = new FileOutputStream(outFile)

    IOUtils.copy(inputStream, outputStream)

    inputStream.close()
    outputStream.close()
  }

  protected void recursiveCopy(File curDir, File outputDir) {
    curDir.listFiles().each { File file ->
      if (file.name.startsWith(".")) return

      if (file.directory) {
        File newDir = new File(outputDir, file.name)
        newDir.mkdirs()

        zippedDirectories.add(newDir)

        recursiveCopy(file, newDir)
      } else {
        File newFile = new File(outputDir, file.getName())
        zippedFiles.add(newFile)

        copy(new FileInputStream(file), newFile)
      }
    }
  }

  protected void copyBuildDirectory(File destDirectory) {
    File classesDir = new File(project.build.outputDirectory)

    if (classesDir.exists()) {
      destDirectory.mkdirs()

      recursiveCopy(classesDir, destDirectory)
    }
  }

  protected void extractArtifact(File to, Artifact artifact) {
    String name = artifact.file.name

    if (name.endsWith(".jar")) {
      name = name.substring(0, name.length() - 4)
    }

    File mainTo = new File(to, name)

    mainTo.mkdirs()

    ZipFile f = new ZipFile(artifact.getFile())

    f.entries().each { ZipEntry entry ->
      File oFile = new File(mainTo, entry.name)

      if (!entry.directory) {
        File parentDir = oFile.parentFile

        if (!zippedDirectories.contains(parentDir)) {
          parentDir.mkdirs()

          zippedDirectories.add(parentDir)
        }

        zippedFiles.add(oFile)

        copy(f.getInputStream(entry), oFile)
      }
    }
  }

  protected void createManifest() {
    File manifest = new File(fExport, "META-INF")
    manifest.mkdirs()

    File manifestFile = new File(manifest, "MANIFEST.MF")

    zippedFiles.add(manifestFile)

    manifestFile.write("Manifest-Version: 1.0\nMain-Class: ${mainClass}")
  }

  protected void jarArtifact() {
    FileOutputStream fos = new FileOutputStream(project.build.directory + "/bathe.war")

    JarOutputStream jar = new JarOutputStream(fos)

    int exportDir = fExport.absolutePath.length()

    zippedDirectories.each { File dir ->
      String name = dir.absolutePath.substring(exportDir)
      getLog().info("adding ${name}")
      JarEntry ze = new JarEntry(name)
      jar.putNextEntry(ze)
      jar.closeEntry()
    }

    zippedFiles.each { File file ->
      String name = file.absolutePath.substring(exportDir)
      getLog().info("adding ${name}")

      JarEntry ze = new JarEntry(name)
      jar.putNextEntry(ze)
      InputStream is = new FileInputStream(file)
      IOUtils.copy(is, jar)
      jar.closeEntry()
      is.close()
    }

    fos.close()
  }

  protected void createExportDirectory() {
    fExport = new File(project.build.directory + '/bathe')

    fExport.mkdirs()

    fLibrary = new File(fExport, libraryOffset)

    fLibrary.mkdirs()
  }
}

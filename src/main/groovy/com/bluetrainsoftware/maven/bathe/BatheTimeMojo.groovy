package com.bluetrainsoftware.maven.bathe

import groovy.transform.CompileStatic
import org.apache.commons.io.IOUtils
import org.apache.maven.artifact.Artifact
import org.apache.maven.plugin.MojoExecutionException
import org.apache.maven.plugin.MojoFailureException
import org.apache.maven.plugins.annotations.LifecyclePhase
import org.apache.maven.plugins.annotations.Mojo
import org.apache.maven.plugins.annotations.Parameter
import org.apache.maven.plugins.annotations.ResolutionScope

import java.lang.reflect.Field
import java.util.jar.JarEntry
import java.util.jar.JarFile
import java.util.jar.JarOutputStream

@CompileStatic
@Mojo(name = "time", requiresProject = true, requiresDependencyResolution = ResolutionScope.TEST, defaultPhase = LifecyclePhase.TEST)
class BatheTimeMojo extends BaseBatheMojo {
	public static final String ARTIFACT_WAR = 'war'

	@Parameter(property = 'run.mainClass')
	public String mainClass = "bathe.BatheBooter"

	/**
	 * A Jump-Class gives the Bathe-Booter a location of where to jump to.
	 */
	@Parameter(property = 'run.jumpClass')
	public String jumpClass

	/**
	 * What offset within the final jar file the artifacts will take.
	 */
	@Parameter(property = 'run.libraryOffset')
	public String libraryOffset = 'WEB-INF/jars'

	/**
	 * These are a list of libraries who's entire contents will unzipped into the top level jar/war artifact
	 * making them directly accessible.
	 */
	@Parameter(property = 'run.runnableLibraries')
	public String runnableLibraries = 'bathe-booter'

	/**
	 * This lets us override the order in which the jar files will get unarchived into the final file
	 */
	@Parameter(property = 'run.libraryOrdering')
	public String libraryOrdering

	/**
	 * If this is set, we will need to cycle through each of the jar files (including the current project) looking
	 * for files and resources that need to make their way into the top level of the final artifact instead of
	 * at an offset.
	 *
	 * Relative paths only: e.g. META-INF/baseconfig/
	 *
	 * Add trailing slash just in case of accidental match
	 */
	@Parameter(property = 'run.runnableOffsets')
	public String runnableOffsets

	@Parameter(property = 'run.runnableOffsetPreference')
	public RunnablePreference runnablePreference = RunnablePreference.first

	FileOutputStream fos
	JarOutputStream jar
	String[] runLibs
	List<Artifact> sortedArtifacts = []
	RunnableOffsetTracker tracker
	List<String> offsetsForManifest = []


	@Override
	void execute() throws MojoExecutionException, MojoFailureException {
		if (project.packaging == 'pom') {
			return
		}

		getLog().info("bathe ${extension()} generation, library offset ${libraryOffset}")

		project.artifact.file = getGeneratedFile()

		fos = new FileOutputStream(project.artifact.file)
		jar = new JarOutputStream(fos)

		runLibs = runnableLibraries.tokenize(',')

		sortArtifacts()   // make sure we have them in processing order

		initializeTracker() // extract out the list of files we are going to use in the main war


		extractTracking()

		// now start processing the files
		if (isWar()) {
			extractWebAppDirectory()
		}

		extractRunnableLibraries(runLibs)

		copyBuildDirectory(libraryOffset + "/classes")

		extractOtherLibraries(runLibs)

		createManifest()

		jar.close()
	}

	void initializeTracker() {
		tracker = new RunnableOffsetTracker(runnableOffsets, runnablePreference)
		tracker.checkProjectForResources(project.basedir, isWar())

		// check non runnable files first
		filterLibraries { Artifact artifact ->
			if (!artifactRunnable(artifact)) {
				tracker.checkArtifactForResources(artifact)
			}
		}

		// then runnable ones
		filterLibraries { Artifact artifact ->
			if (artifactRunnable(artifact)) {
				tracker.checkArtifactForResources(artifact)
			}
		}
	}

	/**
	 * takes all of the tracked files
	 */
	protected void extractTracking() {
		tracker.sortedTrackingItems().each { RunnableOffsetTracker.Source source ->
			if (source.name == 'META-INF/MANIFEST.MF') {
				return
			}

			if (source.projectOffset) {
				streamFile(source.projectOffset, source.name)
			} else {
				JarFile jf = new JarFile(source.artifact.file)
				Enumeration<JarEntry> entries = jf.entries()
				while (entries.hasMoreElements()) {
					JarEntry je = entries.nextElement()
					if (je.name == source.name) {
						streamJarToJar(je, source.name, jf.getInputStream(je))
					}
				}
			}
		}
	}

	/**
	 * This is typically where the WEB-INF/web.xml is stored along with anything else the user requires
	 */
	protected void extractWebAppDirectory() {
		File webappDirectory = new File(project.basedir, "src/main/webapp")

		if (webappDirectory.exists()) {
			recursiveCopy(webappDirectory, '')
		}
	}

	/**
	 * Runnable artifacts are those that get extracted straight into the final jar without offset.
	 */
	protected boolean artifactRunnable(Artifact artifact) {
		for (String runlib : runLibs) {
			if (artifact.artifactId.contains(runlib)) {
				return true
			}
		}

		return false
	}

	protected void extractRunnableLibraries(String[] runLibs) {
		filterLibraries { Artifact artifact ->
			if (artifactRunnable(artifact)) {
				extractArtifact(artifact, '', true)
			}
		}
	}

	protected void extractOtherLibraries(String[] runLibs) {
		filterLibraries { Artifact artifact ->
			if (!artifactRunnable(artifact)) {
				extractArtifact(artifact, libraryOffset, false)
			}
		}
	}

	protected void filterLibraries(Closure c) {
		sortedArtifacts.each { Artifact artifact ->
			if (artifact.scope == 'compile' || artifact.scope == 'runtime') {
				c(artifact)
			}
		}
	}

	protected void streamFile(File file, String name) {
		if (!file.isDirectory()) {
			JarEntry ze = new JarEntry(name)
			jar.putNextEntry(ze)
			InputStream is = new FileInputStream(file)
			IOUtils.copy(is, jar)
			jar.closeEntry()
			is.close()
		}
	}

	protected void addJarFile(File file, String offset) {
		String offsetDir = offset.endsWith('/') ? offset : offset + '/'

		if (!tracker.tracking(offsetDir + file.name))
			streamFile(file, offsetDir + file.name)
	}

	protected Map<String, String> existingDirs = [:]

	protected String addJarDirectory(String dir) {

		String name = dir.endsWith('/') ? dir : dir + '/'

		if (!existingDirs[name]) {
			existingDirs[name] = name

			offsetsForManifest.add(name)

			JarEntry ze = new JarEntry(name)
			jar.putNextEntry(ze)
			jar.closeEntry()
		}

		return name
	}

	protected void recursiveCopy(File curDir, String offset) {
		curDir.listFiles().each { File file ->
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

		if (tracker.tracking(internalName)) {
			return // skip this file
		}

		streamJarToJar(jarEntry, internalName, is)
	}

	protected void streamJarToJar(JarEntry jarEntry, String internalName, InputStream is) {
		// bully the zip entry into copying the existing one and then reset its name to the new name
		JarEntry ze = new JarEntry(jarEntry.name)
		Field f = ze.class.getSuperclass().getDeclaredField("name")
		f.setAccessible(true)
		f.set(ze, internalName)

		jar.putNextEntry(ze)
		IOUtils.copy(is, jar)
		jar.closeEntry()
	}

	protected void extractArtifact(Artifact artifact, String offset, boolean checkTracker) {
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
			if (entry.isDirectory()) {
				if (!tracker.tracking(offsetDir + entry.name)) {
					addJarDirectory(offsetDir + entry.name)
				}
			} else if (entry.name != 'META-INF/MANIFEST.MF' || offsetDir != '') {
				addJarEntry(entry, f.getInputStream(entry), offsetDir)
			}

		}
	}

	protected void createManifest() {
		StringBuilder manifest = new StringBuilder("Manifest-Version: 1.0\n" +
				"Main-Class: ${mainClass}\n" +
				"Created-by: Bathe/Time\n" +
				"Implementation-Version: ${project.version}\n")

		if (jumpClass)
			manifest.append("Jump-Class: ${jumpClass}\n")

		manifest.append("Jar-Offset: ${libraryOffset}\n")
/*
		manifest.append( "Bathe-Classpath: ")
		manifest.append( offsetsForManifest.join(',') )
		manifest.append("\n")
*/

		byte[] bytes = manifest.toString().bytes

		JarEntry ze = new JarEntry("META-INF/MANIFEST.MF")
		ze.size = bytes.size()
		jar.putNextEntry(ze)
		jar.write(bytes)
		jar.closeEntry()
	}

	/**
	 * This takes the artifacts that are listed in the project and sorts them according to the override settings
	 */
	public void sortArtifacts() {
		project.artifacts.each { Artifact artifact ->
			sortedArtifacts.add(artifact)
		}

		if (libraryOrdering != null) {
			determineSorting(sortedArtifacts)
		}
	}

	protected void determineSorting(List<Artifact> jars) {
		final List<String> order = []

		libraryOrdering.trim().tokenize(',').each { String part ->
			order.add(part.trim())
		}

		jars.sort(new Comparator<Artifact>() {
			@Override
			public int compare(Artifact s1, Artifact s2) {

				int s1Pos = findPartial(s1.artifactId, order);
				int s2Pos = findPartial(s2.artifactId, order);

				if (s1Pos < s2Pos) return -1;
				if (s1Pos == s2Pos) return 0;
				if (s1Pos > s2Pos) return 1;

				return 0;
			}
		})
	}

	protected int findPartial(String jarName, List<String> order) {
		int max = order.size()

		for (int count = 0; count < max; count++) {
			if (jarName.contains(order[count])) {
				int val = ((order.size() - count) * -1) - 1;
				return val
			}
		}

		return 0;
	}

}

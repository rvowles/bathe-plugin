package com.bluetrainsoftware.maven.bathe

import groovy.transform.CompileStatic
import groovy.transform.InheritConstructors
import org.apache.maven.artifact.Artifact

import java.util.jar.JarEntry
import java.util.jar.JarFile

/**
 * author: Richard Vowles - http://gplus.to/Richard.Vowles
 */
//@CompileStatic
class RunnableOffsetTracker {
	List<String> offsets = []

	Map<String, Source> sourceMap = [:]
	RunnablePreference runnablePreference

	@InheritConstructors
	public class DuplicateResourceException extends RuntimeException {
	}

	public class Source {

		/**
		 * when the source is an artifact
		 */
		Artifact artifact

		/**
		 * when the project is the current project, this is the offset directory
		 */
		File projectOffset

		String name

		public Source(String name, Artifact artifact) {
			this.name = name
			this.artifact = artifact
		}

		public Source(String name, File offset) {
			this.name = name
			this.projectOffset = offset
		}

	}

	boolean isActive() {
		return offsets.size() > 0
	}

	RunnableOffsetTracker(String track, RunnablePreference runnablePreference) {
		this.runnablePreference = runnablePreference

		if (track) {
			track.trim().tokenize(',').each { String part ->
				offsets.add(part)
			}
		}
	}

	/**
	 * This could be a regex, but for the moment I want to keep it simple
	 *
	 * @param check - path name to check against. Must be non-null and non-empty.
	 * @return true if we found a match
	 */
	protected boolean matches(String check) {
		if (!check) {
			return false
		}

		for (String offset : offsets) {
			if (check.startsWith(offset)) {
				return true
			}
		}

		return false
	}

	Source tracking(String name) {
		return sourceMap[name]
	}

	Collection<Source> sortedTrackingItems() {
		return sourceMap.values().sort({ Source o1, Source o2 -> return o1.name.compareTo(o2.name) })
	}

	protected void matched(File file, String offset, boolean directory) {
		if (sourceMap[offset]) {
			if (directory) {

			} else if (runnablePreference == RunnablePreference.fail) {
				throw new DuplicateResourceException("Duplicate resource ${offset} in ${file.path}")
			} else if (runnablePreference == RunnablePreference.last) {
				sourceMap[offset] = new Source(offset, file)
			}
		} else {
			sourceMap[offset] = new Source(offset, file)
		}
	}

	protected void matched(Artifact artifact, String offset, boolean directory) {
		if (sourceMap[offset]) {
			if (directory) {

			} else if (runnablePreference == RunnablePreference.fail) {
				throw new DuplicateResourceException("Duplicate resource ${offset} in ${artifact.artifactId}")
			} else if (runnablePreference == RunnablePreference.last) {
				sourceMap[offset] = new Source(offset, artifact)
			}
		} else {
			sourceMap[offset] = new Source(offset, artifact)
		}
	}

	protected void directoryCheck(File dir, String offset) {
		if (offset && matches(offset + '/')) {
			matched(dir, offset + '/', true)
		}

		dir.listFiles().each { File f ->
			String offName = offset ? (offset + '/' + f.name) : f.name
			if (f.isDirectory()) {
				if (!f.name.startsWith('.')) {
					directoryCheck(f, offName)
				}
			} else {
				if (matches(offName)) {
					matched(f, offName, false)
				}
			}
		}
	}

	void checkProjectForResources(File basedir, boolean war) {
		if (!isActive()) return

		if (war) {
			File classesDir = new File(basedir, 'src/main/webapp')

			if (classesDir.exists()) {
				directoryCheck(classesDir, '')
			}
		}

		File resourcesDir = new File(basedir, 'src/main/resources')

		if (resourcesDir.exists()) {
			directoryCheck(resourcesDir, '')
		}
	}

	void checkArtifactForResources(Artifact artifact) {
		if (!isActive()) return

		JarFile jf = new JarFile(artifact.file)

		jf.entries().each { JarEntry entry ->
			if (matches(entry.name)) {
				matched(artifact, entry.name, entry.directory)
			}
		}
	}
}

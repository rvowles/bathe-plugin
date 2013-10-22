package com.bluetrainsoftware.maven.bathe

import org.apache.maven.artifact.Artifact
import org.apache.maven.project.MavenProject
import org.junit.Test

/**
 * author: Richard Vowles - http://gplus.to/Richard.Vowles
 */
class RunnableOffsetTrackerTests {

	RunnableOffsetTracker getTracker(RunnablePreference pref) {
		RunnableOffsetTracker tracker = new RunnableOffsetTracker("META-INF/", pref)
		tracker.checkProjectForResources(new File('./src/test/resources'), false)
		tracker.checkArtifactForResources([ getFile: {-> return new File('src/test/resources/sample.jar')}, getArtifactId: {-> return 'sample'}] as Artifact)

		return tracker
	}

	@Test(expected = RunnableOffsetTracker.DuplicateResourceException)
	public void fail() {
		getTracker(RunnablePreference.fail)
	}

	@Test
	public void last() {
		RunnableOffsetTracker tracker = getTracker(RunnablePreference.last)

		assert tracker.sourceMap['META-INF/sample/sample1.txt']
		assert tracker.sourceMap['META-INF/sample/sample1.txt'].artifact
	}

	@Test
	public void first() {
		RunnableOffsetTracker tracker = getTracker(RunnablePreference.first)

		assert tracker.sourceMap['META-INF/sample/sample1.txt']
		assert tracker.sourceMap['META-INF/sample/sample1.txt'].projectOffset
	}
}

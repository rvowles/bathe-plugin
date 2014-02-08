package com.bluetrainsoftware.maven.bathe

import org.apache.maven.plugins.annotations.LifecyclePhase
import org.apache.maven.plugins.annotations.Mojo
import org.apache.maven.plugins.annotations.ResolutionScope

/**
 * Runs the app with test scope dependencies
 *
 * @author: Richard Vowles - https://plus.google.com/+RichardVowles
 */
@Mojo(name="test", requiresProject = false, requiresDependencyResolution = ResolutionScope.TEST, defaultPhase = LifecyclePhase.PACKAGE)
class TestScopeWebAppMojo extends BaseWebAppMojo {
	@Override
	void addExtraUrls(List<URL> urls) {
		urls.add(new File(project.build.testOutputDirectory).toURI().toURL())

		checkForWebDirs(urls, "test")


	}
}

package com.bluetrainsoftware.maven.bathe

import org.apache.maven.plugins.annotations.LifecyclePhase
import org.apache.maven.plugins.annotations.Mojo
import org.apache.maven.plugins.annotations.ResolutionScope

/**
 *
 * @author: Richard Vowles - https://plus.google.com/+RichardVowles
 */
@Mojo(name="testScopeWebApp", requiresProject = false, requiresDependencyResolution = ResolutionScope.TEST, defaultPhase = LifecyclePhase.PACKAGE)
class TestScopeWebAppMojo extends BaseWebAppMojo {
}

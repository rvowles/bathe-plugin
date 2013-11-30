package com.bluetrainsoftware.maven.bathe

import org.apache.maven.plugins.annotations.LifecyclePhase
import org.apache.maven.plugins.annotations.Mojo
import org.apache.maven.plugins.annotations.ResolutionScope

/**
 * Runs the app with run level dependencies
 *
 * @author: Richard Vowles - https://plus.google.com/+RichardVowles
 */
@Mojo(name="run", requiresProject = false, requiresDependencyResolution = ResolutionScope.RUNTIME, defaultPhase = LifecyclePhase.PACKAGE)
class RunScopeWebAppMojo extends BaseWebAppMojo {
}

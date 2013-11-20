package com.bluetrainsoftware.maven.bathe

import bathe.BatheBooter
import bathe.BatheInitializerProcessor
import groovy.transform.CompileStatic
import org.apache.maven.plugin.AbstractMojo
import org.apache.maven.plugin.MojoExecutionException
import org.apache.maven.plugin.MojoFailureException
import org.apache.maven.plugins.annotations.Component
import org.apache.maven.plugins.annotations.LifecyclePhase
import org.apache.maven.plugins.annotations.Mojo
import org.apache.maven.plugins.annotations.Parameter
import org.apache.maven.plugins.annotations.ResolutionScope
import org.apache.maven.project.MavenProject

/**
 *
 * @author: Richard Vowles - https://plus.google.com/+RichardVowles
 */
@CompileStatic
class BaseWebAppMojo extends AbstractMojo {
	@Component
	MavenProject project

	@Parameter(property = 'run.args')
	String booterArguments = ""

	@Parameter(property = 'run.jumpClass', required = true)
	String jumpClass

	@Override
	void execute() throws MojoExecutionException, MojoFailureException {
		if (project.getPackaging() == "pom")
			return

		List<String> tokArgs = booterArguments.tokenize(' ')
		String[] passingArgs = tokArgs.toArray(new String[tokArgs.size()])
		ClassLoader loader = getClass().getClassLoader()

		getLog().info("Starting Bathe Booter: jump-class ${jumpClass}, args ${tokArgs.join(' ')}")


		RunWebAppBatheBooter booter = new RunWebAppBatheBooter()
		new BatheInitializerProcessor().process(passingArgs, jumpClass, loader);
		booter.exec(loader, null, jumpClass, passingArgs)
	}

	class RunWebAppBatheBooter extends BatheBooter {
		@Override
		public void exec(ClassLoader loader, File runnable, String runnerClass, String[] args) {
			BatheBooter.exec(loader, runnable, runnerClass, args)
		}
	}
}

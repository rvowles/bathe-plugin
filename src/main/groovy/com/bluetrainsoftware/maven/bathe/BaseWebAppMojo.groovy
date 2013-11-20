package com.bluetrainsoftware.maven.bathe

import bathe.BatheBooter
import bathe.BatheInitializerProcessor
import groovy.transform.CompileStatic
import groovy.transform.TypeCheckingMode
import org.apache.maven.artifact.Artifact
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
 * This just expects the booter and any dependencies to be in the classpath. It is designed for running from within an IDE.
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
		URLClassLoader loader = getClassLoader()
		ClassLoader currentClassLoader = Thread.currentThread().getContextClassLoader();

		getLog().info("Starting Bathe Booter: jump-class ${jumpClass}, args ${tokArgs.join(' ')}")

		try {
			Thread.currentThread().setContextClassLoader(loader);
			Class booterClazz = loader.loadClass("bathe.BatheBooter")

			callBooter(booterClazz, passingArgs, jumpClass, loader)
		} finally {
			Thread.currentThread().setContextClassLoader(currentClassLoader);
			loader.close()
		}
	}

	@CompileStatic(value = TypeCheckingMode.SKIP)
	private void callBooter(Class booterClazz, String[] args, String jumpClass, URLClassLoader loader) {
		booterClazz.newInstance().runWithLoader(loader, null, jumpClass, args)
	}

	URLClassLoader getClassLoader() {
		List<URL> urls = []

		urls.add(new File(project.build.outputDirectory).toURI().toURL())

		addExtraUrls(urls)

		project.getArtifacts().each { Artifact artifact ->
			urls.add(artifact.file.toURI().toURL())
		}

		return new URLClassLoader(urls.toArray(new URL[urls.size()]))
	}

	void addExtraUrls(List<URL> urls) {
	}
}

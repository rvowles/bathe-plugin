package com.bluetrainsoftware.maven.bathe

import groovy.transform.CompileStatic
import org.apache.maven.plugin.AbstractMojo
import org.apache.maven.plugins.annotations.Parameter
import org.apache.maven.project.MavenProject

/**
 * author: Richard Vowles - http://gplus.to/RichardVowles
 */
@CompileStatic
abstract class BaseBatheMojo extends AbstractMojo {

	@Parameter(required = true, readonly = true, property = 'project')
	protected MavenProject project;

	protected boolean isWar() {
		return project.packaging == "bathe-war"
	}

	protected String extension() {
		return isWar() ? "war" : "jar"
	}

	protected File getGeneratedFile() {
    if (project.build.finalName) {
      return  new File("${project.build.directory}/${project.build.finalName}.${extension()}")
    } else {
      return new File(project.build.directory + "/${project.artifactId}-${project.version}.${extension()}")
    }
	}

}
